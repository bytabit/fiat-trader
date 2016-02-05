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
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, NOTARY, SELLER}
import org.bytabit.ft.notary.NotaryClientFSM._
import org.bytabit.ft.notary.NotaryFSM._
import org.bytabit.ft.trade.BuyFSM.{ReceiveFiat, TakeSellOffer}
import org.bytabit.ft.trade.SellFSM.{AddSellOffer, CancelSellOffer}
import org.bytabit.ft.trade.TradeFSM.SellerCreatedOffer
import org.bytabit.ft.trade.model.{Offer, SellOffer}
import org.bytabit.ft.trade.{BuyFSM, NotarizeFSM, SellFSM, TradeFSM}
import org.bytabit.ft.util.Config

import scala.concurrent.duration._
import scala.language.postfixOps

object NotaryClientFSM {

  def actorOf(serverURL: URL, walletMgr: ActorRef)(implicit system: ActorSystem) =
    system.actorOf(props(serverURL, walletMgr), name(serverURL))

  // commands

  sealed trait Command

  case object Start extends Command

  final case class ReceivePostedNotaryEvent(event: NotaryFSM.PostedEvent) extends Command

  final case class ReceivePostedTradeEvent(event: TradeFSM.PostedEvent) extends Command

}

class NotaryClientFSM(serverUrl: URL, walletMgr: ActorRef) extends NotaryFSM {

  override val log = Logging(context.system, this)

  override val url: URL = serverUrl

  val isNotary = Config.serverEnabled

  startWith(ADDED, AddedNotary(url))

  when(ADDED, stateTimeout = 30 second) {

    case Event(Start | StateTimeout, d) =>
      reqNotaryEvents(url, None)
      stay()

    case Event(aon: NotaryOnline, d) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! aon
      }

