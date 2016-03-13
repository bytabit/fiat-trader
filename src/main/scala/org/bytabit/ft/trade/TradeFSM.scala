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
import org.bitcoinj.core.{Address, Sha256Hash, Transaction, TransactionOutput}
import org.bytabit.ft.trade.TradeFSM.{SellerSignedOffer, _}
import org.bytabit.ft.trade.model.{SellOffer, TakenOffer, TradeData, _}
import org.bytabit.ft.util.Posted
import org.bytabit.ft.wallet.model.{Buyer, Seller, Tx, TxSig}
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.reflect._
import scala.util.{Failure, Success, Try}

object TradeFSM {

  // actor setup

  def sellProps(offer: Offer, walletMgrRef: ActorRef) = Props(new SellProcess(offer, walletMgrRef))

  def buyProps(sellOffer: SellOffer, walletMgrRef: ActorRef) = Props(new BuyProcess(sellOffer, walletMgrRef))

  def notarizeProps(sellOffer: SellOffer, walletMgrRef: ActorRef) = Props(new NotarizeProcess(sellOffer, walletMgrRef))

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

  final case class BuyerSetFiatDeliveryDetailsKey(id: UUID, fiatDeliveryDetailsKey: Array[Byte]) extends Event

  final case class BuyerTookOffer(id: UUID, buyer: Buyer, buyerOpenTxSigs: Seq[TxSig],
                                  buyerFundPayoutTxo: Seq[TransactionOutput],
                                  cipherBuyerDeliveryDetails: Array[Byte],
                                  posted: Option[DateTime] = None) extends PostedEvent

  final case class SellerSignedOffer(id: UUID, buyerId: Address, openSigs: Seq[TxSig], payoutSigs: Seq[TxSig],
                                     posted: Option[DateTime] = None) extends PostedEvent

