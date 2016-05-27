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

import akka.actor._
import akka.event.Logging
import org.bitcoinj.core.TransactionConfidence.ConfidenceType
import org.bytabit.ft.trade.SellProcess.{CancelSellOffer, RequestCertifyDelivery, SendFiat, Start}
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade.model.{SellOffer, SignedTakenOffer, TakenOffer, _}
import org.bytabit.ft.wallet.EscrowWalletManager.{AddWatchEscrowAddress, RemoveWatchEscrowAddress}
import org.bytabit.ft.wallet.WalletManager.{EscrowTransactionUpdated, TxBroadcast}
import org.bytabit.ft.wallet.{EscrowWalletManager, TradeWalletManager, WalletManager}
import org.joda.time.DateTime

import scala.language.postfixOps

object SellProcess {

  // commands

  sealed trait Command {
    val url: URL
    val id: UUID
  }

  final case class Start(url: URL, id: UUID) extends Command

  final case class AddSellOffer(url: URL, id: UUID, offer: Offer) extends Command

  final case class CancelSellOffer(url: URL, id: UUID) extends Command

  final case class SendFiat(url: URL, id: UUID) extends Command

  final case class RequestCertifyDelivery(url: URL, id: UUID, evidence: Option[Array[Byte]] = None) extends Command

}

