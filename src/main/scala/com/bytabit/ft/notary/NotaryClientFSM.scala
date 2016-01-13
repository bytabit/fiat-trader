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

package com.bytabit.ft.notary

import java.net.URL
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.bytabit.ft.notary.NotaryClientFSM._
import com.bytabit.ft.trade.BuyFSM.{ReceiveFiat, TakeSellOffer}
import com.bytabit.ft.trade.SellFSM.CancelSellOffer
import com.bytabit.ft.trade.TradeFSM.SellerCreatedOffer
import com.bytabit.ft.trade.model.{Contract, Offer, SellOffer}
import com.bytabit.ft.trade.{BuyFSM, SellFSM, TradeFSM}
import com.bytabit.ft.util.{DateTimeOrdering, Posted}
import com.bytabit.ft.wallet.model.Notary
import org.bitcoinj.core.Sha256Hash
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect._
import scala.util.{Failure, Success}

object NotaryClientFSM {

  // actor setup

  def props(url: URL, walletMgr: ActorRef) = Props(new NotaryClientFSM(url, walletMgr))

  def name(url: URL) = s"${NotaryClientFSM.getClass.getSimpleName}-${url.getHost}-${url.getPort}"

  def actorOf(url: URL, walletMgr: ActorRef)(implicit system: ActorSystem) =
    system.actorOf(props(url, walletMgr), name(url))

  // commands

  sealed trait Command

  case object Start extends Command

  final case class ReceivePostedNotaryEvent(event: NotaryClientFSM.PostedEvent) extends Command

  final case class AddSellOffer(offer: Offer) extends Command

  final case class ReceivePostedTradeEvent(event: TradeFSM.PostedEvent) extends Command

  // events

  sealed trait Event {
    val url: URL
  }

  sealed trait PostedEvent extends Event with Posted

  // notary events

  final case class NotaryCreated(url: URL, notary: Notary,
                                 posted: Option[DateTime] = None) extends PostedEvent

  final case class ContractAdded(url: URL, contract: Contract,
                                 posted: Option[DateTime] = None) extends PostedEvent

  final case class ContractRemoved(url: URL, id: Sha256Hash,
                                   posted: Option[DateTime] = None) extends PostedEvent

  final case class NotaryOnline(url: URL) extends Event

  final case class NotaryOffline(url: URL) extends Event

  // trade events

  final case class SellTradeAdded(url: URL, tradeId: UUID, offer: SellOffer,
                                  posted: Option[DateTime] = None) extends Event

  final case class BuyTradeAdded(url: URL, tradeId: UUID, offer: SellOffer,
                                 posted: Option[DateTime] = None) extends Event

  final case class TradeRemoved(url: URL, tradeId: UUID,
                                posted: Option[DateTime]) extends Event

  final case class PostedTradeEventReceived(url: URL,
                                            posted: Option[DateTime]) extends PostedEvent

  // states

  trait State extends FSMState

  case object ADDED extends State {
    override def identifier: String = "ADDED"
  }

  case object ONLINE extends State {
    override def identifier: String = "ONLINE"
  }

  case object OFFLINE extends State {
    override def identifier: String = "OFFLINE"
  }

  // data

  case class Data(url: URL, notary: Option[Notary] = None,
                  contracts: Map[Sha256Hash, Contract] = Map(),
                  activeSellTrades: Map[UUID, SellOffer] = Map(),
                  activeBuyTrades: Map[UUID, SellOffer] = Map(),
                  latestPosted: Option[DateTime] = None) {

    def postedEventReceived(dateTime: DateTime): Data =
      this.copy(latestPosted = Seq(Some(dateTime), latestPosted).flatten.reduceOption(DateTimeOrdering.max))

    def notaryCreated(notary: Notary, posted: DateTime) =
      this.copy(notary = Some(notary))
        .postedEventReceived(posted)

    def contractAdded(contract: Contract, posted: DateTime) =
      this.copy(contracts = contracts + (contract.id -> contract))
        .postedEventReceived(posted)

    def contractRemoved(id: Sha256Hash, posted: DateTime) =
      this.copy(contracts = contracts - id)
        .postedEventReceived(posted)

    def sellTradeAdded(id: UUID, offer: SellOffer, posted: DateTime) =
      this.copy(activeSellTrades = activeSellTrades + (id -> offer))
        .postedEventReceived(posted)

    def buyTradeAdded(id: UUID, offer: SellOffer, posted: DateTime) =
      this.copy(activeBuyTrades = activeBuyTrades + (id -> offer))
        .postedEventReceived(posted)

    def tradeRemoved(id: UUID, posted: DateTime) =
      this.copy(activeSellTrades = activeSellTrades - id, activeBuyTrades = activeBuyTrades - id)
        .postedEventReceived(posted)
  }

}

