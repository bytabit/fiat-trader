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
import org.bytabit.ft.trade.TradeProcess.{BtcBuyerSignedOffer, _}
import org.bytabit.ft.trade.model.{BtcBuyOffer, TakenOffer, TradeData, _}
import org.bytabit.ft.util.Posted
import org.bytabit.ft.wallet.model._
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.reflect._
import scala.util.{Failure, Success, Try}

object TradeProcess {

  // actor setup

  def btcBuyProps(offer: Offer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) = Props(new BtcBuyProcess(offer, tradeWalletMgrRef, escrowWalletMgrRef))

  def btcSellProps(btcBuyOffer: BtcBuyOffer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) = Props(new BtcSellProcess(btcBuyOffer, tradeWalletMgrRef, escrowWalletMgrRef))

  def arbitrateProps(btcBuyOffer: BtcBuyOffer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) = Props(new ArbitrateProcess(btcBuyOffer, tradeWalletMgrRef, escrowWalletMgrRef))

  def name(id: UUID) = s"tradeProcess-${id.toString}"

  // events

  sealed trait Event {
    val id: UUID
  }

  sealed trait PostedEvent extends Event with Posted

  final case class BtcBuyerAddedToOffer(id: UUID, btcBuyer: BtcBuyer) extends Event

  final case class LocalBtcBuyerCreatedOffer(id: UUID, offer: BtcBuyOffer,
                                             posted: Option[DateTime] = None) extends PostedEvent

  final case class BtcBuyerCreatedOffer(id: UUID, offer: BtcBuyOffer,
                                        posted: Option[DateTime] = None) extends PostedEvent

  final case class BtcBuyerCanceledOffer(id: UUID,
                                         posted: Option[DateTime] = None) extends PostedEvent

  final case class BtcSellerSetPaymentDetailsKey(id: UUID, paymentDetailsKey: Array[Byte]) extends Event

  final case class BtcSellerTookOffer(id: UUID, btcSeller: BtcSeller, btcSellerOpenTxSigs: Seq[TxSig],
                                      btcSellerFundPayoutTxo: Seq[TransactionOutput],
                                      cipherBtcSellerPaymentDetails: Array[Byte],
                                      posted: Option[DateTime] = None) extends PostedEvent

  final case class BtcBuyerSignedOffer(id: UUID, btcBuyerId: Address, openSigs: Seq[TxSig], payoutSigs: Seq[TxSig],
                                       posted: Option[DateTime] = None) extends PostedEvent

