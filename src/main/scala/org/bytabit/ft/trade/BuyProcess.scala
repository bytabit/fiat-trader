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
import org.bytabit.ft.trade.BuyProcess.{ReceiveFiat, RequestCertifyPayment, Start, TakeSellOffer}
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.wallet.TradeWalletManager.SetTransactionMemo
import org.bytabit.ft.wallet.WalletManager.{EscrowTransactionUpdated, TxBroadcast}
import org.bytabit.ft.wallet.{EscrowWalletManager, TradeWalletManager, WalletManager}
import org.joda.time.DateTime

import scala.language.postfixOps

object BuyProcess {

  // commands

  sealed trait Command {
    val url: URL
    val id: UUID
  }

  final case class Start(url: URL, id: UUID) extends Command

  final case class TakeSellOffer(url: URL, id: UUID, paymentDetails: String) extends Command

  final case class ReceiveFiat(url: URL, id: UUID) extends Command

  final case class RequestCertifyPayment(url: URL, id: UUID, evidence: Option[Array[Byte]] = None) extends Command

}

case class BuyProcess(sellOffer: SellOffer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) extends TradeProcess {

  override val id = sellOffer.id

  override val log = Logging(context.system, this)

  startWith(CREATED, sellOffer)

  // common path

  when(CREATED) {

    case Event(Start, so: SellOffer) =>
      startCreate(so)
      stay()

    case Event(sco: SellerCreatedOffer, so: SellOffer) if sco.posted.isDefined =>
      goto(CREATED) applying sco andThen { case uso: SellOffer =>
        context.parent ! sco
      }

    case Event(sco: SellerCanceledOffer, _) if sco.posted.isDefined =>
      goto(CANCELED) andThen { uso =>
        context.parent ! sco
      }

    case Event(tso: TakeSellOffer, so: SellOffer) =>
      tradeWalletMgrRef ! TradeWalletManager.TakeSellOffer(so, tso.paymentDetails)
      stay()

    case Event(WalletManager.SellOfferTaken(to), so: SellOffer) if to.paymentDetailsKey.isDefined =>

      if (to.amountOk) {
        val bto = BuyerTookOffer(to.id, to.buyer, to.buyerOpenTxSigs, to.buyerFundPayoutTxo, to.cipherPaymentDetails)
        val bsk = BuyerSetPaymentDetailsKey(to.id, to.paymentDetailsKey.get)
        stay applying bto applying bsk andThen {
          case to: TakenOffer =>
            postTradeEvent(to.url, bto, self)
        }
      } else {
        log.error(s"Insufficient btc amount to take offer ${so.id}")
        stay()
      }

    // my take offer was posted
    case Event(bto: BuyerTookOffer, to: TakenOffer) if to.buyer.id == bto.buyer.id && bto.posted.isDefined =>
      goto(TAKEN) applying bto andThen {
        case uto: TakenOffer =>
          context.parent ! bto
      }

    // someone else took the offer before mine was posted
    case Event(bto: BuyerTookOffer, to: TakenOffer) if to.buyer.id != bto.buyer.id && bto.posted.isDefined =>
      stay()

    // seller signed someone else's take before mine was posted, cancel for us
    case Event(sso: SellerSignedOffer, to: TakenOffer) if to.buyer.id != sso.buyerId && sso.posted.isDefined =>
      goto(CANCELED) andThen { case uto: TakenOffer =>
        context.parent ! SellerCanceledOffer(uto.id, sso.posted)
      }

    // someone else took the offer
    case Event(bto: BuyerTookOffer, so: SellOffer) if bto.posted.isDefined =>
      stay()

    // seller signed someone else's take, cancel for us
    case Event(sso: SellerSignedOffer, so: SellOffer) if sso.posted.isDefined =>
      goto(CANCELED) andThen { case uso: SellOffer =>
        context.parent ! SellerCanceledOffer(uso.id, sso.posted)
      }
  }

  when(TAKEN) {
    case Event(Start, to: TakenOffer) =>
      startTaken(to)
      stay()

    // seller signed my take
    case Event(sso: SellerSignedOffer, to: TakenOffer) if to.buyer.id == sso.buyerId && sso.posted.isDefined =>
      goto(SIGNED) applying sso andThen {
        case sto: SignedTakenOffer =>
          escrowWalletMgrRef ! EscrowWalletManager.AddWatchAddress(sto.fullySignedOpenTx.escrowAddr, to.sellOffer.posted.get)
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(sto.fullySignedOpenTx)
          context.parent ! sso
      }

    // seller signed someone else's take, cancel for us
    case Event(sso: SellerSignedOffer, to: TakenOffer) if to.buyer.id != sso.buyerId && sso.posted.isDefined =>
      goto(CANCELED) andThen { case uto: TakenOffer =>
        context.parent ! SellerCanceledOffer(uto.id, sso.posted)
      }

    case e =>
      log.error(s"Received unexpected event in TAKEN: $e")
      stay()
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      startSigned(sto)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedOpenTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val boe = BuyerOpenedEscrow(sto.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(OPENED) applying boe andThen {
          case ot: OpenedTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Open Trade $id")
            context.parent ! boe
            tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(sto.unsignedFundTx)
        }
      }
      else
        stay()

    case Event(TxBroadcast(tx), sto: SignedTakenOffer) =>
      escrowWalletMgrRef ! EscrowWalletManager.BroadcastSignedTx(tx)
      stay()
  }

  when(OPENED) {
    case Event(Start, ot: OpenedTrade) =>
      startOpened(ot)
      stay()

    case Event(etu: EscrowTransactionUpdated, ot: OpenedTrade) =>
      if (outputsEqual(ot.signedTakenOffer.unsignedFundTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val bfe = BuyerFundedEscrow(ot.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime), ot.paymentDetailsKey)
        goto(FUNDED) applying bfe andThen {
          case usto: FundedTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Funded Trade $id")
            context.parent ! bfe
        }
      }
      else
        stay()

    case Event(TxBroadcast(tx), ot: OpenedTrade) =>
      escrowWalletMgrRef ! EscrowWalletManager.BroadcastSignedTx(tx)
      stay()
  }

  when(FUNDED) {
    case Event(Start, ft: FundedTrade) =>
      startFunded(ft)
      stay()

    case Event(e: ReceiveFiat, ft: FundedTrade) =>
      goto(FIAT_RCVD) andThen {
        case ft: FundedTrade =>
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(ft.sellerSignedPayoutTx, Some(ft.buyer.escrowPubKey))
          context.parent ! FiatReceived(ft.id)
      }

    case Event(rcf: RequestCertifyPayment, ft: FundedTrade) =>
      postTradeEvent(rcf.url, CertifyPaymentRequested(ft.id, rcf.evidence), self)
      stay()

    case Event(cdr: CertifyPaymentRequested, ft: FundedTrade) if cdr.posted.isDefined =>
      goto(CERT_PAYMENT_REQD) applying cdr andThen {
        case cfe: CertifyPaymentEvidence =>
          context.parent ! cdr
      }

    case Event(etu: EscrowTransactionUpdated, ft: FundedTrade) =>
      stay()
  }

  def startFiatRcvd(ft: FundedTrade) = {
    startFunded(ft)
    context.parent ! FiatReceived(ft.id)
  }

  when(FIAT_RCVD) {
    case Event(Start, ft: FundedTrade) =>
      startFiatRcvd(ft)
      stay()

    case Event(etu: EscrowTransactionUpdated, ft: FundedTrade) =>
      if (outputsEqual(ft.unsignedPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val brp = BuyerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(TRADED) applying brp andThen {
          case st: SettledTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Payout Trade $id")
            context.parent ! brp
            escrowWalletMgrRef ! EscrowWalletManager.RemoveWatchAddress(st.escrowAddress)
        }
      }
      else
        stay()

    case Event(TxBroadcast(tx), ft: FundedTrade) =>
      escrowWalletMgrRef ! EscrowWalletManager.BroadcastSignedTx(tx)
      stay()

  }

  // happy path

  override def startSellerTraded(st: SettledTrade) = {
    startBuyerTraded(st)
  }

  when(TRADED) {
    case Event(Start, st: SettledTrade) =>
      startSellerTraded(st)
      stay()

    case Event(etu: EscrowTransactionUpdated, st: SettledTrade) =>
      stay()

    case e =>
      log.error(s"Received event after being traded: $e")
      stay()
  }

  // unhappy path

  when(CERT_PAYMENT_REQD) {

    case Event(Start, sto: CertifyPaymentEvidence) =>
      startCertPaymentReqd(sto)
      stay()

    case Event(fsc: FiatSentCertified, cfe: CertifyPaymentEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd: CertifiedPayment =>
          context.parent ! fsc
      }

    case Event(fnsc: FiatNotSentCertified, cfe: CertifyPaymentEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd: CertifiedPayment =>
          context.parent ! fnsc
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(cfd.arbitratorSignedFiatNotSentPayoutTx, Some(cfd.buyer.escrowPubKey))
      }

    case Event(etu: EscrowTransactionUpdated, cfe: CertifyPaymentEvidence) =>
      // ignore tx updates until decision event from arbitrator received
      stay()
  }

  when(FIAT_SENT_CERTD) {
    case Event(Start, cfd: CertifiedPayment) =>
      startFiatSentCertd(cfd)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedPayment) =>
      if (outputsEqual(cfd.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val sf = SellerFunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(SELLER_FUNDED) applying sf andThen {
          case cst: CertifiedSettledTrade =>
            context.parent ! sf
            escrowWalletMgrRef ! EscrowWalletManager.RemoveWatchAddress(cfd.escrowAddress)
        }
      }
      else
        stay()

    case e =>
      log.error(s"Received event after fiat sent certified by arbitrator: $e")
      stay()
  }

  when(FIAT_NOT_SENT_CERTD) {
    case Event(Start, cfd: CertifiedPayment) =>
      startFiatNotSentCertd(cfd)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedPayment) =>
      if (outputsEqual(cfd.unsignedFiatNotSentPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val br = BuyerRefunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(BUYER_REFUNDED) applying br andThen {
          case cst: CertifiedSettledTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Arbitrated Refund Trade $id")
            context.parent ! br
            escrowWalletMgrRef ! EscrowWalletManager.RemoveWatchAddress(cfd.escrowAddress)
        }
      }
      else
        stay()

    case Event(TxBroadcast(tx), cfd: CertifiedPayment) =>
      escrowWalletMgrRef ! EscrowWalletManager.BroadcastSignedTx(tx)
      stay()

    case e =>
      log.error(s"Received event after fiat not sent certified by arbitrator: $e")
      stay()
  }

  when(SELLER_FUNDED) {
    case Event(Start, cst: CertifiedSettledTrade) =>
      startSellerFunded(cst)
      stay()

    case Event(etu: EscrowTransactionUpdated, cst: CertifiedSettledTrade) =>
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

    case Event(etu: EscrowTransactionUpdated, cst: CertifiedSettledTrade) =>
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



