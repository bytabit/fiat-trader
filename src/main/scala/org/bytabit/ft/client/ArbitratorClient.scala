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
import org.bytabit.ft.trade.TradeProcess.BtcBuyerCreatedOffer
import org.bytabit.ft.trade.model.ARBITRATOR
import org.bytabit.ft.trade.{ArbitrateProcess, TradeProcess}
import org.bytabit.ft.util.{BTCMoney, Config}
import org.bytabit.ft.wallet.{TradeWalletManager, WalletManager}

import scala.concurrent.duration._
import scala.language.postfixOps

object ArbitratorClient {

  def props(url: URL, tradeWalletMgr: ActorRef, escrowWalletMgr: ActorRef) = Props(new ArbitratorClient(url, tradeWalletMgr, escrowWalletMgr))

  def name(url: URL) = s"${ArbitratorClient.getClass.getSimpleName}-${url.getHost}-${url.getPort}"
}

case class ArbitratorClient(url: URL, tradeWalletMgr: ActorRef, escrowWalletMgr: ActorRef) extends EventClient {

  // persistence

  override def persistenceId = s"${ArbitratorClient.name(url)}-persister"

  startWith(ADDED, AddedServer(url))

  when(ADDED, stateTimeout = 30 second) {

    // startup

    case Event(Start | StateTimeout, d) =>
      reqPostedEvents(url, None)
      stay()

    // create arbitrator

    case Event(npe: NoPostedEventsReceived, d) =>
      tradeWalletMgr ! TradeWalletManager.CreateArbitrator(Config.publicUrl, Config.bondPercent, BTCMoney(Config.btcArbitratorFee))
      stay()

    // new arbitrator created

    case Event(ac: WalletManager.ArbitratorCreated, d) =>
      createArbitratorManager(ac.arbitrator) ! ArbitratorManager.Start
      stay()

    case Event(ac: ArbitratorManager.ArbitratorCreated, d) =>
      goto(ONLINE) applying ArbitratorAdded(url, ac.arbitrator, ac.posted) andThen { ud =>
        context.parent ! ServerOnline(ac.url)
        context.parent ! ac
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

      // create and start active trades
      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

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

    // send arbitrator commands to ArbitratorManager

    case Event(ac: ArbitratorManager.Command, ActiveServer(lp, a, at)) =>
      arbitratorManager(a) match {
        case Some(ref) => ref ! ac
        case None => log.error(s"Could not send command to arbitrator ${a.url}")
      }
      stay()

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

    // send trade commands to trades

    case Event(ac: ArbitrateProcess.Command, d) =>
      tradeProcess(ac.id) match {
        case Some(ref) => ref ! ac
        case None => log.error(s"Could not send arbitrate process command to trade ${ac.id}")
      }
      stay()

    // handle posted trade events

    // create trade
    case Event(ReceivePostedTradeEvent(bco: BtcBuyerCreatedOffer), ActiveServer(lp, a, at)) =>
      tradeProcess(bco.id) match {
        case Some(ref) =>
          ref ! bco
        case None =>
          createArbitrateTrade(bco.id, bco.offer) ! bco
      }
      stay()

    // add trade and update latestUpdate
    case Event(sco: TradeProcess.BtcBuyerCreatedOffer, ActiveServer(lp, a, at)) =>
      stay() applying TradeAdded(a.url, ARBITRATOR, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // remove trade and update latestUpdate
    case Event(sco: TradeProcess.BtcBuyerCanceledOffer, ActiveServer(lp, a, at)) =>
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

//      // create and start arbitrator
//      createArbitratorManager(a) ! ArbitratorManager.Start
//
//      // create and start active trades
//      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

      // request new events from event server
      reqPostedEvents(url, Some(lp))

      context.parent ! ServerOffline(a.url)
      stay()

    case Event(StateTimeout, ActiveServer(lp, a, at)) =>
      reqPostedEvents(a.url, Some(lp))
      stay()

    case Event(ServerOnline(_), ActiveServer(lp, a, at)) =>
      goto(ONLINE) andThen { ud =>

        // create and start arbitrator
        createArbitratorManager(a) ! ArbitratorManager.Start

        // create and start active trades
        at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

        context.parent ! ServerOnline(a.url)
      }

    case Event(ServerOffline(_), _) =>
      stay()
  }

  initialize()

}
