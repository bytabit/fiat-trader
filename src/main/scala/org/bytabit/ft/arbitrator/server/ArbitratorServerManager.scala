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

package org.bytabit.ft.arbitrator.server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.pattern.ask
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.arbitrator.ArbitratorFSM
import org.bytabit.ft.arbitrator.ArbitratorFSM.{ArbitratorCreated, ContractAdded, ContractRemoved}
import org.bytabit.ft.arbitrator.server.ArbitratorServerManager._
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.model.Contract
import org.bytabit.ft.util.ListenerUpdater.AddListener
import org.bytabit.ft.util._
import org.bytabit.ft.wallet.WalletManager
import org.bytabit.ft.wallet.model.Arbitrator
import org.joda.money.CurrencyUnit
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object ArbitratorServerManager {

  // actor setup

  def props(walletMgr: ActorRef) = Props(new ArbitratorServerManager(walletMgr))

  val name = ArbitratorServerManager.getClass.getSimpleName
  val persistenceId = s"$name-persister"

  def actorOf(walletMgr: ActorRef)(implicit system: ActorSystem) =
    system.actorOf(props(walletMgr), name)

  // commands

  sealed trait Command

  case object Start extends Command

  final case class AddContractTemplate(currencyUnit: CurrencyUnit, deliveryMethod: FiatDeliveryMethod) extends Command {
    assert(Monies.isFiat(currencyUnit))
  }

  final case class RemoveContractTemplate(id: Sha256Hash) extends Command

  final case class PostTradeEvent(evt: TradeFSM.PostedEvent) extends Command

  // events

  sealed trait Event

  case class ArbitratorEventPosted(event: ArbitratorFSM.PostedEvent) extends Event {
    assert(event.posted.isDefined)
  }

  case class TradeEventPosted(event: TradeFSM.PostedEvent) extends Event {
    assert(event.posted.isDefined)
  }

  // data

  case class Data(arbitrator: Option[Arbitrator] = None, contract: Seq[Contract] = Seq(),
                  postedArbitratorEvents: Seq[ArbitratorFSM.PostedEvent] = Seq(),
                  postedTradeEvents: Seq[TradeFSM.PostedEvent] = Seq()) {

    def arbitratorCreated(a: Arbitrator) =
      this.copy(arbitrator = Some(a))

    def contractTemplateAdded(ct: Contract) =
      this.copy(contract = contract :+ ct)

    def contractTemplateRemoved(id: Sha256Hash) =
      this.copy(contract = contract.filterNot(_.id == id))

    def arbitratorEventPosted(event: ArbitratorFSM.PostedEvent) =
      this.copy(postedArbitratorEvents = postedArbitratorEvents :+ event)

    def tradeEventPosted(event: TradeFSM.PostedEvent) =
      this.copy(postedTradeEvents = postedTradeEvents :+ event)

    def postedEvents(since: Option[DateTime]) = since match {
      case Some(s: DateTime) =>
        PostedEvents(postedArbitratorEvents.filter(_.posted.get.isAfter(s)),
          postedTradeEvents.filter(_.posted.get.isAfter(s)))
      case None =>
        PostedEvents(postedArbitratorEvents, postedTradeEvents)
    }
  }

}

class ArbitratorServerManager(walletMgr: ActorRef) extends PersistentActor with ListenerUpdater with ArbitratorServerHttp {

  // implicits

  override implicit val system = context.system

  override implicit val materializer = ActorMaterializer()

  implicit val dispatcher = system.dispatcher

  implicit val timeout = Timeout(5 seconds) // needed for `?` below

  override val bindingFuture = binding(Config.localAddress, Config.localPort)

  // logging

  override val log = Logging(context.system, this)

  // persistence

  override def persistenceId: String = ArbitratorServerManager.persistenceId

  private var data = Data()

  // http server handlers

  override def getPostedEvents(since: Option[DateTime]) = data.postedEvents(since)

  override def postTradeEvent(te: TradeFSM.PostedEvent): Future[TradeFSM.PostedEvent] = {
    for {
      pte <- (self ask PostTradeEvent(te)).mapTo[TradeEventPosted]
    } yield pte.event
  }

  // apply events to data

