/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package akka.dispatch

//import akka.actor.ActorCell
//import akka.util.Unsafe
//
//import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * The event-based ``Dispatcher`` binds a set of Actors to a thread pool backed up by a
  * `BlockingQueue`.
  *
  * The preferred way of creating dispatchers is to define configuration of it and use the
  * the `lookup` method in [[akka.dispatch.Dispatchers]].
  *
  * @param throughput positive integer indicates the dispatcher will only process so much messages at a time from the
  *                   mailbox, without checking the mailboxes of other actors. Zero or negative means the dispatcher
  *                   always continues until the mailbox is empty.
  *                   Larger values (or zero or negative) increase throughput, smaller values increase fairness
  */
//class AndroidDispatcher(_configurator: MessageDispatcherConfigurator,
//                        id: String,
//                        throughput: Int,
//                        throughputDeadlineTime: Duration,
//                        executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
//                        shutdownTimeout: FiniteDuration)
//  extends Dispatcher(_configurator, id, throughput, throughputDeadlineTime, executorServiceFactoryProvider, shutdownTimeout) {

import AbstractMessageDispatcher.inhabitantsOffset
import MessageDispatcher.{actors, debug}

/**
  * If you override it, you must call it. But only ever once. See "attach" for only invocation.
  *
  * INTERNAL API
  */
//  override protected[akka] def register(actor: ActorCell) {
//    if (debug) actors.put(this, actor.self)
//    addInhabitants(+1)
//  }

/**
  * If you override it, you must call it. But only ever once. See "detach" for the only invocation
  *
  * INTERNAL API
  */
//  override protected[akka] def unregister(actor: ActorCell) {
//    if (debug) actors.remove(this, actor.self)
//    addInhabitants(-1)
//    val mailBox = actor.swapMailbox(mailboxes.deadLetterMailbox)
//    mailBox.becomeClosed()
//    mailBox.cleanUp()
//  }

// Disable so that wrong addInhabitants won't be called
//  /** Override this to define which runnables will be batched. */
//  override def batchable(runnable: Runnable): Boolean = runnable match {
//    case _ ⇒ false
//  }

//  private final def getAndAddLong(obj: Object, fieldOffset: Long, addValue: Long): Long = {
//    var currentValue: Long = 0
//    do {
//      currentValue = Unsafe.instance.getLongVolatile(obj, fieldOffset)
//    } while (!Unsafe.instance.compareAndSwapLong(obj, fieldOffset, currentValue, currentValue + addValue))
//
//    currentValue
//  }

//  private final def addInhabitants(add: Long): Long = {
//    val old = getAndAddLong(this, inhabitantsOffset, add)
//    val ret = old + add
//    if (ret < 0) {
//      // We haven't succeeded in decreasing the inhabitants yet but the simple fact that we're trying to
//      // go below zero means that there is an imbalance and we might as well throw the exception
//      val e = new IllegalStateException("ACTOR SYSTEM CORRUPTED!!! A dispatcher can't have less than 0 inhabitants!")
//      reportFailure(e)
//      throw e
//    }
//    ret
//  }
//}
