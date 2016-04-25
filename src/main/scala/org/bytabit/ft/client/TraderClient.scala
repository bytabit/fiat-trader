///*
// * Copyright 2016 Steven Myers
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.bytabit.ft.client
//
//import java.net.URL
//import java.util.UUID
//
//import akka.actor.{ActorRef, Props}
//import org.bytabit.ft.client.EventClient._
//import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, SELLER}
//import org.bytabit.ft.trade.BuyProcess.{ReceiveFiat, TakeSellOffer}
//import org.bytabit.ft.trade.SellProcess.{AddSellOffer, CancelSellOffer, SendFiat}
//import org.bytabit.ft.trade.TradeProcess.SellerCreatedOffer
//import org.bytabit.ft.trade.model.{Offer, SellOffer}
//import org.bytabit.ft.trade.{BuyProcess, SellProcess, TradeProcess}
//
//import scala.concurrent.duration._
//import scala.language.postfixOps
//
//object TraderClient {
//
//  def props(url: URL, walletMgr: ActorRef) = Props(new TraderClient(url, walletMgr))
//
//  def name(url: URL) = s"${TraderClient.getClass.getSimpleName}-${url.getHost}-${url.getPort}"
//}
//
//case class TraderClient(url: URL, walletMgr: ActorRef) extends EventClient {
//
//  // persistence
//
//  override def persistenceId = TraderClient.name(url)
//
//  startWith(ADDED, AddedServer(url))
//
//  when(ADDED, stateTimeout = 30 second) {
//
//    case Event(Start | StateTimeout, d) =>
//      reqArbitratorEvents(url, None)
//      stay()
//
//    case Event(son: ServerOnline, d) =>
//      goto(ONLINE) andThen { ud =>
//        context.parent ! son
//      }
//
//    case Event(soff: ServerOffline, d) =>
//      goto(OFFLINE) andThen { ud =>
//        context.parent ! soff
//      }
//  }
//
//  when(ONLINE, stateTimeout = 10 second) {
//
//    // handle server commands
//
//    case Event(Start, ActiveServer(n, lp, cm, at)) =>
//      // notify parent server is online
//      context.parent ! ArbitratorCreated(n.url, n)
//      context.parent ! ServerOnline(n.url)
//
//      // notify parent of arbitrator contract
//      cm.values.foreach(c => context.parent ! ContractAdded(c.arbitrator.url, c))
//
//      // start active trade FSMs and notify parent
//      at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellProcess.Start))
//      at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyProcess.Start))
//
//      // request new events from event server
//      reqArbitratorEvents(url, Some(lp))
//      stay()
//
//    case Event(StateTimeout, ActiveServer(n, lp, cm, at)) =>
//      reqArbitratorEvents(url, Some(lp))
//      stay()
//
//    case Event(son: ServerOnline, d) =>
//      stay()
//
//    case Event(soff: ServerOffline, ActiveServer(n, lp, cm, at)) =>
//      goto(OFFLINE) andThen { ud =>
//        context.parent ! soff
//        cm.keys.foreach(context.parent ! ContractRemoved(n.url, _))
//      }
//
//    // handle arbitrator events
//
//    case Event(ReceivePostedArbitratorEvent(pe), _) =>
//      stay applying pe andThen { ud =>
//        context.parent ! pe
//      }
//
//    // handle trade commands
//
//    case Event(AddSellOffer(o), d) =>
//      createSellTrade(o.id, o) ! SellProcess.Start
//      stay()
//
//    case Event(cso: CancelSellOffer, d) =>
//      tradeProcess(cso.id) match {
//        case Some(ref) => ref ! cso
//        case None => log.error(s"Could not cancel offer ${cso.id}")
//      }
//      stay()
//
//    case Event(tso: TakeSellOffer, d) =>
//      tradeProcess(tso.id) match {
//        case Some(ref) => ref ! tso
//        case None => log.error(s"Could not take offer ${tso.id}")
//      }
//      stay()
//
//    case Event(rf: ReceiveFiat, d) =>
//      tradeProcess(rf.id) match {
//        case Some(ref) => ref ! rf
//        case None => log.error(s"Could not receive fiat ${rf.id}")
//      }
//      stay()
//
//    case Event(sf: SendFiat, d) =>
//      tradeProcess(sf.id) match {
//        case Some(ref) => ref ! sf
//        case None => log.error(s"Could not send fiat ${sf.id}")
//      }
//      stay()
//
//    case Event(rcd: BuyProcess.RequestCertifyDelivery, d) =>
//      tradeProcess(rcd.id) match {
//        case Some(ref) => ref ! rcd
//        case None => log.error(s"Could not request certify delivery ${rcd.id}")
//      }
//      stay()
//
//    case Event(rcd: SellProcess.RequestCertifyDelivery, d) =>
//      tradeProcess(rcd.id) match {
//        case Some(ref) => ref ! rcd
//        case None => log.error(s"Could not request certify delivery ${rcd.id}")
//      }
//      stay()
//
//    // handle trade events
//
//    // add local trade and update latestUpdate
//    case Event(lsco: TradeProcess.LocalSellerCreatedOffer, ActiveServer(n, lp, cm, at)) if lsco.posted.isDefined =>
//      stay() applying SellTradeAdded(n.url, lsco.id, lsco.offer, lsco.posted) andThen { ud =>
//        context.parent ! lsco
//      }
//
//    // add remote trade and update latestUpdate
//    case Event(sco: TradeProcess.SellerCreatedOffer, ActiveServer(n, lp, cm, at)) if sco.posted.isDefined =>
//      stay() applying BuyTradeAdded(n.url, sco.id, sco.offer, sco.posted) andThen { ud =>
//        context.parent ! sco
//      }
//
//    // add arbitrated remote trade and update latestUpdate
//    case Event(sco: TradeProcess.SellerCreatedOffer, ActiveServer(n, lp, cm, at)) if sco.posted.isDefined =>
//      stay() applying ArbitrateTradeAdded(n.url, sco.id, sco.offer, sco.posted) andThen { ud =>
//        context.parent ! sco
//      }
//
//    // remove trade and update latestUpdate
//    case Event(sco: TradeProcess.SellerCanceledOffer, ActiveServer(n, lp, cm, at)) if sco.posted.isDefined =>
//      stay() applying TradeRemoved(n.url, sco.id, sco.posted) andThen { ud =>
//        context.parent ! sco
//        stopTrade(sco.id)
//      }
//
//    // update latestUpdate for other posted events
//    case Event(te: TradeProcess.PostedEvent, ActiveServer(n, lp, cm, at)) if te.posted.isDefined =>
//      stay() applying PostedEventReceived(n.url, te.posted) andThen { ud =>
//        context.parent ! te
//      }
//
//    // forward all other trade events to parent
//
//    case Event(te: TradeProcess.Event, _) =>
//      context.parent ! te
//      stay()
//
//    // forward all client events to parent
//
//    case Event(ne: EventClient.Event, _) =>
//      context.parent ! ne
//      stay()
//
//    // handle receive posted trade events
//
//    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), d) =>
//      tradeProcess(sco.id) match {
//        case Some(ref) =>
//          ref ! sco
//        case None =>
//          createBuyTrade(sco.id, sco.offer) ! sco
//      }
//      stay()
//
//    case Event(ReceivePostedTradeEvent(pe), d) =>
//      tradeProcess(pe.id) match {
//        case Some(ref) =>
//          ref ! pe
//        case None =>
//          log.error(s"No tradeFSM found for ${pe.id}")
//      }
//      stay()
//  }
//
//  when(OFFLINE, stateTimeout = 30 second) {
//
//    case Event(Start, ActiveServer(n, lp, cm, at)) =>
//      // notify parent arbitrator was created but server is offline
//      context.parent ! ArbitratorCreated(n.url, n)
//      context.parent ! ServerOffline(n.url)
//
//      // TODO FT-23: disable trade negotation buttons in trade UI when arbitrator is offline
//      // start active trade FSMs and notify parent
//      at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellProcess.Start))
//      at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyProcess.Start))
//
//      reqArbitratorEvents(n.url, Some(lp))
//      stay()
//
//    case Event(Start, AddedServer(u)) =>
//      // notify parent arbitrator is offline, not yet contacted
//      context.parent ! ServerOffline(u)
//
//      reqArbitratorEvents(u, None)
//      stay()
//
//    case Event(StateTimeout, ActiveServer(n, lp, cm, at)) =>
//      reqArbitratorEvents(n.url, Some(lp))
//      stay()
//
//    case Event(StateTimeout, AddedServer(u)) =>
//      reqArbitratorEvents(u, None)
//      stay()
//
//    case Event(ServerOnline(_), ActiveServer(n, lp, cm, at)) =>
//      goto(ONLINE) andThen { ud =>
//        context.parent ! ServerOnline(n.url)
//        cm.values.foreach(c => context.parent ! ContractAdded(c.arbitrator.url, c))
//      }
//
//    case Event(ServerOnline(_), AddedServer(u)) =>
//      goto(ONLINE) andThen { ud =>
//        context.parent ! ServerOnline(u)
//      }
//
//    case Event(ServerOffline(_), _) =>
//      stay()
//
//    // update latestUpdate for other posted events
//    case Event(te: TradeProcess.PostedEvent, ActiveServer(n, lp, cm, at)) if te.posted.isDefined =>
//      stay() applying PostedEventReceived(n.url, te.posted) andThen { ud =>
//        context.parent ! te
//      }
//
//    // forward all other trade events to parent
//    case Event(te: TradeProcess.Event, _) =>
//      context.parent ! te
//      stay()
//  }
//
//  initialize()
//
//}
