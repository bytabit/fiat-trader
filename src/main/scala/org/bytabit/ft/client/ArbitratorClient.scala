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

import akka.actor.{ActorRef, Props}
import org.bytabit.ft.client.ClientFSM._
import org.bytabit.ft.fxui.model.TradeUIModel.{ARBITRATOR, BUYER, SELLER}
import org.bytabit.ft.trade.TradeFSM.SellerCreatedOffer
import org.bytabit.ft.trade.model.SellOffer
import org.bytabit.ft.trade.{ArbitrateProcess, BuyProcess, SellProcess, TradeFSM}

import scala.concurrent.duration._
import scala.language.postfixOps

object ArbitratorClient {

  def props(url: URL, walletMgr: ActorRef) = Props(new ArbitratorClient(url, walletMgr))

  def name(url: URL) = s"${ArbitratorClient.getClass.getSimpleName}-${url.getHost}-${url.getPort}"
}

case class ArbitratorClient(url: URL, walletMgr: ActorRef) extends ClientFSM {

  // persistence

  override def persistenceId = ArbitratorClient.name(url)

  startWith(ADDED, AddedServer(url))

  when(ADDED, stateTimeout = 30 second) {

    case Event(Start | StateTimeout, d) =>
      reqArbitratorEvents(url, None)
      stay()

    case Event(son: ServerOnline, d) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! son
      }

    case Event(soff: ServerOffline, d) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! soff
      }
  }

  when(ONLINE, stateTimeout = 10 second) {

    // handle server commands

    case Event(Start, ActiveServer(n, lp, cm, at)) =>
      // notify parent server is online
      context.parent ! ArbitratorCreated(n.url, n)
      context.parent ! ServerOnline(n.url)

      // notify parent of arbitrator contract
      cm.values.foreach(c => context.parent ! ContractAdded(c.arbitrator.url, c))

      // start active trade FSMs and notify parent
      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

      // request new events from event server
      reqArbitratorEvents(url, Some(lp))
      stay()

    case Event(StateTimeout, ActiveServer(n, lp, cm, at)) =>
      reqArbitratorEvents(url, Some(lp))
      stay()

    case Event(son: ServerOnline, d) =>
      stay()

    case Event(soff: ServerOffline, ActiveServer(n, lp, cm, at)) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! soff
        cm.keys.foreach(context.parent ! ContractRemoved(n.url, _))
      }

    // handle arbitrator events

    case Event(ReceivePostedArbitratorEvent(pe), _) =>
      stay applying pe andThen { ud =>
        context.parent ! pe
      }

    // handle trade events

    // add arbitrated remote trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCreatedOffer, ActiveServer(n, lp, cm, at)) if sco.posted.isDefined =>
      stay() applying ArbitrateTradeAdded(n.url, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // remove trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCanceledOffer, ActiveServer(n, lp, cm, at)) if sco.posted.isDefined =>
      stay() applying TradeRemoved(n.url, sco.id, sco.posted) andThen { ud =>
        context.parent ! sco
        stopTrade(sco.id)
      }

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, ActiveServer(n, lp, cm, at)) if te.posted.isDefined =>
      stay() applying PostedTradeEventReceived(n.url, te.posted) andThen { ud =>
        context.parent ! te
      }

    // forward all other trade events to parent
    case Event(te: TradeFSM.Event, _) =>
      context.parent ! te
      stay()

    // handle arbitrator commands

    case Event(cfs: ArbitrateProcess.CertifyFiatSent, d) =>
      tradeFSM(cfs.id) match {
        case Some(ref) => ref ! cfs
        case None => log.error(s"Could not arbitrate fiat sent ${cfs.id}")
      }
      stay()

    case Event(cfns: ArbitrateProcess.CertifyFiatNotSent, d) =>
      tradeFSM(cfns.id) match {
        case Some(ref) => ref ! cfns
        case None => log.error(s"Could not arbitrate fiat not sent ${cfns.id}")
      }
      stay()

    // forward all client events to parent

    case Event(ne: ClientFSM.Event, _) =>
      context.parent ! ne
      stay()

    // handle receive posted trade events

    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), d) =>
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

    case Event(Start, ActiveServer(n, lp, cm, at)) =>
      // notify parent arbitrator was created but server is offline
      context.parent ! ArbitratorCreated(n.url, n)
      context.parent ! ServerOffline(n.url)

      // TODO FT-23: disable trade negotation buttons in trade UI when arbitrator is offline
      // start active trade FSMs and notify parent
      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

      reqArbitratorEvents(n.url, Some(lp))
      stay()

    case Event(Start, AddedServer(u)) =>
      // notify parent server is offline, arbitrator not created
      context.parent ! ServerOffline(u)

      reqArbitratorEvents(u, None)
      stay()

    case Event(StateTimeout, ActiveServer(n, lp, cm, at)) =>
      reqArbitratorEvents(n.url, Some(lp))
      stay()

    case Event(StateTimeout, AddedServer(u)) =>
      reqArbitratorEvents(u, None)
      stay()

    case Event(ServerOnline(_), ActiveServer(n, lp, cm, at)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! ServerOnline(n.url)
        cm.values.foreach(c => context.parent ! ContractAdded(c.arbitrator.url, c))
      }

    case Event(ServerOnline(_), AddedServer(u)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! ServerOnline(u)
      }

    case Event(ServerOffline(_), _) =>
      stay()

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, ActiveServer(n, lp, cm, at)) if te.posted.isDefined =>
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

  def createArbitrateTrade(id: UUID, so: SellOffer): ActorRef = {
    context.actorOf(TradeFSM.arbitrateProps(so, walletMgr), TradeFSM.name(id))
  }

}
