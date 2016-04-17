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
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import org.bytabit.ft.client.ArbitratorClient._
import org.bytabit.ft.client.ClientFSM._
import org.bytabit.ft.fxui.model.TradeUIModel.{ARBITRATOR, BUYER, SELLER}
import org.bytabit.ft.trade.BuyProcess.{ReceiveFiat, TakeSellOffer}
import org.bytabit.ft.trade.SellProcess.{AddSellOffer, CancelSellOffer, SendFiat}
import org.bytabit.ft.trade.TradeFSM.SellerCreatedOffer
import org.bytabit.ft.trade.model.{Offer, SellOffer}
import org.bytabit.ft.trade.{ArbitrateProcess, BuyProcess, SellProcess, TradeFSM}
import org.bytabit.ft.util.Config

import scala.concurrent.duration._
import scala.language.postfixOps

object ArbitratorClient {

  def actorOf(serverURL: URL, walletMgr: ActorRef)(implicit system: ActorSystem) =
    system.actorOf(props(serverURL, walletMgr), name(serverURL))

  // commands

  sealed trait Command

  case object Start extends Command

  final case class ReceivePostedArbitratorEvent(event: ClientFSM.PostedEvent) extends Command

  final case class ReceivePostedTradeEvent(event: TradeFSM.PostedEvent) extends Command

}

class ArbitratorClient(serverUrl: URL, walletMgr: ActorRef) extends ClientFSM {

  override val log = Logging(context.system, this)

  override val url: URL = serverUrl

  val isArbitrator = Config.serverEnabled

  startWith(ADDED, AddedArbitrator(url))

  when(ADDED, stateTimeout = 30 second) {

    case Event(Start | StateTimeout, d) =>
      reqArbitratorEvents(url, None)
      stay()

    case Event(aon: ArbitratorOnline, d) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! aon
      }

