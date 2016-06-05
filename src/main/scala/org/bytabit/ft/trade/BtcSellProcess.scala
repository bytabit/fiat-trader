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
import org.bytabit.ft.trade.BtcSellProcess.{ReceiveFiat, RequestCertifyPayment, Start, TakeBtcBuyOffer}
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.wallet.TradeWalletManager.SetTransactionMemo
import org.bytabit.ft.wallet.WalletManager.{EscrowTransactionUpdated, TxBroadcast}
import org.bytabit.ft.wallet.{EscrowWalletManager, TradeWalletManager, WalletManager}
import org.joda.time.DateTime

import scala.language.postfixOps

object BtcSellProcess {

  // commands

  sealed trait Command {
    val url: URL
    val id: UUID
  }

  final case class Start(url: URL, id: UUID) extends Command

  final case class TakeBtcBuyOffer(url: URL, id: UUID, paymentDetails: String) extends Command

  final case class ReceiveFiat(url: URL, id: UUID) extends Command

  final case class RequestCertifyPayment(url: URL, id: UUID, evidence: Option[Array[Byte]] = None) extends Command

}

case class BtcSellProcess(btcBuyOffer: BtcBuyOffer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) extends TradeProcess {

  override val id = btcBuyOffer.id

  override val log = Logging(context.system, this)

  startWith(CREATED, btcBuyOffer)

  // common path

  when(CREATED) {

    case Event(Start, so: BtcBuyOffer) =>
      startCreate(so)
      stay()

    case Event(sco: BtcBuyerCreatedOffer, so: BtcBuyOffer) if sco.posted.isDefined =>
      goto(CREATED) applying sco andThen { case uso: BtcBuyOffer =>
        context.parent ! sco
      }

    case Event(sco: BtcBuyerCanceledOffer, _) if sco.posted.isDefined =>
      goto(CANCELED) andThen { uso =>
        context.parent ! sco
      }

    case Event(tso: TakeBtcBuyOffer, so: BtcBuyOffer) =>
      tradeWalletMgrRef ! TradeWalletManager.TakeBtcBuyOffer(so, tso.paymentDetails)
      stay()

    case Event(WalletManager.BtcBuyOfferTaken(to), so: BtcBuyOffer) if to.paymentDetailsKey.isDefined =>

      if (to.amountOk) {
        val sto = BtcSellerTookOffer(to.id, to.btcSeller, to.btcSellerOpenTxSigs, to.btcSellerFundPayoutTxo, to.cipherPaymentDetails)
        val ssk = BtcSellerSetPaymentDetailsKey(to.id, to.paymentDetailsKey.get)
        stay applying sto applying ssk andThen {
          case to: TakenOffer =>
            postTradeEvent(to.url, sto, self)
        }
      } else {
        log.error(s"Insufficient btc amount to take offer ${so.id}")
        stay()
      }

    // my take offer was posted
    case Event(bto: BtcSellerTookOffer, to: TakenOffer) if to.btcSeller.id == bto.btcSeller.id && bto.posted.isDefined =>
      goto(TAKEN) applying bto andThen {
        case uto: TakenOffer =>
          context.parent ! bto
      }

    // someone else took the offer before mine was posted
    case Event(bto: BtcSellerTookOffer, to: TakenOffer) if to.btcSeller.id != bto.btcSeller.id && bto.posted.isDefined =>
      stay()

    // btcBuyer signed someone else's take before mine was posted, cancel for us
    case Event(sso: BtcBuyerSignedOffer, to: TakenOffer) if to.btcSeller.id != sso.btcBuyerId && sso.posted.isDefined =>
      goto(CANCELED) andThen { case uto: TakenOffer =>
        context.parent ! BtcBuyerCanceledOffer(uto.id, sso.posted)
      }

    // someone else took the offer
    case Event(bto: BtcSellerTookOffer, so: BtcBuyOffer) if bto.posted.isDefined =>
      stay()

    // btcBuyer signed someone else's take, cancel for us
    case Event(sso: BtcBuyerSignedOffer, so: BtcBuyOffer) if sso.posted.isDefined =>
      goto(CANCELED) andThen { case uso: BtcBuyOffer =>
        context.parent ! BtcBuyerCanceledOffer(uso.id, sso.posted)
      }
  }

  when(TAKEN) {
    case Event(Start, to: TakenOffer) =>
      startTaken(to)
      stay()

    // btcBuyer signed my take
    case Event(bso: BtcBuyerSignedOffer, to: TakenOffer) if to.btcSeller.id == bso.btcBuyerId && bso.posted.isDefined =>
      goto(SIGNED) applying bso andThen {
        case sto: SignedTakenOffer =>
          escrowWalletMgrRef ! EscrowWalletManager.AddWatchAddress(sto.fullySignedOpenTx.escrowAddr, to.btcBuyOffer.posted.get)
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(sto.fullySignedOpenTx)
          context.parent ! bso
      }

    // btcBuyer signed someone else's take, cancel for us
    case Event(sso: BtcBuyerSignedOffer, to: TakenOffer) if to.btcSeller.id != sso.btcBuyerId && sso.posted.isDefined =>
      goto(CANCELED) andThen { case uto: TakenOffer =>
        context.parent ! BtcBuyerCanceledOffer(uto.id, sso.posted)
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
        val boe = BtcSellerOpenedEscrow(sto.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
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
        val bfe = BtcSellerFundedEscrow(ot.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime), ot.paymentDetailsKey)
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
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(ft.btcBuyerSignedPayoutTx, Some(ft.btcSeller.escrowPubKey))
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
        val brp = BtcSellerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
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

  override def startBtcBuyerTraded(st: SettledTrade) = {
    startBtcSellerTraded(st)
  }

  when(TRADED) {
    case Event(Start, st: SettledTrade) =>
      startBtcBuyerTraded(st)
      stay()

    case Event(etu: EscrowTransactionUpdated, st: SettledTrade) =>
      stay()

    case e =>
      log.warning(s"Received event after being traded: ${e.getClass}")
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
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(cfd.arbitratorSignedFiatNotSentPayoutTx, Some(cfd.btcSeller.escrowPubKey))
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
        val sf = BtcBuyerFunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(BTCBUYER_FUNDED) applying sf andThen {
          case cst: CertifiedSettledTrade =>
            context.parent ! sf
            escrowWalletMgrRef ! EscrowWalletManager.RemoveWatchAddress(cfd.escrowAddress)
        }
      }
      else
        stay()

    case e =>
      log.warning(s"Received event after fiat sent certified by arbitrator: ${e.getClass}")
      stay()
  }

  when(FIAT_NOT_SENT_CERTD) {
    case Event(Start, cfd: CertifiedPayment) =>
      startFiatNotSentCertd(cfd)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedPayment) =>
      if (outputsEqual(cfd.unsignedFiatNotSentPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val br = BtcSellerRefunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(BTCSELLER_REFUNDED) applying br andThen {
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
      log.warning(s"Received event after fiat not sent certified by arbitrator: ${e.getClass}")
      stay()
  }

  when(BTCBUYER_FUNDED) {
    case Event(Start, cst: CertifiedSettledTrade) =>
      startBtcBuyerFunded(cst)
      stay()

    case Event(etu: EscrowTransactionUpdated, cst: CertifiedSettledTrade) =>
      //log.warning("Received escrow tx update after btcBuyer funded")
      stay()

    case e =>
      log.warning(s"Received event after btc buyer funded: ${e.getClass}")
      stay()
  }

  when(BTCSELLER_REFUNDED) {
    case Event(Start, cst: CertifiedSettledTrade) =>
      startBtcSellerRefunded(cst)
      stay()

    case Event(etu: EscrowTransactionUpdated, cst: CertifiedSettledTrade) =>
      //log.warning("Received escrow tx update after seller refunded")
      stay()

    case e =>
      log.warning(s"Received event after seller refunded: ${e.getClass}")
      stay()
  }

  // cancel path

  when(CANCELED) {
    case e =>
      log.warning(s"Received event after being canceled: ${e.getClass}")
      stay()
  }

  initialize()

}



