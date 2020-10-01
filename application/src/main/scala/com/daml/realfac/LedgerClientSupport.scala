package com.daml.realfac

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import com.daml.grpc.adapter.AkkaExecutionSequencerPool
import com.daml.ledger.api.refinements.ApiTypes.{ApplicationId, WorkflowId}
import com.daml.ledger.api.v1.active_contracts_service.GetActiveContractsResponse
import com.daml.ledger.api.v1.command_service.SubmitAndWaitRequest
import com.daml.ledger.api.v1.commands.{Command, Commands}
import com.daml.ledger.api.v1.transaction.{Transaction, TransactionTree}
import com.daml.ledger.api.v1.transaction_filter.{Filters, TransactionFilter}
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.configuration.{CommandClientConfiguration, LedgerClientConfiguration, LedgerIdRequirement}
import com.google.protobuf.empty.Empty
import scalaz.syntax.tag._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait LedgerClientSupport {
  def submitCommand[T](command: Command): Future[Empty]
  def subscribe(max: Option[Long])(f: Transaction => Unit): Future[Done]
  def subscribeTree(max: Option[Long])(f: TransactionTree => Unit): Future[Done]
  def fetch(max: Option[Long])(f: GetActiveContractsResponse => Unit): Future[Done]
  def shutdown(): Unit
}

object LedgerClientSupport {
  def create(ledgerHost: String,
             ledgerPort: Int,
             actingParty: String): Future[LedgerClientSupport] = {

    val actorSystem = ActorSystem()
    implicit val mat: Materializer = Materializer(actorSystem)
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    val aesf = new AkkaExecutionSequencerPool("clientPool")(actorSystem)

    val applicationId = ApplicationId("Realestate-Facilitator")
    val workflowId = WorkflowId(s"$actingParty Workflow")
    val clientConfig = LedgerClientConfiguration(
      applicationId = ApplicationId.unwrap(applicationId),
      ledgerIdRequirement = LedgerIdRequirement.none,
      commandClient = CommandClientConfiguration.default,
      sslContext = None,
      token = None
    )

    for {
      client <- LedgerClient.singleHost(ledgerHost, ledgerPort, clientConfig)(ec, aesf)
      ledgerId = client.ledgerId
      transactionClient = client.transactionClient
      fetchClient = client.activeContractSetClient
      commandServiceClient = client.commandServiceClient
      transactionFilter = TransactionFilter(Map(actingParty -> Filters.defaultInstance))
      ledgerEnd <- transactionClient.getLedgerEnd()
    } yield new LedgerClientSupport {
      def submitCommand[T](command: Command): Future[Empty] = {
        val commands = Commands(
          ledgerId = ledgerId.unwrap,
          workflowId = WorkflowId.unwrap(workflowId),
          applicationId = ApplicationId.unwrap(applicationId),
          commandId = UUID.randomUUID.toString,
          party = actingParty,
          commands = Seq(command)
        )
        commandServiceClient.submitAndWait(SubmitAndWaitRequest(Some(commands)))
      }

      def subscribe(max: Option[Long])
                   (f: Transaction => Unit): Future[Done] = {
        val source: Source[Transaction, NotUsed] =
          transactionClient.getTransactions(ledgerEnd.offset.get, None, transactionFilter)
        max.fold(source)(n => source.take(n)) runForeach f
      }

      def subscribeTree(max: Option[Long])
                   (f: TransactionTree => Unit): Future[Done] = {
        val source: Source[TransactionTree, NotUsed] =
          transactionClient.getTransactionTrees(ledgerEnd.offset.get, None, transactionFilter)
        max.fold(source)(n => source.take(n)) runForeach f
      }

      def fetch(max: Option[Long])
               (f: GetActiveContractsResponse => Unit): Future[Done] = {
        val source = fetchClient.getActiveContracts(transactionFilter)
        max.fold(source)(n => source.take(n)) runForeach f
      }

      override def shutdown(): Unit = {
        println("Shutting down...")
        Await.result(actorSystem.terminate(), 10.seconds)
      }
    }
  }
}