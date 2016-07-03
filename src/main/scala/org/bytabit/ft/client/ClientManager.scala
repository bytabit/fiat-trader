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
import org.bytabit.ft.client.ClientManager.{ProfileNameUpdated, _}
import org.bytabit.ft.client.model.{ClientProfile, PaymentDetails}
import org.bytabit.ft.trade.{ArbitrateProcess, BtcBuyProcess, BtcSellProcess, TradeProcess}
import org.bytabit.ft.util.{Config, PaymentMethod}
import org.bytabit.ft.wallet.WalletManager.InsufficientBtc
import org.bytabit.ft.wallet.{EscrowWalletManager, TradeWalletManager, WalletManager}
import org.joda.money.CurrencyUnit

import scala.reflect._

object ClientManager {

  // actor setup

  def props = Props(new ClientManager())

  val name = ClientManager.getClass.getSimpleName
  val persistenceId = s"$name-persister"

  def actorOf(system: ActorSystem) =
    system.actorOf(props, name)

  // client manager commands

  sealed trait Command

  final case class AddServer(url: URL) extends Command

  final case class RemoveServer(url: URL) extends Command

  case object FindServers extends Command

  case object FindClientProfile extends Command

  case class UpdateProfileName(name: String) extends Command

  case class UpdateProfileEmail(email: String) extends Command

  case class AddPaymentDetails(paymentDetails: PaymentDetails) extends Command

  case class RemovePaymentDetails(currencyUnit: CurrencyUnit, paymentMethod: PaymentMethod) extends Command

  // events

  sealed trait Event

  case class ClientCreated(profile: ClientProfile) extends Event

  case class ServerAdded(url: URL) extends Event

  case class ServerRemoved(url: URL) extends Event

  case class FoundServers(urls: Set[URL]) extends Event

  case class FoundClientProfile(profile: ClientProfile) extends Event

  case class ProfileNameUpdated(name: String) extends Event

  case class ProfileEmailUpdated(email: String) extends Event

  // states

  sealed trait State extends FSMState

  case object ADDED extends State {
    override def identifier: String = "ADDED"
  }

  case object CREATED extends State {
    override def identifier: String = "CREATED"
  }

  // data

  sealed trait Data

  case class AddedClientManager() extends Data {
    def profileCreated(profile: ClientProfile): CreatedClientManager = {
      CreatedClientManager(profile)
    }
  }

  case class CreatedClientManager(clientProfile: ClientProfile, servers: Set[URL] = Set())
    extends Data {

    def clientAdded(url: URL): CreatedClientManager = {
      this.copy(servers = servers + url)
    }

    def clientRemoved(url: URL): CreatedClientManager = {
      this.copy(servers = servers.filterNot(_ == url))
    }

    def nameUpdated(name: String): CreatedClientManager = {
      this.copy(clientProfile = this.clientProfile.copy(name = Some(name)))
    }

    def emailUpdated(email: String): CreatedClientManager = {
      this.copy(clientProfile = this.clientProfile.copy(email = Some(email)))
    }
  }

}

class ClientManager() extends PersistentFSM[State, Data, Event] {

  // implicits

  implicit val system = context.system

  // persistence

  override def persistenceId: String = ClientManager.persistenceId

  override def domainEventClassTag: ClassTag[ClientManager.Event] = classTag[ClientManager.Event]

  // apply event to state and data

  def applyEvent(evt: ClientManager.Event, data: Data): Data = (evt, data) match {
    case (ClientCreated(p), data: AddedClientManager) => data.profileCreated(p)
    case (ServerAdded(u), data: CreatedClientManager) => data.clientAdded(u)
    case (ServerRemoved(u), data: CreatedClientManager) => data.clientRemoved(u)
    case (ProfileNameUpdated(n), data: CreatedClientManager) => data.nameUpdated(n)
    case (ProfileEmailUpdated(e), data: CreatedClientManager) => data.emailUpdated(e)
    case _ =>
      log.warning(s"unexpected event: $evt, data: $data")
      data
  }

