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

package com.bytabit.ft.util

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import com.bytabit.ft.util.ListenerUpdater.{AddListener, Command, RemoveListener}

object ListenerUpdater {

  sealed trait Command

  case class AddListener(actor: ActorRef) extends Command

  case class RemoveListener(actor: ActorRef) extends Command

}

trait ListenerUpdater {

  private var listeners = Seq[ActorRef]()

  val log: LoggingAdapter

  def sendToListeners(event: Any) =
    listeners foreach { l =>
      log.debug("send event: {} to listener: {}", event, l)
      l ! event
    }

  def handleListenerCommand(cmd: Command): Unit = cmd match {

    case AddListener(actor) if !listeners.contains(actor) =>
      listeners = listeners :+ actor

    case RemoveListener(actor) =>
      listeners = listeners.filter(_ != actor)
  }
}