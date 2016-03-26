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

package org.bytabit.ft.arbitrator

import java.net.URL

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.persistence.{PersistentActor, SnapshotOffer}
import org.bytabit.ft.arbitrator.ArbitratorClientManager._
import org.bytabit.ft.trade.BuyProcess.{ReceiveFiat, TakeSellOffer}
import org.bytabit.ft.trade.SellProcess.{AddSellOffer, CancelSellOffer, SendFiat}
import org.bytabit.ft.trade.{ArbitrateProcess, BuyProcess, SellProcess, TradeFSM}
import org.bytabit.ft.util.ListenerUpdater
import org.bytabit.ft.util.ListenerUpdater.AddListener

object ArbitratorClientManager {

  // actor setup

  def props(walletMgr: ActorRef) = Props(new ArbitratorClientManager(walletMgr))

  val name = ArbitratorClientManager.getClass.getSimpleName
  val persistenceId = s"$name-persister"

  def actorOf(walletMgr: ActorRef)(implicit system: ActorSystem) = system.actorOf(props(walletMgr), name)

  // arbitrator commands

  sealed trait Command

  case object Start extends Command

  final case class AddArbitrator(url: URL) extends Command

  final case class RemoveArbitrator(url: URL) extends Command

  // events

  sealed trait Event

  case class ArbitratorAdded(url: URL) extends Event

  case class ArbitratorRemoved(url: URL) extends Event

  // data

  case class Data(arbitrators: Set[URL] = Set()) {
    def arbitratorAdded(url: URL): Data = {
      this.copy(arbitrators = arbitrators + url)
    }

    def arbitratorRemoved(url: URL): Data = {
      this.copy(arbitrators = arbitrators.filterNot(_ == url))
    }
  }

}

class ArbitratorClientManager(walletMgr: ActorRef) extends PersistentActor with ListenerUpdater {

  // implicits

  implicit val system = context.system

  // logging

  val log = Logging(context.system, this)

  // persistence

  override def persistenceId: String = ArbitratorClientManager.persistenceId

  private var data = Data()

  // apply commands to data

  def applyEvent(evt: Event, data: Data): Data = evt match {
    case ArbitratorAdded(url) => data.arbitratorAdded(url)
    case ArbitratorRemoved(url) => data.arbitratorRemoved(url)
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
      data.arbitrators.foreach { u =>
        startArbitratorClientFSM(u)
        context.sender ! ArbitratorAdded(u)
      }

    case AddArbitrator(url) if !data.arbitrators.contains(url) =>
      startArbitratorClientFSM(url)
      val aa = ArbitratorAdded(url)
      persist(aa)(updateData)
      context.sender ! aa

    case RemoveArbitrator(u: URL) =>
      // TODO FT-24: return errors if arbitrator in use for active trades
      if (data.arbitrators.contains(u)) {
        val ar = ArbitratorRemoved(u)
        persist(ar)(updateData)
        context.sender ! ar
        stopArbitratorClientFSM(u)
      }

    case cso: AddSellOffer =>
      arbitratorClientFSM(cso.offer.contract.arbitrator.url).foreach(_ ! cso)

    case cso: CancelSellOffer =>
      arbitratorClientFSM(cso.arbitratorUrl).foreach(_ ! cso)

    case TakeSellOffer(url, oid, fdd) =>
      arbitratorClientFSM(url).foreach(_ ! TakeSellOffer(url, oid, fdd))

    case ReceiveFiat(url, oid) =>
      arbitratorClientFSM(url).foreach(_ ! ReceiveFiat(url, oid))

    case SendFiat(url, oid) =>
      arbitratorClientFSM(url).foreach(_ ! SendFiat(url, oid))

    case rcd: SellProcess.RequestCertifyDelivery =>
      arbitratorClientFSM(rcd.arbitratorUrl).foreach(_ ! rcd)

    case rcd: BuyProcess.RequestCertifyDelivery =>
      arbitratorClientFSM(rcd.arbitratorUrl).foreach(_ ! rcd)

    case cfs: ArbitrateProcess.CertifyFiatSent =>
      arbitratorClientFSM(cfs.arbitratorUrl).foreach(_ ! cfs)

    case cfns: ArbitrateProcess.CertifyFiatNotSent =>
      arbitratorClientFSM(cfns.arbitratorUrl).foreach(_ ! cfns)

    case evt: ArbitratorFSM.Event =>
      sendToListeners(evt)

    case evt: TradeFSM.Event =>
      sendToListeners(evt)

    case "snap" => saveSnapshot(data)

    case e =>
      log.error(s"Unexpected event from ${context.sender()}: $e.toString")
  }

  // start/stop arbitrator client FSMs

  def startArbitratorClientFSM(url: URL) = {
    val arbitratorClientRef = context.actorOf(ArbitratorFSM.props(url, walletMgr), ArbitratorFSM.name(url))
    arbitratorClientRef ! ArbitratorClient.Start
  }

  def stopArbitratorClientFSM(url: URL) = {
    arbitratorClientFSM(url).foreach(context.stop)
  }

  // find arbitrator client FSM
  def arbitratorClientFSM(url: URL): Option[ActorRef] = context.child(ArbitratorFSM.name(url))
}