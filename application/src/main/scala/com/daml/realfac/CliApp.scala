package com.daml.realfac

import scala.concurrent.ExecutionContext.Implicits.global

object CliApp extends App {

  if (args.length != 3) {
    println("Usage: LEDGER_HOST LEDGER_PORT ACTING_PARTY")
    System.exit(-1)
  }
  private val Array(ledgerHost, ledgerPort, actingParty) = args

  private val cli = Cli.create(actingParty)

  for {
    client <- LedgerClientSupport.create(ledgerHost, ledgerPort.toInt, actingParty)
    projectLedger <- ProjectLedgerService.create(client, cli.notify, actingParty)
  } yield {
    val (help, commands) = ProjectCommandFactory.createCommands(projectLedger)
    cli.runRepl(help, commands)
    println("Stopping")
    client.shutdown()
    println("Stopped")
  }
}