  // Listen for wallet manager events
  system.eventStream.subscribe(context.self, WalletManager.TradeWalletRunning.getClass)
  system.eventStream.subscribe(context.self, classOf[WalletManager.ClientProfileIdCreated])

  // Create wallets
  val tradeWalletMgrRef: ActorRef = context.actorOf(TradeWalletManager.props, TradeWalletManager.name)
  val escrowWalletMgrRef: ActorRef = context.actorOf(EscrowWalletManager.props, EscrowWalletManager.name)

  startWith(ADDED, AddedClientManager())

  when(ADDED) {
    case Event(WalletManager.TradeWalletRunning, d: AddedClientManager) =>
      tradeWalletMgrRef ! TradeWalletManager.CreateClientProfileId
      stay()

    case Event(WalletManager.ClientProfileIdCreated(clientProfileId), d: AddedClientManager) =>
      val cpc = ClientCreated(ClientProfile(clientProfileId))
      goto(CREATED) applying cpc

    case Event(FindServers, d: AddedClientManager) =>
      stay()
  }

  when(CREATED) {

    case Event(WalletManager.TradeWalletRunning, d: CreatedClientManager) =>
      d.servers.foreach { u =>
        startClient(u)
      }
      stay()

    case Event(UpdateProfileName(n), d: CreatedClientManager) =>
      val pnu = ProfileNameUpdated(n)
      goto(CREATED) applying pnu andThen { u =>
        sender ! pnu
      }

    case Event(UpdateProfileEmail(e), d: CreatedClientManager) =>
      val peu = ProfileEmailUpdated(e)
      goto(CREATED) applying peu andThen { u =>
        sender ! peu
      }

    case Event(ac: AddServer, d: CreatedClientManager) if !d.servers.contains(ac.url) =>
      val ca = ServerAdded(ac.url)
      goto(CREATED) applying ca andThen {
        case ud: CreatedClientManager =>
          startClient(ca.url)
          context.sender ! ca
        case ud =>
          log.warning(s"unexpected updated data: $ud")
      }

    case Event(rc: RemoveServer, d: CreatedClientManager) =>
      // TODO FT-24: return errors if client in use for active trades
      if (d.servers.contains(rc.url)) {
        val cr = ServerRemoved(rc.url)
        goto(CREATED) applying cr andThen {
          case ud: CreatedClientManager =>
            context.sender ! cr
            stopClient(cr.url)
          case ud =>
            log.warning(s"unexpected updated data: $ud")
        }
      } else
        stay()

    case Event(FindServers, d: CreatedClientManager) =>
      sender() ! FoundServers(d.servers)
      stay()

    case Event(FindClientProfile, d: CreatedClientManager) =>
      sender() ! FoundClientProfile(d.clientProfile)
      stay()

    case Event(ac: ArbitratorManager.Command, d: CreatedClientManager) =>
      client(ac.url).foreach(_ ! ac)
      stay()

    case Event(apc: ArbitrateProcess.Command, d: CreatedClientManager) =>
      client(apc.url).foreach(_ ! apc)
      stay()

    case Event(spc: BtcSellProcess.Command, d: CreatedClientManager) =>
      client(spc.url).foreach(_ ! spc)
      stay()

    case Event(bpc: BtcBuyProcess.Command, d: CreatedClientManager) =>
      client(bpc.url).foreach(_ ! bpc)
      stay()

    case Event(evt: EventClient.Event, d: CreatedClientManager) =>
      system.eventStream.publish(evt)
      stay()

    case Event(evt: ArbitratorManager.Event, d: CreatedClientManager) =>
      system.eventStream.publish(evt)
      stay()

    case Event(evt: TradeProcess.Event, d: CreatedClientManager) =>
      system.eventStream.publish(evt)
      stay()

    case Event(evt: InsufficientBtc, d: CreatedClientManager) =>
      system.eventStream.publish(evt)
      stay()

    case Event(e, d: Data) =>
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