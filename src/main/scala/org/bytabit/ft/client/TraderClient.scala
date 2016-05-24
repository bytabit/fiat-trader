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

import akka.actor.{ActorRef, Props}
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.client.EventClient._
import org.bytabit.ft.trade.SellProcess.AddSellOffer
import org.bytabit.ft.trade.TradeProcess.SellerCreatedOffer
import org.bytabit.ft.trade.model.{BUYER, SELLER}
import org.bytabit.ft.trade.{BuyProcess, SellProcess, TradeProcess}

import scala.concurrent.duration._
import scala.language.postfixOps

object TraderClient {

  def props(url: URL, tradeWalletMgr: ActorRef, escrowWalletMgr: ActorRef) = Props(new TraderClient(url, tradeWalletMgr, escrowWalletMgr))

  def name(url: URL) = s"${TraderClient.getClass.getSimpleName}-${url.getHost}-${url.getPort}"
}

case class TraderClient(url: URL, tradeWalletMgr: ActorRef, escrowWalletMgr: ActorRef) extends EventClient {

  // persistence

  override def persistenceId = TraderClient.name(url)

  startWith(ADDED, AddedServer(url))

  when(ADDED, stateTimeout = 30 second) {

    case Event(Start | StateTimeout, d) =>
      reqPostedEvents(url, None)
      stay()

    // arbitrator was created and posted

    case Event(ReceivePostedArbitratorEvent(ac: ArbitratorManager.ArbitratorCreated), d) =>
      goto(ONLINE) applying ArbitratorAdded(url, ac.arbitrator, ac.posted) andThen { ud =>
        createArbitratorManager(ac.arbitrator) ! ac
        context.parent ! ServerOnline(ac.url)
      }

    // server online, do nothing until arbitrator created

    case Event(ServerOnline(_), d) =>
      stay()

    // server offline

    case Event(soff: ServerOffline, d) =>
      stay()
  }

  when(ONLINE, stateTimeout = 10 second) {

    // startup

    case Event(Start, ActiveServer(lp, a, at)) =>

      // create and start arbitrator
      createArbitratorManager(a) ! ArbitratorManager.Start

      // start active trades
      at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellProcess.Start))
      at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyProcess.Start))

      // request new events from event server
      reqPostedEvents(url, Some(lp))

      context.parent ! ServerOnline(a.url)
      stay()

    case Event(StateTimeout, ActiveServer(lp, a, at)) =>
      reqPostedEvents(url, Some(lp))
      stay()

    case Event(son: ServerOnline, d) =>
      stay()

    case Event(npe: NoPostedEventsReceived, d) =>
      stay()

    case Event(soff: ServerOffline, ActiveServer(lp, a, at)) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! soff
      }

    // send received posted arbitrator events to ArbitratorManager

    case Event(ReceivePostedArbitratorEvent(ae), ActiveServer(lp, a, at)) =>
      arbitratorManager(a) match {
        case Some(ref) => ref ! ae
        case None => log.error(s"Could not send event to arbitrator ${a.url}")
      }
      stay()

    // update latest posted time and send posted arbitrator events to parent

    case Event(ae: ArbitratorManager.PostedEvent, ActiveServer(lp, a, at)) =>
      stay() applying PostedEventReceived(a.url, ae.posted) andThen { ud =>
        context.parent ! ae
      }

    // send seller commands to trades

    case Event(AddSellOffer(u, i, o), d) =>
      createSellTrade(i, o) ! SellProcess.Start
      stay()

    case Event(sc: SellProcess.Command, d) =>
      tradeProcess(sc.id) match {
        case Some(ref) => ref ! sc
        case None => log.error(s"Could not send sell process command to ${sc.id}")
      }
      stay()


    // send buyer commands to trades

    case Event(sc: BuyProcess.Command, d) =>
      tradeProcess(sc.id) match {
        case Some(ref) => ref ! sc
        case None => log.error(s"Could not send buy process command to ${sc.id}")
      }
      stay()

    // handle posted trade events

    // send event to existing local seller trade or create remote buyer trade
    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), ActiveServer(lp, a, at)) =>
      tradeProcess(sco.id) match {
        case Some(ref) =>
          ref ! sco
        case None =>
          createBuyTrade(sco.id, sco.offer) ! sco
      }
      stay()

    // add local seller created trade and update latestUpdate
    case Event(sco: TradeProcess.LocalSellerCreatedOffer, ActiveServer(lp, a, at)) =>
      stay() applying TradeAdded(a.url, SELLER, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // add remote buyer created trade and update latestUpdate
    case Event(sco: TradeProcess.SellerCreatedOffer, ActiveServer(lp, a, at)) =>
      stay() applying TradeAdded(a.url, BUYER, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // remove trade and update latestUpdate
    case Event(sco: TradeProcess.SellerCanceledOffer, ActiveServer(lp, a, at)) =>
      stay() applying TradeRemoved(a.url, sco.id, sco.posted) andThen { ud =>
        context.parent ! sco
        stopTrade(sco.id)
      }

    // send received posted trade events to trades

    case Event(ReceivePostedTradeEvent(te), ActiveServer(lp, a, at)) =>
      tradeProcess(te.id) match {
        case Some(ref) => ref ! te
        case None => log.error(s"Could not send event to trade ${te.id}")
      }
      stay()

    // update latest posted time and send posted trade events to parent

    case Event(te: TradeProcess.PostedEvent, ActiveServer(lp, a, at)) =>
      stay() applying PostedEventReceived(a.url, te.posted) andThen { ud =>
        context.parent ! te
      }

    // send other non-posted events to parent
    case Event(te: TradeProcess.Event, ActiveServer(lp, a, at)) =>
      context.parent ! te
      stay()
  }

  when(OFFLINE, stateTimeout = 30 second) {

    case Event(Start, ActiveServer(lp, a, at)) =>

      // request new events from event server
      reqPostedEvents(url, Some(lp))

      context.parent ! ServerOffline(a.url)
      stay()

    case Event(StateTimeout, ActiveServer(lp, a, at)) =>
      reqPostedEvents(a.url, Some(lp))
      stay()

    case Event(ServerOnline(_), ActiveServer(lp, a, at)) =>
      goto(ONLINE) andThen { ud =>

        // create and start active trades
        // TODO FT-23: disable trade negotation buttons in trade UI when arbitrator is offline
        at.get(SELLER).foreach(_.foreach(t => createSellTrade(t._1, t._2.offer) ! SellProcess.Start))
        at.get(BUYER).foreach(_.foreach(t => createBuyTrade(t._1, t._2) ! BuyProcess.Start))

        // create and start arbitrator
        createArbitratorManager(a) ! ArbitratorManager.Start

        context.parent ! ServerOnline(a.url)
      }

    case Event(ServerOffline(_), _) =>
      stay()
  }

  initialize()

}