    case Event(aoff: ArbitratorOffline, d) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! aoff
      }
  }

  when(ONLINE, stateTimeout = 10 second) {

    // handle arbitrator commands

    case Event(Start, ActiveArbitrator(n, lp, cm, at)) =>
      // notify parent arbitrator is online
      context.parent ! ArbitratorCreated(n.url, n)
      context.parent ! ArbitratorOnline(n.url)

      // notify parent of arbitrator contract
      cm.values.foreach(c => context.parent ! ContractAdded(c.arbitrator.url, c))

      // start active trade FSMs and notify parent
      at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellProcess.Start))
      at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyProcess.Start))
      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

      // request new events from event server
      reqArbitratorEvents(url, Some(lp))
      stay()

    case Event(StateTimeout, ActiveArbitrator(n, lp, cm, at)) =>
      reqArbitratorEvents(url, Some(lp))
      stay()

    case Event(aon: ArbitratorOnline, d) =>
      stay()

    case Event(aoff: ArbitratorOffline, ActiveArbitrator(n, lp, cm, at)) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! aoff
        cm.keys.foreach(context.parent ! ContractRemoved(n.url, _))
      }

    // handle arbitrator events

    case Event(ReceivePostedArbitratorEvent(pe), _) =>
      stay applying pe andThen { ud =>
        context.parent ! pe
      }

    // handle trade commands

    case Event(AddSellOffer(o), d) if !isArbitrator =>
      createSellTrade(o.id, o) ! SellProcess.Start
      stay()

    case Event(cso: CancelSellOffer, d) if !isArbitrator =>
      tradeFSM(cso.id) match {
        case Some(ref) => ref ! cso
        case None => log.error(s"Could not cancel offer ${cso.id}")
      }
      stay()

    case Event(tso: TakeSellOffer, d) if !isArbitrator =>
      tradeFSM(tso.id) match {
        case Some(ref) => ref ! tso
        case None => log.error(s"Could not take offer ${tso.id}")
      }
      stay()

    case Event(rf: ReceiveFiat, d) if !isArbitrator =>
      tradeFSM(rf.id) match {
        case Some(ref) => ref ! rf
        case None => log.error(s"Could not receive fiat ${rf.id}")
      }
      stay()

    case Event(sf: SendFiat, d) if !isArbitrator =>
      tradeFSM(sf.id) match {
        case Some(ref) => ref ! sf
        case None => log.error(s"Could not send fiat ${sf.id}")
      }
      stay()

    case Event(rcd: BuyProcess.RequestCertifyDelivery, d) if !isArbitrator =>
      tradeFSM(rcd.id) match {
        case Some(ref) => ref ! rcd
        case None => log.error(s"Could not request certify delivery ${rcd.id}")
      }
      stay()

    case Event(rcd: SellProcess.RequestCertifyDelivery, d) if !isArbitrator =>
      tradeFSM(rcd.id) match {
        case Some(ref) => ref ! rcd
        case None => log.error(s"Could not request certify delivery ${rcd.id}")
      }
      stay()

    // handle trade events

    // add local trade and update latestUpdate
    case Event(lsco: TradeFSM.LocalSellerCreatedOffer, ActiveArbitrator(n, lp, cm, at))
      if lsco.posted.isDefined && !isArbitrator =>
      stay() applying SellTradeAdded(n.url, lsco.id, lsco.offer, lsco.posted) andThen { ud =>
        context.parent ! lsco
      }

    // add remote trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCreatedOffer, ActiveArbitrator(n, lp, cm, at))
      if sco.posted.isDefined && !isArbitrator =>
      stay() applying BuyTradeAdded(n.url, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // add arbitrated remote trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCreatedOffer, ActiveArbitrator(n, lp, cm, at))
      if sco.posted.isDefined && isArbitrator =>
      stay() applying ArbitrateTradeAdded(n.url, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // remove trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCanceledOffer, ActiveArbitrator(n, lp, cm, at)) if sco.posted.isDefined =>
      stay() applying TradeRemoved(n.url, sco.id, sco.posted) andThen { ud =>
        context.parent ! sco
        stopTrade(sco.id)
      }

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, ActiveArbitrator(n, lp, cm, at)) if te.posted.isDefined =>
      stay() applying PostedTradeEventReceived(n.url, te.posted) andThen { ud =>
        context.parent ! te
      }

    // forward all other trade events to parent
    case Event(te: TradeFSM.Event, _) =>
      context.parent ! te
      stay()

    // handle arbitrator commands
    case Event(cfs: ArbitrateProcess.CertifyFiatSent, d) if isArbitrator =>
      tradeFSM(cfs.id) match {
        case Some(ref) => ref ! cfs
        case None => log.error(s"Could not arbitrate fiat sent ${cfs.id}")
      }
      stay()

    case Event(cfns: ArbitrateProcess.CertifyFiatNotSent, d) if isArbitrator =>
      tradeFSM(cfns.id) match {
        case Some(ref) => ref ! cfns
        case None => log.error(s"Could not arbitrate fiat not sent ${cfns.id}")
      }
      stay()

    // forward all other arbitrator events to parent
    case Event(ne: ClientFSM.Event, _) =>
      context.parent ! ne
      stay()

    // handle receive posted trade events

    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), d) if !isArbitrator =>
      tradeFSM(sco.id) match {
        case Some(ref) =>
          ref ! sco
        case None =>
          createBuyTrade(sco.id, sco.offer) ! sco
      }
      stay()

    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), d) if isArbitrator =>
      tradeFSM(sco.id) match {
        case Some(ref) =>
          ref ! sco
        case None =>
          createArbitrateTrade(sco.id, sco.offer) ! sco
      }
      stay()

    case Event(ReceivePostedTradeEvent(pe), d) =>
      tradeFSM(pe.id) match {
        case Some(ref) =>
          ref ! pe
        case None =>
          log.error(s"No tradeFSM found for ${pe.id}")
      }
      stay()
  }

  when(OFFLINE, stateTimeout = 30 second) {

    case Event(Start, ActiveArbitrator(n, lp, cm, at)) =>
      // notify parent arbitrator was created but is offline
      context.parent ! ArbitratorCreated(n.url, n)
      context.parent ! ArbitratorOffline(n.url)

      // TODO FT-23: disable trade negotation buttons in trade UI when arbitrator is offline
      // start active trade FSMs and notify parent
      at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellProcess.Start))
      at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyProcess.Start))
      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

      reqArbitratorEvents(n.url, Some(lp))
      stay()

    case Event(Start, AddedArbitrator(u)) =>
      // notify parent arbitrator is offline, not yet contacted
      context.parent ! ArbitratorOffline(u)

      reqArbitratorEvents(u, None)
      stay()

    case Event(StateTimeout, ActiveArbitrator(n, lp, cm, at)) =>
      reqArbitratorEvents(n.url, Some(lp))
      stay()

    case Event(StateTimeout, AddedArbitrator(u)) =>
      reqArbitratorEvents(u, None)
      stay()

    case Event(ArbitratorOnline(_), ActiveArbitrator(n, lp, cm, at)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! ArbitratorOnline(n.url)
        cm.values.foreach(c => context.parent ! ContractAdded(c.arbitrator.url, c))
      }

    case Event(ArbitratorOnline(_), AddedArbitrator(u)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! ArbitratorOnline(u)
      }

    case Event(ArbitratorOffline(_), _) =>
      stay()

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, ActiveArbitrator(n, lp, cm, at)) if te.posted.isDefined =>
      stay() applying PostedTradeEventReceived(n.url, te.posted) andThen { ud =>
        context.parent ! te
      }

    // forward all other trade events to parent
    case Event(te: TradeFSM.Event, _) =>
      context.parent ! te
      stay()

  }

  initialize()

  // create trade FSMs

  def createSellTrade(id: UUID, o: Offer): ActorRef = {
    context.actorOf(TradeFSM.sellProps(o, walletMgr), TradeFSM.name(id))
  }

  def createBuyTrade(id: UUID, so: SellOffer): ActorRef = {
    context.actorOf(TradeFSM.buyProps(so, walletMgr), TradeFSM.name(id))
  }

  def createArbitrateTrade(id: UUID, so: SellOffer): ActorRef = {
    context.actorOf(TradeFSM.arbitrateProps(so, walletMgr), TradeFSM.name(id))
  }

}