  def applyEvent(event: ArbitratorServerManager.Event, data: Data): Data = event match {
    case ArbitratorEventPosted(ac: ArbitratorFSM.ArbitratorCreated) =>
      data.arbitratorEventPosted(ac).arbitratorCreated(ac.arbitrator)

    case ArbitratorEventPosted(ca: ArbitratorFSM.ContractAdded) if data.arbitrator.isDefined =>
      data.arbitratorEventPosted(ca).contractTemplateAdded(ca.contract)

    case ArbitratorEventPosted(ctr: ArbitratorFSM.ContractRemoved) =>
      data.arbitratorEventPosted(ctr).contractTemplateRemoved(ctr.id)

    case TradeEventPosted(te: TradeFSM.PostedEvent) =>
      data.tradeEventPosted(te)

    case e =>
      log.error(s"Unexpected event $e")
      data
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

    // handlers for manager commands
    case Start if data.arbitrator.isDefined =>
      self ! AddListener(context.sender())
      data.arbitrator.foreach(a => context.sender ! ArbitratorCreated(a.url, a))
      data.contract.foreach(c => context.sender ! ContractAdded(c.arbitrator.url, c))

    case Start if data.arbitrator.isEmpty =>
      self ! AddListener(context.sender())
      walletMgr ! WalletManager.CreateArbitrator(Config.publicUrl, Config.bondPercent, BTCMoney(Config.btcArbitratorFee))

    case WalletManager.ArbitratorCreated(a) =>
      val ac = ArbitratorFSM.ArbitratorCreated(a.url, a)
      persist(ArbitratorEventPosted(ac.copy(posted = Some(DateTime.now))))(updateData)
      sendToListeners(ac)

    case AddContractTemplate(cu, dm) =>
      // TODO FT-26: send back errors if arbitrator not initialized or contract already exists
      data.arbitrator.foreach { a =>
        val c = Contract(a, cu, dm)
        val ca = ContractAdded(a.url, c)
        persist(ArbitratorEventPosted(ca.copy(posted = Some(DateTime.now))))(updateData)
        sendToListeners(ca)
      }

    case RemoveContractTemplate(id) =>
      // TODO FT-26: send back errors if arbitrator not initialized or contract doesn't exists
      data.arbitrator.foreach { a =>
        val ctr = ContractRemoved(a.url, id)
        persist(ArbitratorEventPosted(ctr.copy(posted = Some(DateTime.now))))(updateData)
        sendToListeners(ctr)
      }

    // handle trade events

    case PostTradeEvent(evt: TradeFSM.SellerCreatedOffer) =>
      val tep = TradeEventPosted(evt.copy(posted = Some(DateTime.now())))
      persist(tep)(updateData)
      sender ! tep

    case PostTradeEvent(evt: TradeFSM.SellerCanceledOffer) =>
      val tep = TradeEventPosted(evt.copy(posted = Some(DateTime.now())))
      persist(tep)(updateData)
      sender ! tep

    case PostTradeEvent(evt: TradeFSM.BuyerTookOffer) =>
      val tep = TradeEventPosted(evt.copy(posted = Some(DateTime.now())))
      persist(tep)(updateData)
      sender ! tep

    case PostTradeEvent(evt: TradeFSM.SellerSignedOffer) =>
      val tep = TradeEventPosted(evt.copy(posted = Some(DateTime.now())))
      persist(tep)(updateData)
      sender ! tep

    case PostTradeEvent(evt: TradeFSM.CertifyDeliveryRequested) =>
      val tep = TradeEventPosted(evt.copy(posted = Some(DateTime.now())))
      persist(tep)(updateData)
      sender ! tep

    case PostTradeEvent(evt: TradeFSM.FiatSentCertified) =>
      val tep = TradeEventPosted(evt.copy(posted = Some(DateTime.now())))
      persist(tep)(updateData)
      sender ! tep

    case PostTradeEvent(evt: TradeFSM.FiatNotSentCertified) =>
      val tep = TradeEventPosted(evt.copy(posted = Some(DateTime.now())))
      persist(tep)(updateData)
      sender ! tep

    case "snap" => saveSnapshot(data)

    case "print" => println(data)
  }
}
