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

package org.bytabit.ft.trade

import java.net.URL
import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.bitcoinj.core.{Address, Transaction, TransactionOutput}
import org.bytabit.ft.trade.TradeFSM.{SellerSignedOffer, _}
import org.bytabit.ft.trade.model.{SellOffer, TakenOffer, TradeData, _}
import org.bytabit.ft.util.Posted
import org.bytabit.ft.wallet.model.{Buyer, Seller, Tx, TxSig}
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.reflect._
import scala.util.{Failure, Success}

object TradeFSM {

  // actor setup

  def sellProps(offer: Offer, walletMgrRef: ActorRef) = Props(new SellFSM(offer, walletMgrRef))

  def buyProps(sellOffer: SellOffer, walletMgrRef: ActorRef) = Props(new BuyFSM(sellOffer, walletMgrRef))

  def name(id: UUID) = s"tradeFSM-${id.toString}"

  // events

  sealed trait Event {
    val id: UUID
  }

  sealed trait PostedEvent extends Event with Posted

  final case class SellerAddedToOffer(id: UUID, seller: Seller) extends Event

  final case class LocalSellerCreatedOffer(id: UUID, offer: SellOffer,
                                           posted: Option[DateTime] = None) extends PostedEvent

  final case class SellerCreatedOffer(id: UUID, offer: SellOffer,
                                      posted: Option[DateTime] = None) extends PostedEvent

  final case class SellerCanceledOffer(id: UUID,
                                       posted: Option[DateTime] = None) extends PostedEvent

  final case class BuyerTookOffer(id: UUID, buyer: Buyer, buyerOpenTxSigs: Seq[TxSig],
                                  buyerFundPayoutTxo: Seq[TransactionOutput],
                                  posted: Option[DateTime] = None) extends PostedEvent

  final case class SellerSignedOffer(id: UUID, buyerId: Address, openSigs: Seq[TxSig], payoutSigs: Seq[TxSig],
                                     posted: Option[DateTime] = None) extends PostedEvent

  final case class BuyerOpenedEscrow(id: UUID, openSigs: Seq[TxSig]) extends Event

  final case class BuyerFundedEscrow(id: UUID) extends Event

  final case class FiatReceived(id: UUID) extends Event

  final case class BuyerReceivedPayout(id: UUID) extends Event

  final case class SellerReceivedPayout(id: UUID) extends Event

  // states

  sealed trait State extends FSMState

  case object ADDED extends State {
    override val identifier: String = "ADDED"
  }

  case object CREATED extends State {
    override val identifier: String = "CREATED"
  }

  case object CANCELED extends State {
    override val identifier: String = "CANCELED"
  }

  case object TAKEN extends State {
    override val identifier: String = "TAKEN"
  }

  case object SIGNED extends State {
    override val identifier: String = "SIGNED"
  }

  case object OPENED extends State {
    override val identifier: String = "OPENED"
  }

  case object FUNDED extends State {
    override val identifier: String = "FUNDED"
  }

  case object FIAT_SENT extends State {
    override val identifier: String = "FIAT SENT"
  }

  case object FIAT_RCVD extends State {
    override val identifier: String = "FIAT RCVD"
  }

  case object BOUGHT extends State {
    override val identifier: String = "BOUGHT"
  }

  case object SOLD extends State {
    override val identifier: String = "SOLD"
  }

}

abstract class TradeFSM(id: UUID)
  extends PersistentFSM[TradeFSM.State, TradeData, TradeFSM.Event] with TradeFSMJsonProtocol {

  import spray.json._

  //val log: LoggingAdapter

  // implicits

  implicit val system = context.system

  implicit def executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  // persistence

  override def persistenceId: String = TradeFSM.name(id)

  override def domainEventClassTag: ClassTag[TradeFSM.Event] = classTag[TradeFSM.Event]

  // apply events to trade data

  def applyEvent(event: TradeFSM.Event, tradeData: TradeData): TradeData =

    (event, tradeData) match {

      case (SellerCreatedOffer(_, so, Some(_)), offer: Offer) =>
        so

      case (BuyerTookOffer(_, b, bots, bfpt, _), sellOffer: SellOffer) =>
        sellOffer.withBuyer(b, bots, bfpt)

      case (SellerSignedOffer(_, bi, sots, spts, Some(_)), takenOffer: TakenOffer) =>
        takenOffer.withSellerSigs(sots, spts)

      case _ =>
        tradeData
    }

  // http flow

  def connectionFlow(url: URL) = Http().outgoingConnection(host = url.getHost, port = url.getPort)

  // http request and handler

  def postTradeEvent(url: URL, postedEvent: TradeFSM.PostedEvent, self: ActorRef): Unit = {

    val tradeUri = s"/trade"

    Marshal(postedEvent.toJson).to[RequestEntity].onSuccess {

      case reqEntity =>

        val req = Source.single(HttpRequest(uri = tradeUri, method = HttpMethods.POST,
          entity = reqEntity.withContentType(ContentTypes.`application/json`)))
          .via(connectionFlow(url))

        req.runWith(Sink.head).onComplete {

          case Success(HttpResponse(StatusCodes.OK, headers, respEntity, protocol)) =>
            log.debug(s"Response from ${url.toString}$tradeUri OK, $respEntity")
            Unmarshal(respEntity).to[TradeFSM.PostedEvent].onSuccess {
              case pe: TradeFSM.PostedEvent if pe.posted.isDefined =>
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

  def outputsEqual(tx1: Tx, tx2: Transaction): Boolean = {
    tx1.outputs.toSet == tx2.getOutputs.toSet
  }
}
