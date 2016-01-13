/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytabit.ft.fxui.util

import java.util.concurrent.Executor
import javafx.application.Platform
import javafx.concurrent.{Service, Task}

import akka.actor.{ActorRef, ActorSystem, Inbox}
import akka.event.{Logging, LoggingAdapter}
import com.sun.glass.ui.Application

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object JavaFXExecutionContext {
  implicit val javaFxExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(command: Runnable): Unit = {
      Platform.runLater(command)
    }
  })
}

abstract class ActorFxService(system: ActorSystem) extends Service[Unit] {

  final val log: LoggingAdapter = Logging.getLogger(system, this)
  final val inbox: Inbox = Inbox.create(system)

  implicit val sys = system
  implicit val dis = system.dispatcher

  def sendMsg[T <: AnyRef](fRef: Future[ActorRef], msg: T) = {
    for {
      ref <- fRef
    } yield {
      inbox.send(ref, msg)
    }
  }

  protected def handler: (Any) => Unit

  def createTask: Task[Unit] = new Task[Unit] {

    protected def call: Unit = {
      while (!isCancelled) {
        val result = Try(inbox.receive(FiniteDuration(1, "minute")))
        if (result.isSuccess) {
          //log.debug(result.get.toString)
          Application.invokeLater(new Runnable {
            def run() {
              handler(result.get)
            }
          })
        }
      }
      log.info("ActorFXService Cancelled")
    }
  }
}