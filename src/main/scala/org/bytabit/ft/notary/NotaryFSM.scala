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

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, NOTARY, Role, SELLER}
import org.bytabit.ft.notary.NotaryClient.{ReceivePostedNotaryEvent, ReceivePostedTradeEvent}
import org.bytabit.ft.notary.NotaryFSM._
import org.bytabit.ft.notary.server.PostedEvents
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.model.{Contract, SellOffer}
import org.bytabit.ft.util.{DateTimeOrdering, Posted}
import org.bytabit.ft.wallet.model.Notary
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.{ClassTag, _}
import scala.util.{Failure, Success}

object NotaryFSM {

  // actor setup

  def props(url: URL, walletMgr: ActorRef) = Props(new NotaryClient(url, walletMgr))

  def name(url: URL) = s"${NotaryFSM.getClass.getSimpleName}-${url.getHost}-${url.getPort}"

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

  final case class NotarizeTradeAdded(url: URL, tradeId: UUID, offer: SellOffer,
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

  trait NotaryData {
    val serverUrl: URL

    def latest(latestPosted: DateTime, newPosted: DateTime): DateTime = Seq(newPosted, latestPosted).reduce(DateTimeOrdering.max)
  }

  case class AddedNotary(serverUrl: URL) extends NotaryData {

    def created(notary: Notary, posted: DateTime) = ActiveNotary(notary, posted)
  }

  case class ActiveNotary(notary: Notary, latestPosted: DateTime,
                          contracts: Map[Sha256Hash, Contract] = Map(),
                          activeTrades: Map[Role, Map[UUID, SellOffer]] = Map()) extends NotaryData {

    val serverUrl = notary.url

    def postedEventReceived(posted: DateTime) =
      this.copy(latestPosted = latest(posted, latestPosted))

    def contractAdded(contract: Contract, posted: DateTime) =
      this.copy(contracts = contracts + (contract.id -> contract),
        latestPosted = latest(posted, latestPosted))

    def contractRemoved(id: Sha256Hash, posted: DateTime) =
      this.copy(contracts = contracts - id,
        latestPosted = latest(posted, latestPosted))

    def tradeAdded(role: Role, id: UUID, offer: SellOffer, posted: DateTime) = {
      val updatedRoleTrades: Map[UUID, SellOffer] = activeTrades.getOrElse(role, Map()) + (id -> offer)
      this.copy(activeTrades = activeTrades + (role -> updatedRoleTrades),
        latestPosted = latest(posted, latestPosted))
    }

    def tradeRemoved(id: UUID, posted: DateTime) = {
      val updatedTrades = activeTrades.map { rm => rm._1 -> (rm._2 - id) }
      this.copy(activeTrades = updatedTrades,
        latestPosted = latest(posted, latestPosted))
    }
  }

}

trait NotaryFSM extends PersistentFSM[NotaryFSM.State, NotaryFSM.NotaryData, NotaryFSM.Event] with NotaryFSMJsonProtocol {

  val url: URL

  // implicits

  implicit val system = context.system

  implicit def executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  // persistence

  override def persistenceId = NotaryFSM.name(url)

  override def domainEventClassTag: ClassTag[NotaryFSM.Event] = classTag[NotaryFSM.Event]

  // apply events to state and data

  def applyEvent(event: NotaryFSM.Event, notaryData: NotaryFSM.NotaryData): NotaryData =
    (event, notaryData) match {

      case (NotaryCreated(u, n, Some(p)), an: AddedNotary) =>
        an.created(n, p)

      case (ContractAdded(u, c, Some(p)), an: ActiveNotary) =>
        an.contractAdded(c, p)

      case (ContractRemoved(u, id, Some(p)), an: ActiveNotary) =>
        an.contractRemoved(id, p)

      case (SellTradeAdded(u, i, o, Some(p)), an: ActiveNotary) =>
        an.tradeAdded(SELLER, i, o, p)

      case (BuyTradeAdded(u, i, o, Some(p)), an: ActiveNotary) =>
        an.tradeAdded(BUYER, i, o, p)

      case (NotarizeTradeAdded(u, i, o, Some(p)), an: ActiveNotary) =>
        an.tradeAdded(NOTARY, i, o, p)

      case (TradeRemoved(u, i, Some(p)), an: ActiveNotary) =>
        an.tradeRemoved(i, p)

      case (PostedTradeEventReceived(u, Some(p)), an: ActiveNotary) =>
        an.postedEventReceived(p)

      case _ => notaryData
    }

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

  def stopTrade(id: UUID) = {
    tradeFSM(id).foreach(context.stop)
  }

  // find trade FSM
  def tradeFSM(id: UUID): Option[ActorRef] = context.child(TradeFSM.name(id))

}