    case Event(aoff: NotaryOffline, d) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! aoff
      }
  }

  when(ONLINE, stateTimeout = 10 second) {

    // handle notary commands

    case Event(Start, ActiveNotary(n, lp, cm, at)) =>
      // notify parent notary is online
      context.parent ! NotaryCreated(n.url, n)
      context.parent ! NotaryOnline(n.url)

      // notify parent of notary contract
      cm.values.foreach(c => context.parent ! ContractAdded(c.notary.url, c))

      // start active trade FSMs and notify parent
      at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellFSM.Start))
      at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyFSM.Start))
      at.get(NOTARY).foreach(_.foreach(t => createNotarizeTrade(t._1, t._2) ! NotarizeFSM.Start))

      // request new events from event server
      reqNotaryEvents(url, Some(lp))
      stay()

    case Event(StateTimeout, ActiveNotary(n, lp, cm, at)) =>
      reqNotaryEvents(url, Some(lp))
      stay()

    case Event(aon: NotaryOnline, d) =>
      stay()

    case Event(aoff: NotaryOffline, ActiveNotary(n, lp, cm, at)) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! aoff
        cm.keys.foreach(context.parent ! ContractRemoved(n.url, _))
      }

    // handle notary events

    case Event(ReceivePostedNotaryEvent(pe), _) =>
      stay applying pe andThen { ud =>
        context.parent ! pe
      }

    // handle trade commands

    case Event(AddSellOffer(o), d) if !isNotary =>
      createSellTrade(o.id, o) ! SellFSM.Start
      stay()

    case Event(cso: CancelSellOffer, d) if !isNotary =>
      tradeFSM(cso.id) match {
        case Some(ref) => ref ! cso
        case None => log.error(s"Could not cancel offer ${cso.id}")
      }
      stay()

    case Event(tso: TakeSellOffer, d) if !isNotary =>
      tradeFSM(tso.id) match {
        case Some(ref) => ref ! tso
        case None => log.error(s"Could not take offer ${tso.id}")
      }
      stay()

    case Event(fr: ReceiveFiat, d) if !isNotary =>
      tradeFSM(fr.id) match {
        case Some(ref) => ref ! fr
        case None => log.error(s"Could not receive fiat ${fr.id}")
      }
      stay()

    case Event(rcd: BuyFSM.RequestCertifyDelivery, d) if !isNotary =>
      tradeFSM(rcd.id) match {
        case Some(ref) => ref ! rcd
        case None => log.error(s"Could not request certify delivery ${rcd.id}")
      }
      stay()

    case Event(rcd: SellFSM.RequestCertifyDelivery, d) if !isNotary =>
      tradeFSM(rcd.id) match {
        case Some(ref) => ref ! rcd
        case None => log.error(s"Could not request certify delivery ${rcd.id}")
      }
      stay()

    // handle trade events

    // add local trade and update latestUpdate
    case Event(lsco: TradeFSM.LocalSellerCreatedOffer, ActiveNotary(n, lp, cm, at))
      if lsco.posted.isDefined && !isNotary =>
      stay() applying SellTradeAdded(n.url, lsco.id, lsco.offer, lsco.posted) andThen { ud =>
        context.parent ! lsco
      }

    // add remote trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCreatedOffer, ActiveNotary(n, lp, cm, at))
      if sco.posted.isDefined && !isNotary =>
      stay() applying BuyTradeAdded(n.url, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // add notarized remote trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCreatedOffer, ActiveNotary(n, lp, cm, at))
      if sco.posted.isDefined && isNotary =>
      stay() applying NotarizeTradeAdded(n.url, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // remove trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCanceledOffer, ActiveNotary(n, lp, cm, at)) if sco.posted.isDefined =>
      stay() applying TradeRemoved(n.url, sco.id, sco.posted) andThen { ud =>
        context.parent ! sco
        stopTrade(sco.id)
      }

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, ActiveNotary(n, lp, cm, at)) if te.posted.isDefined =>
      stay() applying PostedTradeEventReceived(n.url, te.posted) andThen { ud =>
        context.parent ! te
      }

    // forward all other trade events to parent
    case Event(te: TradeFSM.Event, _) =>
      context.parent ! te
      stay()

    // handle notary commands
    case Event(cfs: NotarizeFSM.CertifyFiatSent, d) if isNotary =>
      tradeFSM(cfs.id) match {
        case Some(ref) => ref ! cfs
        case None => log.error(s"Could not notarize fiat sent ${cfs.id}")
      }
      stay()

    case Event(cfns: NotarizeFSM.CertifyFiatNotSent, d) if isNotary =>
      tradeFSM(cfns.id) match {
        case Some(ref) => ref ! cfns
        case None => log.error(s"Could not notarize fiat not sent ${cfns.id}")
      }
      stay()

    // forward all other notary events to parent
    case Event(ne: NotaryFSM.Event, _) =>
      context.parent ! ne
      stay()

    // handle receive posted trade events

    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), d) if !isNotary =>
      tradeFSM(sco.id) match {
        case Some(ref) =>
          ref ! sco
        case None =>
          createBuyTrade(sco.id, sco.offer) ! sco
      }
      stay()

    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), d) if isNotary =>
      tradeFSM(sco.id) match {
        case Some(ref) =>
          ref ! sco
        case None =>
          createNotarizeTrade(sco.id, sco.offer) ! sco
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

    case Event(Start, ActiveNotary(n, lp, cm, at)) =>
      // notify parent notary was created but is offline
      context.parent ! NotaryCreated(n.url, n)
      context.parent ! NotaryOffline(n.url)

      // TODO issue #28, disable trade negotation buttons in trade UI when notary is offline
      // start active trade FSMs and notify parent
      at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellFSM.Start))
      at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyFSM.Start))
      at.get(NOTARY).foreach(_.foreach(t => createNotarizeTrade(t._1, t._2) ! NotarizeFSM.Start))

      reqNotaryEvents(n.url, Some(lp))
      stay()

    case Event(Start, AddedNotary(u)) =>
      // notify parent notary is offline, not yet contacted
      context.parent ! NotaryOffline(u)

      reqNotaryEvents(u, None)
      stay()

    case Event(StateTimeout, ActiveNotary(n, lp, cm, at)) =>
      reqNotaryEvents(n.url, Some(lp))
      stay()

    case Event(StateTimeout, AddedNotary(u)) =>
      reqNotaryEvents(u, None)
      stay()

    case Event(NotaryOnline(_), ActiveNotary(n, lp, cm, at)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! NotaryOnline(n.url)
        cm.values.foreach(c => context.parent ! ContractAdded(c.notary.url, c))
      }

    case Event(NotaryOnline(_), AddedNotary(u)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! NotaryOnline(u)
      }

    case Event(NotaryOffline(_), _) =>
      stay()

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, ActiveNotary(n, lp, cm, at)) if te.posted.isDefined =>
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

  def createNotarizeTrade(id: UUID, so: SellOffer): ActorRef = {
    context.actorOf(TradeFSM.notarizeProps(so, walletMgr), TradeFSM.name(id))
  }

}
