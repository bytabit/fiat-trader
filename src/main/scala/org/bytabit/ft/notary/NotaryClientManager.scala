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

package org.bytabit.ft.notary

import java.net.URL

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.persistence.{PersistentActor, SnapshotOffer}
import org.bytabit.ft.notary.NotaryClientManager._
import org.bytabit.ft.trade.BuyFSM.{ReceiveFiat, TakeSellOffer}
import org.bytabit.ft.trade.SellFSM.{AddSellOffer, CancelSellOffer}
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.util.ListenerUpdater
import org.bytabit.ft.util.ListenerUpdater.AddListener

object NotaryClientManager {

  // actor setup

  def props(walletMgr: ActorRef) = Props(new NotaryClientManager(walletMgr))

  val name = NotaryClientManager.getClass.getSimpleName
  val persistenceId = s"$name-persister"

  def actorOf(walletMgr: ActorRef)(implicit system: ActorSystem) = system.actorOf(props(walletMgr), name)

  // notary commands

  sealed trait Command

  case object Start extends Command

  final case class AddNotary(url: URL) extends Command

  final case class RemoveNotary(url: URL) extends Command

  // events

  sealed trait Event

  case class NotaryAdded(url: URL) extends Event

  case class NotaryRemoved(url: URL) extends Event

  // data

  case class Data(notaries: Seq[URL] = Seq()) {
    def notaryAdded(url: URL): Data = {
      this.copy(notaries = notaries :+ url)
    }

    def notaryRemoved(url: URL): Data = {
      this.copy(notaries = notaries.filterNot(_ == url))
    }
  }

}

class NotaryClientManager(walletMgr: ActorRef) extends PersistentActor with ListenerUpdater {

  // implicits

  implicit val system = context.system

  // logging

  val log = Logging(context.system, this)

  // persistence

  override def persistenceId: String = NotaryClientManager.persistenceId

  private var data = Data()

  // apply commands to data

  def applyEvent(evt: Event, data: Data): Data = evt match {
    case NotaryAdded(url) => data.notaryAdded(url)
    case NotaryRemoved(url) => data.notaryRemoved(url)
  }

  def updateData(evt: Event) = {
    data = applyEvent(evt, data)
  }

  override val receiveRecover: Receive = {

    case evt: Event =>
      updateData(evt)

    case SnapshotOffer(_, snapshot: Data) =>
      data = snapshot
  }

  override val receiveCommand: Receive = {

    // handlers for listener registration

    case c: ListenerUpdater.Command => handleListenerCommand(c)

    case Start =>
      self ! AddListener(context.sender())
      data.notaries.foreach { u =>
        startNotaryClientFSM(u)
        context.sender ! NotaryAdded(u)
      }

    case AddNotary(url) if !data.notaries.contains(url) =>
      startNotaryClientFSM(url)
      val aa = NotaryAdded(url)
      persist(aa)(updateData)
      context.sender ! aa

    case RemoveNotary(u: URL) =>
      // TODO issue #29, return errors if notary in use for active trades
      if (data.notaries.contains(u)) {
        val ar = NotaryRemoved(u)
        persist(ar)(updateData)
        context.sender ! ar
        stopNotaryClientFSM(u)
      }

    case cso: AddSellOffer =>
      notaryClientFSM(cso.offer.contract.notary.url).foreach(_ ! cso)

    case cso: CancelSellOffer =>
      notaryClientFSM(cso.notaryUrl).foreach(_ ! cso)

    case TakeSellOffer(url, oid, fdd) =>
      notaryClientFSM(url).foreach(_ ! TakeSellOffer(url, oid, fdd))

    case ReceiveFiat(url, oid) =>
      notaryClientFSM(url).foreach(_ ! ReceiveFiat(url, oid))

    case evt: NotaryClientFSM.Event =>
      sendToListeners(evt)

    case evt: TradeFSM.Event =>
      sendToListeners(evt)

    case "snap" => saveSnapshot(data)

    case e =>
      log.error(s"Unexpected event from ${context.sender()}: $e.toString")
  }

  // start/stop notary client FSMs

  def startNotaryClientFSM(url: URL) = {
    val notaryClientRef = context.actorOf(NotaryClientFSM.props(url, walletMgr), NotaryClientFSM.name(url))
    notaryClientRef ! NotaryClientFSM.Start
  }

  def stopNotaryClientFSM(url: URL) = {
    notaryClientFSM(url).foreach(context.stop)
  }

  // find notary client FSM
  def notaryClientFSM(url: URL): Option[ActorRef] = context.child(NotaryClientFSM.name(url))
}