  final case class BtcSellerOpenedEscrow(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class BtcSellerFundedEscrow(id: UUID, txHash: Sha256Hash, updateTime: DateTime, paymentDetailsKey: Option[Array[Byte]]) extends Event

  final case class FiatReceived(id: UUID) extends Event

  final case class BtcBuyerFiatSent(id: UUID, reference: Option[String] = None,
                                    posted: Option[DateTime] = None) extends PostedEvent

  final case class BtcSellerReceivedPayout(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class BtcBuyerReceivedPayout(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class CertifyPaymentRequested(id: UUID, evidence: Option[Array[Byte]] = None,
                                           posted: Option[DateTime] = None) extends PostedEvent

  final case class FiatSentCertified(id: UUID, payoutSigs: Seq[TxSig],
                                     posted: Option[DateTime] = None) extends PostedEvent

  final case class FiatNotSentCertified(id: UUID, payoutSigs: Seq[TxSig],
                                        posted: Option[DateTime] = None) extends PostedEvent

  final case class BtcBuyerFunded(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

  final case class BtcSellerRefunded(id: UUID, txHash: Sha256Hash, updateTime: DateTime) extends Event

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

  case object CERT_PAYMENT_REQD extends State {
    override val identifier: String = "CERT PAYMENT REQD"
  }

  case object FIAT_SENT_CERTD extends State {
    override val identifier: String = "FIAT SENT CERTD"
  }

  case object FIAT_NOT_SENT_CERTD extends State {
    override val identifier: String = "FIAT NOT SENT CERTD"
  }

  case object BTCBUYER_FUNDED extends State {
    override val identifier: String = "BTCBUYER FUNDED"
  }

  case object BTCSELLER_REFUNDED extends State {
    override val identifier: String = "BTCSELLER REFUNDED"
  }

}

trait TradeProcess extends PersistentFSM[TradeProcess.State, TradeData, TradeProcess.Event] with TradeJsonProtocol {

  import spray.json._

  val id: UUID

  val tradeWalletMgrRef: ActorRef

  val escrowWalletMgrRef: ActorRef

  // implicits

  implicit val system = context.system

  implicit def executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  // persistence

  override def persistenceId: String = TradeProcess.name(id)

  override def domainEventClassTag: ClassTag[TradeProcess.Event] = classTag[TradeProcess.Event]

  // apply events to trade data

  def applyEvent(event: TradeProcess.Event, tradeData: TradeData): TradeData =

    (event, tradeData) match {

      // common path

      case (BtcBuyerCreatedOffer(_, so, Some(p)), offer: Offer) =>
        so.withPosted(p)

      case (BtcBuyerCreatedOffer(_, so, Some(p)), btcBuyOffer: BtcBuyOffer) =>
        so.withPosted(p)

      case (BtcSellerTookOffer(_, b, bots, bfpt, cdd, _), btcBuyOffer: BtcBuyOffer) =>
        btcBuyOffer.withBtcSeller(b, bots, bfpt, cdd)

      case (BtcSellerTookOffer(_, b, bots, bfpt, cdd, _), takenOffer: TakenOffer) =>
        takenOffer

      case (BtcBuyerSignedOffer(_, bi, sots, spts, Some(_)), takenOffer: TakenOffer) =>
        takenOffer.withBtcBuyerSigs(sots, spts)

      case (BtcSellerSetPaymentDetailsKey(_, dk), takenOffer: TakenOffer) =>
        takenOffer.withPaymentDetailsKey(dk)

      case (BtcSellerOpenedEscrow(_, th, ut), signedTakenOffer: SignedTakenOffer) =>
        signedTakenOffer.withOpenTx(th, ut)

      case (BtcSellerFundedEscrow(_, th, ut, fdk), openedTrade: OpenedTrade) =>
        openedTrade.withFundTx(th, ut, fdk)

      // happy path

      case (BtcBuyerFiatSent(_, ref, posted), fundedTrade: FundedTrade) =>
        fundedTrade.withFiatSentReference(ref)

      case (BtcSellerReceivedPayout(_, txh, ut), fundedTrade: FundedTrade) =>
        fundedTrade.withPayoutTx(txh, ut)

      case (BtcBuyerReceivedPayout(_, txh, ut), fundedTrade: FundedTrade) =>
        fundedTrade.withPayoutTx(txh, ut)

      // unhappy path

      case (CertifyPaymentRequested(_, e, Some(_)), fundedTrade: FundedTrade) =>
        fundedTrade.certifyFiatRequested(e)

      case (CertifyPaymentRequested(_, e, Some(_)), certifyFiatEvidence: CertifyPaymentEvidence) =>
        certifyFiatEvidence.addCertifyPaymentRequest(e)
      //certifyFiatEvidence.copy(evidence = certifyFiatEvidence.evidence ++ e.toSeq)

      case (FiatSentCertified(_, ps, Some(_)), certifyFiatEvidence: CertifyPaymentEvidence) =>
        certifyFiatEvidence.withArbitratedFiatSentSigs(ps)

      case (FiatNotSentCertified(_, ps, Some(_)), certifyFiatEvidence: CertifyPaymentEvidence) =>
        certifyFiatEvidence.withArbitratedFiatNotSentSigs(ps)

      case (BtcBuyerFunded(_, th, ut), certifiedPayment: CertifiedPayment) =>
        certifiedPayment.withPayoutTx(th, ut)

      case (BtcSellerRefunded(_, th, ut), certifiedPayment: CertifiedPayment) =>
        certifiedPayment.withPayoutTx(th, ut)

      // cancel path

      // TODO FT-80, FT-81, FT-82

      // error

      case _ =>
        log.error(s"No transition for event: $event\nwith trade data: ${tradeData.getClass.getSimpleName}")
        tradeData
    }

  // send start events for each state

  // common path

  def startCreate(so: BtcBuyOffer): Unit = {
    context.parent ! BtcBuyerCreatedOffer(so.id, so)
  }

  def startTaken(to: TakenOffer): Unit = {
    startCreate(to.btcBuyOffer)
    context.parent ! BtcSellerTookOffer(to.id, to.btcSeller, to.btcSellerOpenTxSigs, to.btcSellerFundPayoutTxo, to.cipherPaymentDetails)
  }

  def startSigned(sto: SignedTakenOffer): Unit = {
    startTaken(sto.takenOffer)
    context.parent ! BtcBuyerSignedOffer(sto.id, sto.btcSeller.id, sto.btcBuyerOpenTxSigs, sto.btcBuyerPayoutTxSigs)
  }

  def startOpened(ot: OpenedTrade): Unit = {
    startSigned(ot.signedTakenOffer)
    context.parent ! BtcSellerOpenedEscrow(ot.id, ot.openTxHash, ot.openTxUpdateTime)
  }

  def startFunded(ft: FundedTrade): Unit = {
    startOpened(ft.openedTrade)
    context.parent ! BtcSellerFundedEscrow(ft.id, ft.fundTxHash, ft.fundTxUpdateTime, ft.paymentDetailsKey)
  }

  // happy path

  def startBtcBuyerTraded(st: SettledTrade): Unit = {
    startFunded(st.fundedTrade)
    context.parent ! BtcBuyerReceivedPayout(st.id, st.payoutTxHash, st.payoutTxUpdateTime)
  }

  def startBtcSellerTraded(st: SettledTrade): Unit = {
    startFunded(st.fundedTrade)
    context.parent ! BtcSellerReceivedPayout(st.id, st.payoutTxHash, st.payoutTxUpdateTime)
  }

  // unhappy path

  def startCertPaymentReqd(cfe: CertifyPaymentEvidence): Unit = {
    startFunded(cfe.fundedTrade)
    context.parent ! CertifyPaymentRequested(cfe.id)
  }

  def startFiatSentCertd(cfd: CertifiedPayment): Unit = {
    startCertPaymentReqd(cfd.certifyPaymentEvidence)
    context.parent ! FiatSentCertified(cfd.id, cfd.arbitratorPayoutTxSigs)
  }

  def startFiatNotSentCertd(cfd: CertifiedPayment): Unit = {
    startCertPaymentReqd(cfd.certifyPaymentEvidence)
    context.parent ! FiatNotSentCertified(cfd.id, cfd.arbitratorPayoutTxSigs)
  }

  def startBtcBuyerFunded(cst: CertifiedSettledTrade): Unit = {
    startFiatSentCertd(cst.certifiedPayment)
    context.parent ! BtcBuyerFunded(cst.id, cst.payoutTxHash, cst.payoutTxUpdateTime)
  }

  def startBtcSellerRefunded(cst: CertifiedSettledTrade): Unit = {
    startFiatNotSentCertd(cst.certifiedPayment)
    context.parent ! BtcSellerRefunded(cst.id, cst.payoutTxHash, cst.payoutTxUpdateTime)
  }

  // http flow

  def connectionFlow(url: URL) = Http().outgoingConnection(host = url.getHost, port = url.getPort)

  // http request and handler

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

  def outputsEqual(tx1: Tx, tx2: Transaction, from: Int, until: Int): Boolean = {
    tx1.outputs.slice(from, until).toSet == tx2.getOutputs.slice(from, until).toSet
  }

  def outputsEqual(tx1: Tx, tx2: Transaction): Boolean = {
    val until: Int = Seq(tx1.outputs.length - 1, tx2.getOutputs.length - 1).min
    outputsEqual(tx1, tx2, 0, until)
  }

  def paymentDetailsKey(tx: Transaction): Option[Array[Byte]] = {
    Try {
      val outputs = tx.getOutputs
      val lastOutputScript = outputs.get(outputs.size - 1).getScriptPubKey
      lastOutputScript.getChunks.get(1).data
    }.toOption
  }
}