  final case class BuyerOpenedEscrow(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class BuyerFundedEscrow(id: UUID, txHash: Sha256Hash, updateTime: DateTime, fiatDeliveryDetailsKey: Option[Array[Byte]]) extends Event

  final case class FiatReceived(id: UUID) extends Event

  final case class FiatSent(id: UUID) extends Event

  final case class BuyerReceivedPayout(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class SellerReceivedPayout(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class CertifyDeliveryRequested(id: UUID, evidence: Option[Array[Byte]] = None,
                                            posted: Option[DateTime] = None) extends PostedEvent

  final case class FiatSentCertified(id: UUID, payoutSigs: Seq[TxSig],
                                     posted: Option[DateTime] = None) extends PostedEvent

  final case class FiatNotSentCertified(id: UUID, payoutSigs: Seq[TxSig],
                                        posted: Option[DateTime] = None) extends PostedEvent

  final case class SellerFunded(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class BuyerRefunded(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

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

  case object TRADED extends State {
    override val identifier: String = "TRADED"
  }

  case object CERT_DELIVERY_REQD extends State {
    override val identifier: String = "CERT DELIVERY REQD"
  }

  case object FIAT_SENT_CERTD extends State {
    override val identifier: String = "FIAT SENT CERTD"
  }

  case object FIAT_NOT_SENT_CERTD extends State {
    override val identifier: String = "FIAT NOT SENT CERTD"
  }

  case object SELLER_FUNDED extends State {
    override val identifier: String = "SELLER FUNDED"
  }

  case object BUYER_REFUNDED extends State {
    override val identifier: String = "BUYER REFUNDED"
  }

}

trait TradeFSM extends PersistentFSM[TradeFSM.State, TradeData, TradeFSM.Event] with TradeFSMJsonProtocol {

  import spray.json._

  val id: UUID

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

      // common path

      case (SellerCreatedOffer(_, so, Some(_)), offer: Offer) =>
        so

      case (BuyerTookOffer(_, b, bots, bfpt, cdd, _), sellOffer: SellOffer) =>
        sellOffer.withBuyer(b, bots, bfpt, cdd)

      case (BuyerTookOffer(_, b, bots, bfpt, cdd, _), takenOffer: TakenOffer) =>
        takenOffer

      case (SellerSignedOffer(_, bi, sots, spts, Some(_)), takenOffer: TakenOffer) =>
        takenOffer.withSellerSigs(sots, spts)

      case (BuyerSetFiatDeliveryDetailsKey(_, dk), takenOffer: TakenOffer) =>
        takenOffer.withFiatDeliveryDetailsKey(dk)

      case (BuyerOpenedEscrow(_, th, ut), signedTakenOffer: SignedTakenOffer) =>
        signedTakenOffer.withOpenTx(th, ut)

      case (BuyerFundedEscrow(_, th, ut, fdk), openedTrade: OpenedTrade) =>
        openedTrade.withFundTx(th, ut, fdk)

      // happy path

      case (BuyerReceivedPayout(_, txh, ut), fundedTrade: FundedTrade) =>
        fundedTrade.withPayoutTx(txh, ut)

      case (SellerReceivedPayout(_, txh, ut), fundedTrade: FundedTrade) =>
        fundedTrade.withPayoutTx(txh, ut)

      // unhappy path

      case (CertifyDeliveryRequested(_, e, Some(_)), fundedTrade: FundedTrade) =>
        fundedTrade.certifyFiatRequested(e)

      case (CertifyDeliveryRequested(_, e, Some(_)), certifyFiatEvidence: CertifyFiatEvidence) =>
        certifyFiatEvidence.addCertifyDeliveryRequest(e)
      //certifyFiatEvidence.copy(evidence = certifyFiatEvidence.evidence ++ e.toSeq)

      case (FiatSentCertified(_, ps, Some(_)), certifyFiatEvidence: CertifyFiatEvidence) =>
        certifyFiatEvidence.withNotarizedFiatSentSigs(ps)

      case (FiatNotSentCertified(_, ps, Some(_)), certifyFiatEvidence: CertifyFiatEvidence) =>
        certifyFiatEvidence.withNotarizedFiatNotSentSigs(ps)

      case (SellerFunded(_, th, ut), certifiedFiatDelivery: CertifiedFiatDelivery) =>
        certifiedFiatDelivery.withPayoutTx(th, ut)

      case (BuyerRefunded(_, th, ut), certifiedFiatDelivery: CertifiedFiatDelivery) =>
        certifiedFiatDelivery.withPayoutTx(th, ut)

      // cancel path

      // TODO FT-80, FT-81, FT-82

      // error

      case _ =>
        log.error(s"No transition for event: $event\nwith trade data: ${tradeData.getClass.getSimpleName}")
        tradeData
    }

  // send start events for each state

  // common path

  def startCreate(so: SellOffer): Unit = {
    context.parent ! SellerCreatedOffer(so.id, so)
  }

  def startTaken(to: TakenOffer): Unit = {
    startCreate(to.sellOffer)
    context.parent ! BuyerTookOffer(to.id, to.buyer, to.buyerOpenTxSigs, to.buyerFundPayoutTxo, to.cipherFiatDeliveryDetails)
  }

  def startSigned(sto: SignedTakenOffer): Unit = {
    startTaken(sto.takenOffer)
    context.parent ! SellerSignedOffer(sto.id, sto.buyer.id, sto.sellerOpenTxSigs, sto.sellerPayoutTxSigs)
  }

  def startOpened(ot: OpenedTrade): Unit = {
    startSigned(ot.signedTakenOffer)
    context.parent ! BuyerOpenedEscrow(ot.id, ot.openTxHash, ot.openTxUpdateTime)
  }

  def startFunded(ft: FundedTrade): Unit = {
    startOpened(ft.openedTrade)
    context.parent ! BuyerFundedEscrow(ft.id, ft.fundTxHash, ft.fundTxUpdateTime, ft.fiatDeliveryDetailsKey)
  }

  // happy path

  def startTraded(st: SettledTrade): Unit = {
    startFunded(st.fundedTrade)
    context.parent ! SellerReceivedPayout(st.id, st.payoutTxHash, st.payoutTxUpdateTime)
  }

  // unhappy path

  def startCertDeliveryReqd(cfe: CertifyFiatEvidence): Unit = {
    startFunded(cfe.fundedTrade)
    context.parent ! CertifyDeliveryRequested(cfe.id)
  }

  def startFiatSentCertd(cfd: CertifiedFiatDelivery): Unit = {
    startCertDeliveryReqd(cfd.certifyFiatEvidence)
    context.parent ! FiatSentCertified(cfd.id, cfd.notaryPayoutTxSigs)
  }

  def startFiatNotSentCertd(cfd: CertifiedFiatDelivery): Unit = {
    startCertDeliveryReqd(cfd.certifyFiatEvidence)
    context.parent ! FiatNotSentCertified(cfd.id, cfd.notaryPayoutTxSigs)
  }

  def startSellerFunded(cst: CertifiedSettledTrade): Unit = {
    startFiatSentCertd(cst.certifiedFiatDelivery)
    context.parent ! SellerFunded(cst.id, cst.payoutTxHash, cst.payoutTxUpdateTime)
  }

  def startBuyerRefunded(cst: CertifiedSettledTrade): Unit = {
    startFiatNotSentCertd(cst.certifiedFiatDelivery)
    context.parent ! BuyerRefunded(cst.id, cst.payoutTxHash, cst.payoutTxUpdateTime)
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

  def outputsEqual(tx1: Tx, tx2: Transaction, from: Int, until: Int): Boolean = {
    tx1.outputs.slice(from, until).toSet == tx2.getOutputs.slice(from, until).toSet
  }

  def outputsEqual(tx1: Tx, tx2: Transaction): Boolean = {
    val until: Int = Seq(tx1.outputs.length - 1, tx2.getOutputs.length - 1).min
    outputsEqual(tx1, tx2, 0, until)
  }

  def fiatDeliveryDetailsKey(tx: Transaction): Option[Array[Byte]] = {
    Try {
      val outputs = tx.getOutputs
      val lastOutputScript = outputs.get(outputs.size - 1).getScriptPubKey
      lastOutputScript.getChunks.get(1).data
    }.toOption
  }
}
