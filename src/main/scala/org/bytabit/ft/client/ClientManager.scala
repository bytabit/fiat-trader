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

package org.bytabit.ft.client

import java.net.URL

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.client.ClientManager._
import org.bytabit.ft.trade.{ArbitrateProcess, BtcBuyProcess, BtcSellProcess, TradeProcess}
import org.bytabit.ft.util.Config
import org.bytabit.ft.wallet.WalletManager.InsufficentBtc

import scala.reflect._

object ClientManager {

  // actor setup

  def props(tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) =
    Props(new ClientManager(tradeWalletMgrRef, escrowWalletMgrRef))

  val name = ClientManager.getClass.getSimpleName
  val persistenceId = s"$name-persister"

  def actorOf(system: ActorSystem, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) =
    system.actorOf(props(tradeWalletMgrRef, escrowWalletMgrRef), name)

  // client manager commands

  sealed trait Command

  case object Start extends Command

  final case class AddClient(url: URL) extends Command

  final case class RemoveClient(url: URL) extends Command

  // events

  sealed trait Event

  case class ClientAdded(url: URL) extends Event

  case class ClientRemoved(url: URL) extends Event

  // states

  sealed trait ClientManagerState extends FSMState

  case object ADDED extends ClientManagerState {
    override def identifier: String = "ADDED"
  }

  // data

  case class ClientManagerData(clients: Set[URL] = Set()) {

    def clientAdded(url: URL): ClientManagerData = {
      this.copy(clients = clients + url)
    }

    def clientRemoved(url: URL): ClientManagerData = {
      this.copy(clients = clients.filterNot(_ == url))
    }
  }

}

class ClientManager(tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef)
  extends PersistentFSM[ClientManagerState, ClientManagerData, ClientManager.Event] {

  // implicits

  implicit val system = context.system

  // persistence

  override def persistenceId: String = ClientManager.persistenceId

  override def domainEventClassTag: ClassTag[ClientManager.Event] = classTag[ClientManager.Event]

  // apply event to state and data

  def applyEvent(evt: ClientManager.Event, data: ClientManagerData): ClientManagerData = evt match {
    case ClientAdded(url) => data.clientAdded(url)
    case ClientRemoved(url) => data.clientRemoved(url)
  }

  startWith(ADDED, ClientManagerData())

  when(ADDED) {

    case Event(ClientManager.Start, d: ClientManagerData) =>
      goto(ADDED) andThen { ud: ClientManagerData =>
        ud.clients.foreach { u =>
          startClient(u)
          system.eventStream.publish(ClientAdded(u))
        }
      }

    case Event(ac: AddClient, d: ClientManagerData) if !d.clients.contains(ac.url) =>
      val ca = ClientAdded(ac.url)
      goto(ADDED) applying ca andThen { ud: ClientManagerData =>
        startClient(ca.url)
        context.sender ! ca
      }

    case Event(rc: RemoveClient, d: ClientManagerData) =>
      // TODO FT-24: return errors if client in use for active trades
      if (d.clients.contains(rc.url)) {
        val cr = ClientRemoved(rc.url)
        goto(ADDED) applying cr andThen { ud: ClientManagerData =>
          context.sender ! cr
          stopClient(cr.url)
        }
      } else
        stay()

    case Event(ac: ArbitratorManager.Command, d: ClientManagerData) =>
      client(ac.url).foreach(_ ! ac)
      stay()

    case Event(apc: ArbitrateProcess.Command, d: ClientManagerData) =>
      client(apc.url).foreach(_ ! apc)
      stay()

    case Event(spc: BtcSellProcess.Command, d: ClientManagerData) =>
      client(spc.url).foreach(_ ! spc)
      stay()

    case Event(bpc: BtcBuyProcess.Command, d: ClientManagerData) =>
      client(bpc.url).foreach(_ ! bpc)
      stay()

    case Event(evt: EventClient.Event, d: ClientManagerData) =>
      system.eventStream.publish(evt)
      stay()

    case Event(evt: ArbitratorManager.Event, d: ClientManagerData) =>
      system.eventStream.publish(evt)
      stay()

    case Event(evt: TradeProcess.Event, d: ClientManagerData) =>
      system.eventStream.publish(evt)
      stay()

    case Event(evt: InsufficentBtc, d: ClientManagerData) =>
      system.eventStream.publish(evt)
      stay()

    case Event(e, d: ClientManagerData) =>
      log.error(s"Unexpected event from ${context.sender()}: $e.toString")
      stay()
  }

  // start/stop clients

  def name(url: URL): String = {
    if (Config.arbitratorEnabled) {
      ArbitratorClient.name(url)
    } else {
      TraderClient.name(url)
    }
  }

  def props(url: URL): Props = {
    if (Config.arbitratorEnabled && Config.publicUrl == url) {
      ArbitratorClient.props(url, tradeWalletMgrRef, escrowWalletMgrRef)
    } else {
      TraderClient.props(url, tradeWalletMgrRef, escrowWalletMgrRef)
    }
  }

  def startClient(url: URL): Unit = {
    val clientRef = context.actorOf(props(url), name(url))
    clientRef ! EventClient.Start
  }

  def stopClient(url: URL): Unit = {
    client(url).foreach(context.stop)
  }

  // find client
  def client(url: URL): Option[ActorRef] = context.child(name(url))
}