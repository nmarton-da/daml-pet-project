package com.daml.realfac

import com.daml.ledger.api.v1.commands.Command
import com.daml.ledger.api.v1.event.{CreatedEvent, ExercisedEvent}
import com.daml.ledger.api.v1.{value => V}
import com.daml.ledger.client.binding.{Template, ValueDecoder, Contract => C, Primitive => P}
import com.daml.realfac.model.RealFac.CashAccount.CashAccount.{CashAccount, CashArrived, PendingDeposit, Withdraw}
import com.daml.realfac.model.RealFac.Project.Project._
import com.daml.realfac.model.RealFac.Project.ProjectParties.ProjectParties
import com.daml.realfac.model.RealFac.Project.Task.{TaskDescriptor, TaskState}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProjectLedgerService {
  def deposit(serviceProvider: String, amount: Int, ref: String): Future[Unit]

  def cashArrived(ref: String): Future[Unit]

  def withdraw(amount: Int): Future[Unit]

  def createProject(ref: String, serviceProvider: String, client: String): Future[Unit]

  def addTask(ref: String, taskId: String, cash: Int, deps: List[String]): Future[Unit]

  def removeTask(ref: String): Future[Unit]

  def sign(ref: String): Future[Unit]

  def finalize(ref: String): Future[Unit]

  def startTask(ref: String, taskId: String): Future[Unit]

  def completeTask(ref: String, taskId: String): Future[Unit]

  def approveTask(ref: String, taskId: String): Future[Unit]

  def rejectTask(ref: String, taskId: String): Future[Unit]

  def archiveProject(ref: String): Future[Unit]
}

