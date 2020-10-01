package com.daml.realfac

import java.util

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.tools.jline_embedded.console.ConsoleReader
import scala.util.{Failure, Success}

trait Cli {
  def notify(s: String): Unit
  def runRepl(help: String,
              commands: Map[String, Cli.Command]): Unit
}

object Cli {
  type Command = PartialFunction[List[String], Future[Unit]]

  def create(actingParty: String): Cli = {
    var readingLine = false
    val consoleReader = new ConsoleReader()
    consoleReader.setHistoryEnabled(true)
    consoleReader.setPrompt(actingParty + "> ")

    def out(s: String): Unit = consoleReader.synchronized {
      if (readingLine) {
        println(s"\r$s")
        consoleReader.redrawLine()
        consoleReader.flush()
      } else {
        println(s)
      }
    }

    val BrightGreen = "\u001b[38;5;10m"
    val Reset = "\u001b[0m"
    val BrightCyan = "\u001b[38;5;87m"

    def outNotification(s: String): Unit = out(s"${BrightGreen}Notification -> $s$Reset")

    def outCommandResult(s: String): Unit = {
      val extracted = s.split("User abort:").toList match {
        case _ :: ex1 :: _ => ex1.split("Details:").toList match {
          case extracted :: _ :: _ => extracted.trim
          case _ => ex1.trim
        }
        case _ => s
      }
      out(s"${BrightCyan}Command Result -> $extracted$Reset")
    }

    new Cli {
      override def notify(s: String): Unit = outNotification(s)

      // TODO WARNING this is not reusable, multiple calls here can have unexpected results
      override def runRepl(help: String, commands: Map[String, Command]): Unit = {
        consoleReader.addCompleter((s: String, cursor: Int, list: util.List[CharSequence]) => {
          commands
            .keys
            .++(List("quit", "exit", "reject"))
            .filter(_.startsWith(s))
            .map(_.drop(s.length))
            .foreach(list.add)
          cursor
        })

        @tailrec
        def cliLoop(): Unit = {
          consoleReader.synchronized {
            readingLine = true
          }
          val line = consoleReader.readLine()
          consoleReader.synchronized {
            readingLine = false
          }
          val command = line.split(" ").map(_.trim).filter(_.nonEmpty).toList
          if (command != List("quit") && command != List("exit")) {
            command match {
              case List("help") => out(help)
              case name :: args => commands.get(name) match {
                case None => out("Unknown command\n" + help)
                case Some(commandFun) if commandFun.isDefinedAt(args) => commandFun(args).onComplete {
                  case Success(_) => outCommandResult("Command successful")
                  case Failure(exception) => outCommandResult("Command failed: " + exception.getMessage)
                }
                case Some(_) => out(s"Invalid arguments for '$name' command\n" + help)
              }
              case Nil =>
            }
            cliLoop()
          }
        }
        cliLoop()
      }
    }
  }
}
