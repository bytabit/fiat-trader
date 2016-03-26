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

package org.bytabit.ft.arbitrator

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
import org.bytabit.ft.arbitrator.ArbitratorClient.{ReceivePostedArbitratorEvent, ReceivePostedTradeEvent}
import org.bytabit.ft.arbitrator.ArbitratorFSM._
import org.bytabit.ft.arbitrator.server.PostedEvents
import org.bytabit.ft.fxui.model.TradeUIModel.{ARBITRATOR, BUYER, Role, SELLER}
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.model.{Contract, SellOffer}
import org.bytabit.ft.util.{DateTimeOrdering, Posted}
import org.bytabit.ft.wallet.model.Arbitrator
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.{ClassTag, _}
import scala.util.{Failure, Success}

object ArbitratorFSM {

  // actor setup

  def props(url: URL, walletMgr: ActorRef) = Props(new ArbitratorClient(url, walletMgr))

  def name(url: URL) = s"${ArbitratorFSM.getClass.getSimpleName}-${url.getHost}-${url.getPort}"

  // events

  sealed trait Event {
    val url: URL
  }

  sealed trait PostedEvent extends Event with Posted

  // arbitrator events

  final case class ArbitratorCreated(url: URL, arbitrator: Arbitrator,
                                     posted: Option[DateTime] = None) extends PostedEvent

  final case class ContractAdded(url: URL, contract: Contract,
                                 posted: Option[DateTime] = None) extends PostedEvent

  final case class ContractRemoved(url: URL, id: Sha256Hash,
                                   posted: Option[DateTime] = None) extends PostedEvent

  final case class ArbitratorOnline(url: URL) extends Event

  final case class ArbitratorOffline(url: URL) extends Event

  // trade events

  final case class SellTradeAdded(url: URL, tradeId: UUID, offer: SellOffer,
                                  posted: Option[DateTime] = None) extends Event

  final case class BuyTradeAdded(url: URL, tradeId: UUID, offer: SellOffer,
                                 posted: Option[DateTime] = None) extends Event

  final case class ArbitrateTradeAdded(url: URL, tradeId: UUID, offer: SellOffer,
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

  trait ArbitratorData {
    val serverUrl: URL

    def latest(latestPosted: DateTime, newPosted: DateTime): DateTime = Seq(newPosted, latestPosted).reduce(DateTimeOrdering.max)
  }

  case class AddedArbitrator(serverUrl: URL) extends ArbitratorData {

    def created(arbitrator: Arbitrator, posted: DateTime) = ActiveArbitrator(arbitrator, posted)
  }

  case class ActiveArbitrator(arbitrator: Arbitrator, latestPosted: DateTime,
                              contracts: Map[Sha256Hash, Contract] = Map(),
                              activeTrades: Map[Role, Map[UUID, SellOffer]] = Map()) extends ArbitratorData {

    val serverUrl = arbitrator.url

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

trait ArbitratorFSM extends PersistentFSM[ArbitratorFSM.State, ArbitratorFSM.ArbitratorData, ArbitratorFSM.Event] with ArbitratorFSMJsonProtocol {

  val url: URL

  // implicits

  implicit val system = context.system

  implicit def executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  // persistence

  override def persistenceId = ArbitratorFSM.name(url)

  override def domainEventClassTag: ClassTag[ArbitratorFSM.Event] = classTag[ArbitratorFSM.Event]

  // apply events to state and data

  def applyEvent(event: ArbitratorFSM.Event, arbitratorData: ArbitratorFSM.ArbitratorData): ArbitratorData =
    (event, arbitratorData) match {

      case (ArbitratorCreated(u, n, Some(p)), an: AddedArbitrator) =>
        an.created(n, p)

      case (ContractAdded(u, c, Some(p)), an: ActiveArbitrator) =>
        an.contractAdded(c, p)

      case (ContractRemoved(u, id, Some(p)), an: ActiveArbitrator) =>
        an.contractRemoved(id, p)

      case (SellTradeAdded(u, i, o, Some(p)), an: ActiveArbitrator) =>
        an.tradeAdded(SELLER, i, o, p)

      case (BuyTradeAdded(u, i, o, Some(p)), an: ActiveArbitrator) =>
        an.tradeAdded(BUYER, i, o, p)

      case (ArbitrateTradeAdded(u, i, o, Some(p)), an: ActiveArbitrator) =>
        an.tradeAdded(ARBITRATOR, i, o, p)

      case (TradeRemoved(u, i, Some(p)), an: ActiveArbitrator) =>
        an.tradeRemoved(i, p)

      case (PostedTradeEventReceived(u, Some(p)), an: ActiveArbitrator) =>
        an.postedEventReceived(p)

      case _ => arbitratorData
    }

  // http flow

  def connectionFlow(url: URL): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(host = url.getHost, port = url.getPort)

  // http get events requester and handler

  def reqArbitratorEvents(url: URL, since: Option[DateTime]): Unit = {

    val query = since match {
      case Some(dt) => s"?since=${dt.toString}"
      case None => ""
    }

    val arbitratorUri = s"/arbitrator$query"

    val req = Source.single(HttpRequest(uri = arbitratorUri, method = HttpMethods.GET))
      .via(connectionFlow(url))

    req.runWith(Sink.head).onComplete {

      case Success(HttpResponse(StatusCodes.OK, headers, entity, protocol)) =>
        log.debug(s"Response from ${url.toString} $arbitratorUri OK")
        Unmarshal(entity).to[PostedEvents].onSuccess {
          case PostedEvents(aes, tes) =>
            self ! ArbitratorOnline(url)
            aes.foreach(self ! ReceivePostedArbitratorEvent(_))
            tes.foreach(self ! ReceivePostedTradeEvent(_))
          case _ =>
            log.error("No arbitrator events in response.")
        }

      case Success(HttpResponse(StatusCodes.NoContent, headers, entity, protocol)) =>
        log.debug(s"No new events from ${url.toString}$arbitratorUri")
        self ! ArbitratorOnline(url)

      case Success(HttpResponse(sc, headers, entity, protocol)) =>
        log.error(s"Response from ${url.toString}$arbitratorUri ${sc.toString()}")

      case Failure(failure) =>
        log.debug(s"No Response from ${url.toString}: $failure")
        self ! ArbitratorOffline(url)
    }
  }

  def stopTrade(id: UUID) = {
    tradeFSM(id).foreach(context.stop)
  }

  // find trade FSM
  def tradeFSM(id: UUID): Option[ActorRef] = context.child(TradeFSM.name(id))

}