object ProjectLedgerService {
  def create(ledgerClient: LedgerClientSupport,
             notify: String => Unit,
             actingParty: String): Future[ProjectLedgerService] = {
    // TODO maybe concurrent? grpc intake singlethreaded?
    val cashAccounts: mutable.Map[String, C[CashAccount]] = mutable.Map.empty
    val pendingDeposits: mutable.Map[String, C[PendingDeposit]] = mutable.Map.empty
    val projects: mutable.Map[String, C[Project]] = mutable.Map.empty
    val pendingProjects: mutable.Map[String, C[PendingProject]] = mutable.Map.empty

    def processExercised(event: ExercisedEvent): Unit = {
      if (event.choice == "ArchiveIt") {
        projects.values.find(_.contractId == event.contractId).foreach {
          project =>
            notify(s"Project finished: ${project.value.ref}")
        }
      }

    }

    def processCreated(created: CreatedEvent): Unit = {
      def updateCache[A <: Template[A] : ValueDecoder, KEY]
      (cache: mutable.Map[KEY, C[A]])
      (key: A => KEY): Option[(Option[A], A)] =
        decodeCreated[A](created).map {
          c =>
            val result = cache.get(key(c.value)).map(_.value) -> c.value
            cache.synchronized(cache.update(key(c.value), c))
            result
        }

      updateCache(projects)(_.ref).foreach {
        case (None, c) => notify(s"New Project found: ${c.ref}")
        case (Some(oldC), c) => oldC.tasks.zip(c.tasks).foreach {
          case (oldTask, newTask) if oldTask.state != newTask.state =>
            notify(s"Project ${c.ref} Task ${oldTask.descriptor.id} ${TaskChanged(oldTask.state, newTask.state)}")
          case _ => ()
        }
      }

      updateCache(pendingDeposits)(_.ref).foreach {
        case (None, c) => notify(s"New deposit found: ${c.ref}")
        case _ => ()
      }

      def balance(ca: CashAccount) = s"      balance: ${ca.balance} reserved: ${ca.reserved}"

      def owner(ca: CashAccount) = P.Party.unwrap(ca.owner)

      def caChangeNotification(ca: CashAccount, s: String): Unit =
        notify(s"CashAccount [${owner(ca)}] $s${balance(ca)}")

      updateCache(cashAccounts)(owner).foreach {
        case (None, c) =>
          notify(s"New cash account found: ${owner(c)}${balance(c)}")
        case (Some(oldC), c) if oldC.balance < c.balance =>
          caChangeNotification(c, s"deposited ${c.balance - oldC.balance}")
        case (Some(oldC), c) if oldC.balance == c.balance =>
          caChangeNotification(c, s"reserved ${c.reserved - oldC.reserved}")
        case (Some(oldC), c) if oldC.reserved == c.reserved =>
          caChangeNotification(c, s"withdrawn ${oldC.balance - c.balance}")
        case (Some(oldC), c) =>
          caChangeNotification(c, s"transferred ${oldC.balance - c.balance}")
      }

      def pendingProjectString(p: PendingProject): String =
        s"\n  between service provider=${p.projectParties.serviceProvider} vendor=${p.projectParties.vendor} client=${p.projectParties.client}" +
        s"\n  already signed=" + p.alreadySigned.mkString(", ") +
        s"\n  tasks=\n" + p.taskDescriptors.map(t => s"    ${t.id} cash-on-complete=${t.cashOnComplete} depends-on=" + t.dependsOn.mkString(", ")).mkString("\n")
      updateCache(pendingProjects)(_.ref).foreach {
        case (None, c) =>
          notify(s"New Pending-Project found: ${c.ref}${pendingProjectString(c)}")
        case (Some(oldC), c) if oldC.alreadySigned != c.alreadySigned =>
          notify(s"Pending-Project ${c.ref} was signed by ${c.alreadySigned.toSet.--(oldC.alreadySigned.toSet).mkString(", ")}${pendingProjectString(c)}")
        case (Some(oldC), c) if oldC.taskDescriptors.size < c.taskDescriptors.size =>
          notify(s"Pending-Project ${c.ref} has a new task${pendingProjectString(c)}")
        case (Some(oldC), c) if oldC.taskDescriptors.size > c.taskDescriptors.size =>
          notify(s"Pending-Project ${c.ref} has the last task removed${pendingProjectString(c)}")
        case _ =>
      }

    }

    def execute[A, KEY](map: mutable.Map[KEY, C[A]], key: KEY)(f: C[A] => Command): Future[Unit] = {
      map.get(key) match {
        case Some(a) => ledgerClient.submitCommand(f(a)).map(_ => ())
        case None => Future.failed(new Exception(s"no entity for key $key"))
      }
    }

    println("Fetching current state...")
    for {
      _ <- ledgerClient.fetch(None)(_.activeContracts.foreach(processCreated))
      _ = ledgerClient.subscribe(None)(_.events.flatMap(_.event.created).foreach(processCreated))
      _ = ledgerClient.subscribeTree(None)(_.eventsById.values.flatMap(x => Option(x.getExercised)).foreach(processExercised))
    } yield {
      println("Ready! listening for updates...")
      new ProjectLedgerService {
        override def deposit(serviceProvider: String, amount: Int, ref: String): Future[Unit] =
          ledgerClient.submitCommand(PendingDeposit(
            P.Party(serviceProvider),
            P.Party(actingParty),
            amount,
            ref
          ).create.command).map(_ => ())

        override def cashArrived(ref: String): Future[Unit] =
          execute(pendingDeposits, ref)(_.contractId.exerciseCashArrived(P.Party(actingParty), CashArrived()).command)

        override def withdraw(amount: Int): Future[Unit] =
          execute(cashAccounts, actingParty)(_.contractId.exerciseWithdraw(P.Party(actingParty), Withdraw(amount.toInt)).command)

        override def createProject(ref: String, serviceProvider: String, client: String): Future[Unit] =
          ledgerClient.submitCommand(PendingProject(
            ProjectParties(P.Party(serviceProvider), P.Party(client), P.Party(actingParty)),
            ref,
            List.empty,
            List.empty
          ).create.command).map(_ => ())

        override def addTask(ref: String, taskId: String, cash: Int, deps: List[String]): Future[Unit] = {
          val task = TaskDescriptor(taskId, "", deps, cash)
          execute(pendingProjects, ref)(_.contractId.exerciseAddTask(P.Party(actingParty), task).command)
        }

        override def removeTask(ref: String): Future[Unit] =
          execute(pendingProjects, ref)(_.contractId.exerciseRemoveLastTask(P.Party(actingParty)).command)

        override def sign(ref: String): Future[Unit] =
          execute(pendingProjects, ref)(_.contractId.exerciseSign(P.Party(actingParty), Sign(P.Party(actingParty))).command)

        override def finalize(ref: String): Future[Unit] =
          execute(pendingProjects, ref)(_.contractId.exerciseFinalize(P.Party(actingParty)).command)

        override def startTask(ref: String, taskId: String): Future[Unit] =
          execute(projects, ref)(_.contractId.exerciseStart(P.Party(actingParty), Start(taskId)).command)

        override def completeTask(ref: String, taskId: String): Future[Unit] =
          execute(projects, ref)(_.contractId.exerciseComplete(P.Party(actingParty), Complete(taskId)).command)

        override def approveTask(ref: String, taskId: String): Future[Unit] =
          execute(projects, ref)(_.contractId.exerciseAck(P.Party(actingParty), Ack(taskId)).command)

        override def rejectTask(ref: String, taskId: String): Future[Unit] =
          execute(projects, ref)(_.contractId.exerciseReject(P.Party(actingParty), Reject(taskId)).command)

        override def archiveProject(ref: String): Future[Unit] =
          execute(projects, ref)(_.contractId.exerciseArchiveIt(P.Party(actingParty)).command)
      }
    }
  }

  def decodeCreated[A <: Template[A] : ValueDecoder](event: CreatedEvent): Option[C[A]] = {
    val decoder = implicitly[ValueDecoder[A]]
    for {
      record <- event.createArguments: Option[V.Record]
      a <- decoder.read(V.Value.Sum.Record(record)): Option[A]
    } yield C(
      P.ContractId(event.contractId),
      a,
      event.agreementText,
      event.signatories,
      event.observers,
      event.contractKey)
  }

  def TaskChanged(oldTask: TaskState, newTask: TaskState): String = (oldTask, newTask) match {
    case (TaskState.Pending, TaskState.InProgress) => "started"
    case (TaskState.InProgress, TaskState.PendingDone) => "finished"
    case (TaskState.PendingDone, TaskState.Done) => "approved"
    case (TaskState.PendingDone, TaskState.InProgress) => "rejected"
    case _ => newTask.toString
  }

}