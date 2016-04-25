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
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{StatusCodes, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.client.EventClient._
import org.bytabit.ft.trade.TradeProcess.SellerCreatedOffer
import org.bytabit.ft.trade.model.ARBITRATOR
import org.bytabit.ft.trade.{ArbitrateProcess, BuyProcess, SellProcess, TradeProcess}
import org.bytabit.ft.util.Config

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ArbitratorClient {

  def props(url: URL, walletMgr: ActorRef) = Props(new ArbitratorClient(url, walletMgr))

  def name(url: URL) = s"${ArbitratorClient.getClass.getSimpleName}-${url.getHost}-${url.getPort}"
}

case class ArbitratorClient(url: URL, walletMgr: ActorRef) extends EventClient {

  // persistence

  override def persistenceId = ArbitratorClient.name(url)

  startWith(ADDED, AddedServer(url))

  when(ADDED, stateTimeout = 30 second) {

    // startup

    case Event(Start | StateTimeout, d) =>
      reqPostedEvents(url, None)
      stay()

    // create and start arbitrator

    case Event(son: ServerOnline, d) =>
      createArbitratorManager(url) ! ArbitratorManager.Start
      stay()

    // arbitrator created

    case Event(ac: ArbitratorManager.ArbitratorCreated, d) =>
      goto(ONLINE) applying ArbitratorAdded(url, ac.arbitrator, ac.posted) andThen { ud =>
        context.parent ! ac
      }

    case Event(ReceivePostedArbitratorEvent(ac: ArbitratorManager.ArbitratorCreated), d) =>
      createArbitratorManager(url) ! ac
      goto(ONLINE) applying ArbitratorAdded(url, ac.arbitrator, ac.posted) andThen { ud =>
        context.parent ! ac
      }

    // server offline

    case Event(soff: ServerOffline, d) =>
      context.parent ! soff
      stay()
  }

  when(ONLINE, stateTimeout = 10 second) {

    // startup

    case Event(Start, ActiveServer(lp, a, at)) =>

      // create and start arbitrator
      createArbitratorManager(a.url) ! ArbitratorManager.Start

      // create and start active trades
      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

      // request new events from event server
      reqPostedEvents(url, Some(lp))
      stay()

    case Event(StateTimeout, ActiveServer(lp, a, at)) =>
      reqPostedEvents(url, Some(lp))
      stay()

    case Event(son: ServerOnline, d) =>
      stay()

    case Event(soff: ServerOffline, ActiveServer(lp, a, at)) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! soff
        stopArbitrator(a.url)
      }

    // send arbitrator commands to ArbitratorManager

    case Event(ac: ArbitratorManager.Command, ActiveServer(lp, a, at)) if isArbitrator(a.url) =>
      arbitratorManager(a.url) match {
        case Some(ref) => ref ! ac
        case None => log.error(s"Could not send command to arbitrator ${a.url}")
      }
      stay()

    // send received posted arbitrator events to ArbitratorManager

    case Event(ReceivePostedArbitratorEvent(ae), ActiveServer(lp, a, at)) =>
      arbitratorManager(a.url) match {
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

    case Event(ac: ArbitrateProcess.Command, d) if isArbitrator(ac.url) =>
      tradeProcess(ac.id) match {
        case Some(ref) => ref ! ac
        case None => log.error(s"Could not send arbitrate process command to trade ${ac.id}")
      }
      stay()

    case Event(sc: SellProcess.Command, d) if !isArbitrator(sc.url) =>
      tradeProcess(sc.id) match {
        case Some(ref) => ref ! sc
        case None => log.error(s"Could not send sell process command to trade ${sc.id}")
      }
      stay()

    case Event(bc: BuyProcess.Command, d) if !isArbitrator(bc.url) =>
      tradeProcess(bc.id) match {
        case Some(ref) => ref ! bc
        case None => log.error(s"Could not buy sell process command to trade ${bc.id}")
      }
      stay()

    // handle posted trade events

    // add trade and update latestUpdate
    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), ActiveServer(lp, a, at)) =>
      tradeProcess(sco.id) match {
        case Some(ref) =>
          ref ! sco
        case None =>
          createArbitrateTrade(sco.id, sco.offer) ! sco
      }
      stay()

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
  }

  when(OFFLINE, stateTimeout = 30 second) {

    case Event(Start, ActiveServer(lp, a, at)) =>

      // create and start active trades
      // TODO FT-23: disable trade negotation buttons in trade UI when arbitrator is offline
      at.get(ARBITRATOR).foreach(_.foreach(t => createArbitrateTrade(t._1, t._2) ! ArbitrateProcess.Start))

      // request new events from event server
      reqPostedEvents(url, Some(lp))

      context.parent ! ServerOffline(a.url)
      stay()

    case Event(StateTimeout, ActiveServer(lp, a, at)) =>
      reqPostedEvents(a.url, Some(lp))
      stay()

    case Event(ServerOnline(_), ActiveServer(lp, a, at)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! ServerOnline(a.url)
        // create and start arbitrator
        createArbitratorManager(a.url) ! ArbitratorManager.Start
      }

    case Event(ServerOnline(_), AddedServer(u)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! ServerOnline(u)
      }

    case Event(ServerOffline(_), _) =>
      stay()

    // send trade commands to trades

    case Event(ac: ArbitrateProcess.Command, d) if isArbitrator(ac.url) =>
      tradeProcess(ac.id) match {
        case Some(ref) => ref ! ac
        case None => log.error(s"Could not send arbitrate process command to trade ${ac.id}")
      }
      stay()

    case Event(sc: SellProcess.Command, d) if !isArbitrator(sc.url) =>
      tradeProcess(sc.id) match {
        case Some(ref) => ref ! sc
        case None => log.error(s"Could not send sell process command to trade ${sc.id}")
      }
      stay()

    case Event(bc: BuyProcess.Command, d) if !isArbitrator(bc.url) =>
      tradeProcess(bc.id) match {
        case Some(ref) => ref ! bc
        case None => log.error(s"Could not buy sell process command to trade ${bc.id}")
      }
      stay()

  }

  initialize()

  def isArbitrator(url: URL) = Config.arbitratorEnabled && Config.publicUrl == url

  import spray.json._

  def postTradeEvent(url: URL, postedEvent: TradeProcess.PostedEvent, self: ActorRef): Unit = {

    val tradeUri = s"/trade"

    Marshal(postedEvent.toJson).to[RequestEntity].onSuccess {

      case reqEntity =>

        val req = Source.single(HttpRequest(uri = tradeUri, method = HttpMethods.POST,
          entity = reqEntity.withContentType(ContentTypes.`application/json`)))
          .via(connectionFlow(url))

        req.runWith(Sink.head).onComplete {

          case Success(HttpResponse(StatusCodes.OK, headers, respEntity, protocol)) =>
            log.debug(s"Response from ${url.toString}$tradeUri OK, $respEntity")
            Unmarshal(respEntity).to[TradeProcess.PostedEvent].onSuccess {
              case pe: TradeProcess.PostedEvent if pe.posted.isDefined =>
                self ! pe
              case _ =>
                log.error("No posted event in response.")
            }

          case Success(HttpResponse(sc, h, e, p)) =>
            log.error(s"Response from ${url.toString}$tradeUri ${sc.toString()}")

          case Failure(failure) =>
            log.debug(s"No Response from ${url.toString}: $failure")
        }
    }
  }

}
