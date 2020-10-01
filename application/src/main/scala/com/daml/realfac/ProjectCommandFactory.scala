package com.daml.realfac

object ProjectCommandFactory {

  def createCommands(projectLedgerService: ProjectLedgerService): (String, Map[String, Cli.Command]) = {
    help -> Map(
      "deposit" -> {
        case List(ref, serviceProvider, amount) if amount.forall(_.isDigit) => projectLedgerService.deposit(serviceProvider, amount.toInt, ref)
      },
      "cash-arrived" -> {
        case List(ref) => projectLedgerService.cashArrived(ref)
      },
      "withdraw" -> {
        case List(amount) if amount.forall(_.isDigit) => projectLedgerService.withdraw(amount.toInt)
      },
      "create" -> {
        case List(ref, serviceProvider, client) => projectLedgerService.createProject(ref, serviceProvider, client)
      },
      "add" -> {
        case ref :: taskId :: amount :: deps  if amount.forall(_.isDigit) => projectLedgerService.addTask(ref, taskId, amount.toInt, deps)
      },
      "remove" -> {
        case List(ref) => projectLedgerService.removeTask(ref)
      },
      "sign" -> {
        case List(ref) => projectLedgerService.sign(ref)
      },
      "finalize" -> {
        case List(ref) => projectLedgerService.finalize(ref)
      },
      "start" -> {
        case List(ref, taskId) => projectLedgerService.startTask(ref, taskId)
      },
      "complete" -> {
        case List(ref, taskId) => projectLedgerService.completeTask(ref, taskId)
      },
      "approve" -> {
        case List(ref, taskId) => projectLedgerService.approveTask(ref, taskId)
      },
      "reject" -> {
        case List(ref, taskId) => projectLedgerService.rejectTask(ref, taskId)
      },
      "finish" -> {
        case List(ref) => projectLedgerService.archiveProject(ref)
      },
    )
  }

  val help: String =
    """
      |Usage:
      |
      |-- Cash Management
      |deposit [ref] [service provider] [amount]   -- Client initiates cash transfer to service provider
      |cash-arrived [ref]                          -- Service provider acknowledges: money arrived
      |withdraw [amount]                           -- Client withdraws cash from it's account
      |
      |-- Project Creation
      |create [ref] [service provider] [client]    -- Vendor creates project
      |add [ref] [task] [amount] [dependent task]* -- Vendor adds task with 0 or more tasks to depend on
      |remove [ref]                                -- Vendor removes the last added task
      |sign [ref]                                  -- Service provider or Client signs the Project
      |finalize [ref]                              -- Client signs-off the Project
      |
      |-- Project Management
      |start [ref] [task]                          -- Vendor starts task
      |complete [ref] [task]                       -- Vendor completes task
      |approve [ref] [task]                        -- Client approves completion
      |reject [ref] [task]                         -- Client rejects completion
      |finish [ref]                                -- Service provider finishes a project (all tasks must be done)
      |
      |""".stripMargin

}