case class SellProcess(offer: Offer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) extends TradeProcess {

  override val id = offer.id

  override val log = Logging(context.system, this)

  startWith(ADDED, offer)

  when(ADDED) {

    case Event(Start, o: Offer) =>
      tradeWalletMgrRef ! TradeWalletManager.CreateSellOffer(o)
      stay()

    case Event(Start, so: SellOffer) =>
      postTradeEvent(so.url, SellerCreatedOffer(so.id, so), self)
      stay()

    case Event(WalletManager.SellOfferCreated(so: SellOffer), o: Offer) =>

      if (so.amountOK) {
        postTradeEvent(so.url, SellerCreatedOffer(so.id, so), self)
      } else {
        log.error(s"Insufficient btc amount to create sell offer ${o.id}")
      }
      stay()

    // posted created offer
    case Event(sco: SellerCreatedOffer, o: Offer) if sco.posted.isDefined =>
      goto(CREATED) applying sco andThen { case uso: SellOffer =>
        context.parent ! LocalSellerCreatedOffer(uso.id, uso, sco.posted)
      }
  }

  override def startCreate(so: SellOffer) = {
    context.parent ! LocalSellerCreatedOffer(so.id, so)
  }

  when(CREATED) {

    case Event(Start, so: SellOffer) =>
      startCreate(so)
      stay()

    case Event(cso: CancelSellOffer, so: SellOffer) =>
      postTradeEvent(so.url, SellerCanceledOffer(so.id), self)
      stay()

    case Event(soc: SellerCanceledOffer, so: SellOffer) if soc.posted.isDefined =>
      goto(CANCELED) andThen { uso =>
        context.parent ! soc
      }

    case Event(bto: BuyerTookOffer, so: SellOffer) if bto.posted.isDefined =>
      goto(TAKEN) applying bto andThen {
        case to: TakenOffer =>
          context.parent ! bto
          tradeWalletMgrRef ! TradeWalletManager.SignTakenOffer(to)
      }
  }

  when(TAKEN) {
    case Event(Start, to: TakenOffer) =>
      startTaken(to)
      stay()

    case Event(WalletManager.TakenOfferSigned(sto), to: TakenOffer) =>
      postTradeEvent(sto.url, SellerSignedOffer(sto.id, to.buyer.id, sto.sellerOpenTxSigs, sto.sellerPayoutTxSigs), self)
      stay()

    case Event(sso: SellerSignedOffer, to: TakenOffer) if sso.posted.isDefined =>
      goto(SIGNED) applying sso andThen {
        case sto: SignedTakenOffer =>
          escrowWalletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
          context.parent ! sso
      }
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      startSigned(sto)
      escrowWalletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.fullySignedOpenTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val boe = BuyerOpenedEscrow(sto.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(OPENED) applying boe andThen {
          case ot: OpenedTrade =>
            context.parent ! boe
        }
      }
      else
        stay()
  }

  when(OPENED) {
    case Event(Start, ot: OpenedTrade) =>
      startOpened(ot)
      escrowWalletMgrRef ! AddWatchEscrowAddress(ot.escrowAddress)
      stay()

    case Event(etu: EscrowTransactionUpdated, ot: OpenedTrade) =>
      if (outputsEqual(ot.signedTakenOffer.unsignedFundTx, etu.tx, 0, etu.tx.getOutputs.size() - 1) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val bfe = BuyerFundedEscrow(ot.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime),
          fiatDeliveryDetailsKey(etu.tx))
        goto(FUNDED) applying bfe andThen {
          case ft: FundedTrade =>
            context.parent ! bfe
        }
      }
      else
        stay()
  }

  when(FUNDED) {

    case Event(Start, ft: FundedTrade) =>
      startFunded(ft)
      escrowWalletMgrRef ! AddWatchEscrowAddress(ft.escrowAddress)
      stay()

    case Event(e: SendFiat, ft: FundedTrade) =>
      goto(FIAT_SENT) andThen {
        case ft: FundedTrade =>
          context.parent ! FiatSent(ft.id)
      }

    case Event(cdr: CertifyDeliveryRequested, ft: FundedTrade) if cdr.posted.isDefined =>
      goto(CERT_DELIVERY_REQD) applying cdr andThen {
        case cfe: CertifyFiatEvidence =>
          context.parent ! cdr
      }

    case Event(etu: EscrowTransactionUpdated, ft: FundedTrade) =>
      if (outputsEqual(ft.openedTrade.signedTakenOffer.sellerSignedPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val srp = SellerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(TRADED) applying srp andThen {
          case st: SettledTrade =>
            context.parent ! srp
            escrowWalletMgrRef ! RemoveWatchEscrowAddress(ft.escrowAddress)
        }
      }
      else
        stay()
  }

  def startFiatSent(ft: FundedTrade) = {
    startFunded(ft)
    context.parent ! FiatSent(ft.id)
  }

  when(FIAT_SENT) {

    case Event(Start, ft: FundedTrade) =>
      startFiatSent(ft)
      escrowWalletMgrRef ! AddWatchEscrowAddress(ft.escrowAddress)
      stay()

    case Event(rcf: RequestCertifyDelivery, ft: FundedTrade) =>
      postTradeEvent(rcf.url, CertifyDeliveryRequested(ft.id, rcf.evidence), self)
      stay()

    case Event(cdr: CertifyDeliveryRequested, ft: FundedTrade) if cdr.posted.isDefined =>
      goto(CERT_DELIVERY_REQD) applying cdr andThen {
        case cfe: CertifyFiatEvidence =>
          context.parent ! cdr
      }

    case Event(etu: EscrowTransactionUpdated, ft: FundedTrade) =>
      if (outputsEqual(ft.openedTrade.signedTakenOffer.sellerSignedPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val srp = SellerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(TRADED) applying srp andThen {
          case st: SettledTrade =>
            context.parent ! srp
            escrowWalletMgrRef ! RemoveWatchEscrowAddress(ft.escrowAddress)
        }
      }
      else
        stay()

    case e: Event =>
      log.error(s"unexpected event: $e")
      stay()
  }

  // happy path

  when(TRADED) {

    case Event(Start, st: SettledTrade) =>
      startSellerTraded(st)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SettledTrade) =>
      stay()

    case e =>
      log.error(s"Received event after being traded: $e")
      stay()
  }

  // unhappy path

  when(CERT_DELIVERY_REQD) {

    case Event(Start, cfe: CertifyFiatEvidence) =>
      startCertDeliveryReqd(cfe)
      escrowWalletMgrRef ! AddWatchEscrowAddress(cfe.escrowAddress)
      stay()

    case Event(fsc: FiatSentCertified, cfe: CertifyFiatEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fsc
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(cfd.arbitratorSignedFiatSentPayoutTx, Some(cfd.seller.escrowPubKey))
      }

    case Event(fnsc: FiatNotSentCertified, cfe: CertifyFiatEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fnsc
      }

    case Event(etu: EscrowTransactionUpdated, cfe: CertifyFiatEvidence) =>
      // ignore tx updates until decision event from arbitrator received
      stay()
  }

  when(FIAT_SENT_CERTD) {
    case Event(Start, cfs: CertifiedFiatDelivery) =>
      startFiatSentCertd(cfs)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      if (outputsEqual(cfd.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val sf = SellerFunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(SELLER_FUNDED) applying sf andThen {
          case cst: CertifiedSettledTrade =>
            context.parent ! sf
            escrowWalletMgrRef ! RemoveWatchEscrowAddress(cfd.escrowAddress)
        }
      }
      else
        stay()

    case Event(TxBroadcast(tx), cfd: CertifiedFiatDelivery) =>
      escrowWalletMgrRef ! EscrowWalletManager.BroadcastSignedTx(tx)
      stay()

    case e =>
      log.error(s"Received event after fiat sent certified by arbitrator: $e")
      stay()
  }

  when(FIAT_NOT_SENT_CERTD) {
    case Event(Start, cfd: CertifiedFiatDelivery) =>
      startFiatNotSentCertd(cfd)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      if (outputsEqual(cfd.unsignedFiatNotSentPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val br = BuyerRefunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(BUYER_REFUNDED) applying br andThen {
          case cst: CertifiedSettledTrade =>
            context.parent ! br
            escrowWalletMgrRef ! RemoveWatchEscrowAddress(cfd.escrowAddress)
        }
      }
      else
        stay()
  }

  when(SELLER_FUNDED) {
    case Event(Start, cst: CertifiedSettledTrade) =>
      startSellerFunded(cst)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      //log.warning("Received escrow tx update after seller funded")
      stay()

    case e =>
      log.error(s"Received event after seller funded: $e")
      stay()
  }

  when(BUYER_REFUNDED) {
    case Event(Start, cst: CertifiedSettledTrade) =>
      startBuyerRefunded(cst)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      //log.warning("Received escrow tx update after buyer refunded")
      stay()

    case e =>
      log.error(s"Received event after buyer refunded: $e")
      stay()
  }

  // cancel path

  when(CANCELED) {
    case e =>
      log.error(s"Received event after being canceled: $e")
      stay()
  }

  initialize()
}