class NotaryClientFSM(url: URL, walletMgr: ActorRef)
  extends PersistentFSM[NotaryClientFSM.State, NotaryClientFSM.Data, NotaryClientFSM.Event] with NotaryClientFSMJsonProtocol {

  // implicits

  implicit val system = context.system

  implicit def executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  override def domainEventClassTag: ClassTag[NotaryClientFSM.Event] = classTag[NotaryClientFSM.Event]

  // logging

  override val log = Logging(context.system, this)

  // persistence

  override def persistenceId = NotaryClientFSM.name(url)

  // apply events to state and data

  def applyEvent(evt: NotaryClientFSM.Event, data: NotaryClientFSM.Data): NotaryClientFSM.Data = evt match {

    case NotaryCreated(u, a, Some(p)) =>
      data.notaryCreated(a, p)

    case ContractAdded(u, c, Some(p)) =>
      data.contractAdded(c, p)

    case ContractRemoved(u, id, Some(p)) =>
      data.contractRemoved(id, p)

    case SellTradeAdded(u, i, o, Some(p)) =>
      data.sellTradeAdded(i, o, p)

    case BuyTradeAdded(u, i, o, Some(p)) =>
      data.buyTradeAdded(i, o, p)

    case TradeRemoved(u, i, Some(p)) =>
      data.tradeRemoved(i, p)

    case PostedTradeEventReceived(u, Some(p)) =>
      data.postedEventReceived(p)

    case _ => data
  }

  startWith(ADDED, Data(url))

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

    case Event(Start, Data(u, Some(a), cm, as, ab, lp)) =>
      // notify parent notary is online
      context.parent ! NotaryCreated(a.url, a)
      context.parent ! NotaryOnline(u)

      // notify parent of notary contract
      cm.values.foreach(c => context.parent ! ContractAdded(c.notary.url, c))

      // start active trade FSMs and notify parent
      as.foreach(t => createSellTrade(t._1, t._2.offer) ! SellFSM.Start)
      ab.foreach(t => createBuyTrade(t._1, t._2) ! BuyFSM.Start)

      // request new events from event server
      reqNotaryEvents(url, lp)
      stay()

    case Event(StateTimeout, d) =>
      reqNotaryEvents(url, d.latestPosted)
      stay()

    case Event(aon: NotaryOnline, d) =>
      stay()

    case Event(aoff: NotaryOffline, d) =>
      goto(OFFLINE) andThen { ud =>
        context.parent ! aoff
        // TODO disable contracts in UI when notary is offline
        d.contracts.keys.foreach(context.parent ! ContractRemoved(d.url, _))
      }

    // handle notary events

    case Event(ReceivePostedNotaryEvent(pe), _) =>
      stay applying pe andThen { ud =>
        context.parent ! pe
      }

    // handle trade commands

    case Event(AddSellOffer(o), d) =>
      createSellTrade(o.id, o) ! SellFSM.Start
      stay()

    case Event(cso: CancelSellOffer, d) =>
      tradeFSM(cso.id) match {
        case Some(ref) => ref ! cso
        case None => log.error(s"Could not cancel offer ${cso.id}")
      }
      stay()

    case Event(tso: TakeSellOffer, d) =>
      tradeFSM(tso.id) match {
        case Some(ref) => ref ! tso
        case None => log.error(s"Could not take offer ${tso.id}")
      }
      stay()

    case Event(fr: ReceiveFiat, d) =>
      tradeFSM(fr.id) match {
        case Some(ref) => ref ! fr
        case None => log.error(s"Could not receive fiat ${fr.id}")
      }
      stay()

    // handle trade events

    // add local trade and update latestUpdate
    case Event(lsco: TradeFSM.LocalSellerCreatedOffer, d) if lsco.posted.isDefined =>
      stay() applying SellTradeAdded(d.url, lsco.id, lsco.offer, lsco.posted) andThen { ud =>
        context.parent ! lsco
      }

    // add remote trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCreatedOffer, d) if sco.posted.isDefined =>
      stay() applying BuyTradeAdded(d.url, sco.id, sco.offer, sco.posted) andThen { ud =>
        context.parent ! sco
      }

    // remove trade and update latestUpdate
    case Event(sco: TradeFSM.SellerCanceledOffer, d) if sco.posted.isDefined =>
      stay() applying TradeRemoved(d.url, sco.id, sco.posted) andThen { ud =>
        context.parent ! sco
        stopTrade(sco.id)
      }

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, d) if te.posted.isDefined =>
      stay() applying PostedTradeEventReceived(d.url, te.posted) andThen { ud =>
        context.parent ! te
      }

    // forward all other trade events to parent
    case Event(te: TradeFSM.Event, _) =>
      context.parent ! te
      stay()

    // handle receive posted trade events

    case Event(ReceivePostedTradeEvent(sco: SellerCreatedOffer), d) =>
      tradeFSM(sco.id) match {
        case Some(ref) =>
          ref ! sco
        case None =>
          createBuyTrade(sco.id, sco.offer) ! sco
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

    case Event(Start, Data(u, Some(a), cm, as, ab, lp)) =>
      // notify parent notary was created but is offline
      context.parent ! NotaryCreated(a.url, a)
      context.parent ! NotaryOffline(a.url)

      // TODO disable action buttons in trade UI when notary is offline
      // start active trade FSMs and notify parent
      as.foreach(t => createSellTrade(t._1, t._2.offer) ! SellFSM.Start)
      ab.foreach(t => createBuyTrade(t._1, t._2) ! BuyFSM.Start)

      reqNotaryEvents(url, lp)
      stay()

    case Event(Start, Data(u, None, _, _, _, None)) =>
      // notify parent notary is offline, not yet contacted
      context.parent ! NotaryOffline(url)

      reqNotaryEvents(url, None)
      stay()

    case Event(StateTimeout, d) =>
      reqNotaryEvents(url, d.latestPosted)
      stay()

    case Event(NotaryOnline(_), Data(u, Some(a), cm, alt, art, lp)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! NotaryOnline(u)
        // TODO enable contracts in UI when notary is online
        cm.values.foreach(c => context.parent ! ContractAdded(c.notary.url, c))
      }

    case Event(NotaryOnline(_), Data(u, _, cm, alt, art, lp)) =>
      goto(ONLINE) andThen { ud =>
        context.parent ! NotaryOnline(u)
        cm.values.foreach(c => context.parent ! ContractAdded(c.notary.url, c))
      }

    case Event(NotaryOffline(_), _) =>
      stay()

    // TODO manage trades while offline

    // update latestUpdate for other posted events
    case Event(te: TradeFSM.PostedEvent, d) if te.posted.isDefined =>
      stay() applying PostedTradeEventReceived(d.url, te.posted) andThen { ud =>
        context.parent ! te
      }

    // forward all other trade events to parent
    case Event(te: TradeFSM.Event, _) =>
      context.parent ! te
      stay()

  }

  initialize()

  // http flow

  def connectionFlow(url: URL): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(host = url.getHost, port = url.getPort)

  // http get events requester and handler

  def reqNotaryEvents(url: URL, since: Option[DateTime]): Unit = {

    val query = since match {
      case Some(dt) => s"?since=${dt.toString}"
      case None => ""
    }

    val notaryUri = s"/notary$query"

    val req = Source.single(HttpRequest(uri = notaryUri, method = HttpMethods.GET))
      .via(connectionFlow(url))

    req.runWith(Sink.head).onComplete {

      case Success(HttpResponse(StatusCodes.OK, headers, entity, protocol)) =>
        log.debug(s"Response from ${url.toString} $notaryUri OK")
        Unmarshal(entity).to[PostedEvents].onSuccess {
          case PostedEvents(aes, tes) =>
            self ! NotaryOnline(url)
            aes.foreach(self ! ReceivePostedNotaryEvent(_))
            tes.foreach(self ! ReceivePostedTradeEvent(_))
          case _ =>
            log.error("No notary events in response.")
        }

      case Success(HttpResponse(StatusCodes.NoContent, headers, entity, protocol)) =>
        log.debug(s"No new events from ${url.toString}$notaryUri")
        self ! NotaryOnline(url)

      case Success(HttpResponse(sc, headers, entity, protocol)) =>
        log.error(s"Response from ${url.toString}$notaryUri ${sc.toString()}")

      case Failure(failure) =>
        log.debug(s"No Response from ${url.toString}: $failure")
        self ! NotaryOffline(url)
    }
  }

  // create trade FSMs

  def createSellTrade(id: UUID, o: Offer): ActorRef = {
    context.actorOf(TradeFSM.sellProps(o, walletMgr), TradeFSM.name(id))
  }

  def createBuyTrade(id: UUID, so: SellOffer): ActorRef = {
    context.actorOf(TradeFSM.buyProps(so, walletMgr), TradeFSM.name(id))
  }

  def stopTrade(id: UUID) = {
    tradeFSM(id).foreach(context.stop)
  }

  // find trade FSM
  def tradeFSM(id: UUID): Option[ActorRef] = context.child(TradeFSM.name(id))
}
