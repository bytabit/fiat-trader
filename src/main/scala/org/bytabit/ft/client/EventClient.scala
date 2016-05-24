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

import akka.actor.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.client.EventClient._
import org.bytabit.ft.server.PostedEvents
import org.bytabit.ft.trade.TradeProcess
import org.bytabit.ft.trade.TradeProcess.TradeOffline
import org.bytabit.ft.trade.model._
import org.bytabit.ft.util.DateTimeOrdering
import org.bytabit.ft.wallet.model.Arbitrator
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.{ClassTag, _}
import scala.util.{Failure, Success}

object EventClient {

  // commands

  sealed trait Command

  case object Start extends Command

  final case class ReceivePostedArbitratorEvent(event: ArbitratorManager.PostedEvent) extends Command {
    assert(event.posted.isDefined)
  }

  final case class ReceivePostedTradeEvent(event: TradeProcess.PostedEvent) extends Command {
    assert(event.posted.isDefined)
  }

  // events

  sealed trait Event {
    val url: URL
  }

  // server events

  final case class ServerOnline(url: URL) extends Event

  final case class ServerOffline(url: URL) extends Event

  final case class NoPostedEventsReceived(url: URL) extends Event

  final case class PostedEventReceived(url: URL, posted: Option[DateTime]) extends Event

  // arbitrator events

  final case class ArbitratorAdded(url: URL, arbitrator: Arbitrator, posted: Option[DateTime] = None) extends Event

  final case class ArbitratorRemoved(url: URL, posted: Option[DateTime] = None) extends Event


  // trade events

  final case class TradeAdded(url: URL, role: Role, tradeId: UUID, offer: SellOffer, posted: Option[DateTime] = None) extends Event

  final case class TradeRemoved(url: URL, tradeId: UUID, posted: Option[DateTime]) extends Event

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

  trait Data {
    val serverUrl: URL

    def latest(latestPosted: DateTime, newPosted: DateTime): DateTime = Seq(newPosted, latestPosted).reduce(DateTimeOrdering.max)
  }

  case class AddedServer(serverUrl: URL) extends Data {

    def added(arbitrator: Arbitrator, posted: DateTime) = ActiveServer(posted, arbitrator)
  }

  case class ActiveServer(latestPosted: DateTime, arbitrator: Arbitrator,
                          trades: Map[Role, Map[UUID, SellOffer]] = Map()) extends Data {

    val serverUrl = arbitrator.url

    def postedEventReceived(posted: DateTime) =
      this.copy(latestPosted = latest(posted, latestPosted))

    def tradeAdded(role: Role, id: UUID, offer: SellOffer, posted: DateTime) = {
      val updatedRoleTrades: Map[UUID, SellOffer] = trades.getOrElse(role, Map()) + (id -> offer)
      this.copy(trades = trades + (role -> updatedRoleTrades),
        latestPosted = latest(posted, latestPosted))
    }

    def tradeRemoved(id: UUID, posted: DateTime) = {
      val updatedTrades = trades.map { rm => rm._1 -> (rm._2 - id) }
      this.copy(trades = updatedTrades,
        latestPosted = latest(posted, latestPosted))
    }
  }

}

trait EventClient extends PersistentFSM[EventClient.State, EventClient.Data, EventClient.Event] with EventClientJsonProtocol {

  val url: URL

  val tradeWalletMgr: ActorRef

  val escrowWalletMgr: ActorRef

  // implicits

  implicit val system = context.system

  implicit def executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  // persistence

  override def domainEventClassTag: ClassTag[EventClient.Event] = classTag[EventClient.Event]

  // apply events to state and data

  def applyEvent(event: EventClient.Event, data: EventClient.Data): Data =
    (event, data) match {

      case (ArbitratorAdded(u, a, Some(p)), as: AddedServer) =>
        as.added(a, p)

      case (TradeAdded(u, r, i, o, Some(p)), as: ActiveServer) =>
        as.tradeAdded(r, i, o, p)

      case (TradeRemoved(u, i, Some(p)), as: ActiveServer) =>
        as.tradeRemoved(i, p)

      case (PostedEventReceived(u, Some(p)), as: ActiveServer) =>
        as.postedEventReceived(p)

      case _ => data
    }

  // http flow

  def connectionFlow(url: URL): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(host = url.getHost, port = url.getPort)

  // http get events requester and handler

  def reqPostedEvents(url: URL, since: Option[DateTime]): Unit = {

    val query = since match {
      case Some(dt) => s"?since=${dateTimeFormatter.print(dt)}"
      case None => ""
    }

    val arbitratorUri = s"/events$query"

    val req = Source.single(HttpRequest(uri = arbitratorUri, method = HttpMethods.GET))
      .via(connectionFlow(url))

    req.runWith(Sink.head).onComplete {

      case Success(HttpResponse(StatusCodes.OK, headers, entity, protocol)) =>
        log.debug(s"Response from ${url.toString} $arbitratorUri OK")
        Unmarshal(entity).to[PostedEvents].onSuccess {
          case PostedEvents(aes, tes) =>
            self ! ServerOnline(url)
            aes.foreach(self ! ReceivePostedArbitratorEvent(_))
            tes.foreach(self ! ReceivePostedTradeEvent(_))
          case _ =>
            log.error("No arbitrator events in response.")
        }

      case Success(HttpResponse(StatusCodes.NoContent, headers, entity, protocol)) =>
        log.debug(s"No new events from ${url.toString}$arbitratorUri")
        self ! ServerOnline(url)
        self ! NoPostedEventsReceived(url)

      case Success(HttpResponse(sc, headers, entity, protocol)) =>
        log.error(s"Response from ${url.toString}$arbitratorUri ${sc.toString()}")

      case Failure(failure) =>
        log.debug(s"No Response from ${url.toString}: $failure")
        self ! ServerOffline(url)
    }
  }

  // create ArbitratorManager
  def createArbitratorManager(arbitrator: Arbitrator): ActorRef = {
    context.actorOf(ArbitratorManager.props(arbitrator), ArbitratorManager.name(arbitrator))
  }

  // find ArbitratorManager
  def arbitratorManager(a: Arbitrator): Option[ActorRef] = context.child(ArbitratorManager.name(a))

  // stop ArbitratorManager
  def stopArbitratorManager(a: Arbitrator) = {
    arbitratorManager(a).foreach(context.stop)
  }

  // create trade Processes
  def createArbitrateTrade(id: UUID, so: SellOffer): ActorRef = {
    context.actorOf(TradeProcess.arbitrateProps(so, tradeWalletMgr, escrowWalletMgr), TradeProcess.name(id))
  }

  def createSellTrade(id: UUID, o: Offer): ActorRef = {
    context.actorOf(TradeProcess.sellProps(o, tradeWalletMgr, escrowWalletMgr), TradeProcess.name(id))
  }

  def createBuyTrade(id: UUID, so: SellOffer): ActorRef = {
    context.actorOf(TradeProcess.buyProps(so, tradeWalletMgr, escrowWalletMgr), TradeProcess.name(id))
  }

  // find trade process
  def tradeProcess(id: UUID): Option[ActorRef] = context.child(TradeProcess.name(id))

  // stop trade processes
  def stopTrade(id: UUID) = {
    tradeProcess(id).foreach(context.stop)
  }
}
