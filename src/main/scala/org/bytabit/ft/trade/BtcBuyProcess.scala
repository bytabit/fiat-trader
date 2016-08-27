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
import org.bytabit.ft.trade.BtcBuyProcess.{CancelBtcBuyOffer, RequestCertifyPayment, SendFiat, Start}
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade.model.{BtcBuyOffer, SignedTakenOffer, TakenOffer, _}
import org.bytabit.ft.wallet.EscrowWalletManager.{AddWatchAddress, RemoveWatchAddress}
import org.bytabit.ft.wallet.TradeWalletManager.SetTransactionMemo
import org.bytabit.ft.wallet.WalletManager.{EscrowTransactionUpdated, InsufficientBtc, TxBroadcast}
import org.bytabit.ft.wallet.{EscrowWalletManager, TradeWalletManager, WalletManager}
import org.joda.time.DateTime

import scala.language.postfixOps

object BtcBuyProcess {

  // commands

  sealed trait Command {
    val url: URL
    val id: UUID
  }

  final case class Start(url: URL, id: UUID) extends Command

  final case class AddBtcBuyOffer(url: URL, id: UUID, offer: Offer) extends Command

  final case class CancelBtcBuyOffer(url: URL, id: UUID) extends Command

  final case class SendFiat(url: URL, id: UUID, reference: Option[String] = None) extends Command

  final case class RequestCertifyPayment(url: URL, id: UUID, evidence: Option[Array[Byte]] = None) extends Command

}

case class BtcBuyProcess(offer: Offer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) extends TradeProcess {

  override val id = offer.id

  override val log = Logging(context.system, this)

  startWith(ADDED, offer)

  when(ADDED) {

    case Event(Start, o: Offer) =>
      tradeWalletMgrRef ! TradeWalletManager.CreateBtcBuyOffer(o)
      stay()

    case Event(Start, so: BtcBuyOffer) =>
      postTradeEvent(so.url, BtcBuyerCreatedOffer(so.id, so), self)
      stay()

    case Event(WalletManager.BtcBuyOfferCreated(so: BtcBuyOffer), o: Offer) =>

      if (so.amountOK) {
        postTradeEvent(so.url, BtcBuyerCreatedOffer(so.id, so), self)
      } else {
        log.error(s"Insufficient btc amount to create btc buy offer ${o.id}")
      }
      stay()

    // posted created offer
    case Event(sco: BtcBuyerCreatedOffer, o: Offer) if sco.posted.isDefined =>
      goto(CREATED) applying sco andThen { case uso: BtcBuyOffer =>
        context.parent ! LocalBtcBuyerCreatedOffer(uso.id, uso, sco.posted)
      }

    case Event(we: InsufficientBtc, o: Offer) =>
      context.parent ! we
      stay()
  }

  override def startCreate(so: BtcBuyOffer) = {
    context.parent ! LocalBtcBuyerCreatedOffer(so.id, so)
  }

  when(CREATED) {

    case Event(Start, so: BtcBuyOffer) =>
      startCreate(so)
      stay()

    case Event(cso: CancelBtcBuyOffer, bo: BtcBuyOffer) =>
      postTradeEvent(bo.url, BtcBuyerCanceledOffer(bo.id), self)
      stay()

    case Event(soc: BtcBuyerCanceledOffer, bo: BtcBuyOffer) if soc.posted.isDefined =>
      goto(CANCELED) andThen { uso =>
        context.parent ! soc
      }

    case Event(bto: BtcSellerTookOffer, bo: BtcBuyOffer) if bto.posted.isDefined =>
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
      postTradeEvent(sto.url, BtcBuyerSignedOffer(sto.id, to.btcSeller.id, sto.btcBuyerOpenTxSigs, sto.btcBuyerPayoutTxSigs), self)
      stay()

    case Event(sso: BtcBuyerSignedOffer, to: TakenOffer) if sso.posted.isDefined =>
      goto(SIGNED) applying sso andThen {
        case sto: SignedTakenOffer =>
          escrowWalletMgrRef ! AddWatchAddress(sto.fullySignedOpenTx.escrowAddr, to.btcBuyOffer.posted.get)
          context.parent ! sso
      }
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      startSigned(sto)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.fullySignedOpenTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val boe = BtcSellerOpenedEscrow(sto.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(OPENED) applying boe andThen {
          case ot: OpenedTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Open Trade $id")
            context.parent ! boe
        }
      }
      else
        stay()
  }

  when(OPENED) {
    case Event(Start, ot: OpenedTrade) =>
      startOpened(ot)
      stay()

    case Event(etu: EscrowTransactionUpdated, ot: OpenedTrade) =>
      if (outputsEqual(ot.signedTakenOffer.unsignedFundTx, etu.tx, 0, etu.tx.getOutputs.size() - 1) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val bfe = BtcSellerFundedEscrow(ot.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime),
          paymentDetailsKey(etu.tx))
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
      stay()

    case Event(sf: SendFiat, ft: FundedTrade) =>
      postTradeEvent(sf.url, BtcBuyerFiatSent(sf.id, sf.reference), self)
      stay()

    case Event(fs: BtcBuyerFiatSent, ft: FundedTrade) =>
      goto(FIAT_SENT) applying fs andThen {
        case ft: FundedTrade =>
          context.parent ! fs
      }

    case Event(cdr: CertifyPaymentRequested, ft: FundedTrade) if cdr.posted.isDefined =>
      goto(CERT_PAYMENT_REQD) applying cdr andThen {
        case cfe: CertifyPaymentEvidence =>
          context.parent ! cdr
      }

    case Event(etu: EscrowTransactionUpdated, ft: FundedTrade) =>
      if (outputsEqual(ft.openedTrade.signedTakenOffer.btcBuyerSignedPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val brp = BtcBuyerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(TRADED) applying brp andThen {
          case st: SettledTrade =>
            context.parent ! brp
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Payout Trade $id")
            escrowWalletMgrRef ! RemoveWatchAddress(ft.escrowAddress)
        }
      }
      else
        stay()
  }

  when(FIAT_SENT) {

    case Event(Start, ft: FundedTrade) =>
      startFiatSent(ft)
      stay()

    case Event(rcf: RequestCertifyPayment, ft: FundedTrade) =>
      postTradeEvent(rcf.url, CertifyPaymentRequested(ft.id, rcf.evidence), self)
      stay()

    case Event(cdr: CertifyPaymentRequested, ft: FundedTrade) if cdr.posted.isDefined =>
      goto(CERT_PAYMENT_REQD) applying cdr andThen {
        case cfe: CertifyPaymentEvidence =>
          context.parent ! cdr
      }

    case Event(etu: EscrowTransactionUpdated, ft: FundedTrade) =>
      if (outputsEqual(ft.openedTrade.signedTakenOffer.btcBuyerSignedPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val brp = BtcBuyerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(TRADED) applying brp andThen {
          case st: SettledTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Payout Trade $id")
            context.parent ! brp
            escrowWalletMgrRef ! RemoveWatchAddress(ft.escrowAddress)
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
      startBtcBuyerTraded(st)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SettledTrade) =>
      stay()

    case e =>
      log.warning(s"Received event after being traded: ${e.getClass}")
      stay()
  }

  // unhappy path

  when(CERT_PAYMENT_REQD) {

    case Event(Start, cfe: CertifyPaymentEvidence) =>
      startCertPaymentReqd(cfe)
      stay()

    case Event(fsc: FiatSentCertified, cfe: CertifyPaymentEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd: CertifiedPayment =>
          context.parent ! fsc
          tradeWalletMgrRef ! TradeWalletManager.BroadcastTx(cfd.arbitratorSignedFiatSentPayoutTx, Some(cfd.btcBuyer.escrowPubKey))
      }

    case Event(fnsc: FiatNotSentCertified, cfe: CertifyPaymentEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd: CertifiedPayment =>
          context.parent ! fnsc
      }

    case Event(etu: EscrowTransactionUpdated, cfe: CertifyPaymentEvidence) =>
      // ignore tx updates until decision event from arbitrator received
      stay()
  }

  when(FIAT_SENT_CERTD) {
    case Event(Start, cfs: CertifiedPayment) =>
      startFiatSentCertd(cfs)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedPayment) =>
      if (outputsEqual(cfd.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val sf = BtcBuyerFunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(BTCBUYER_FUNDED) applying sf andThen {
          case cst: CertifiedSettledTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Arbitrated Payout Trade $id")
            context.parent ! sf
            escrowWalletMgrRef ! RemoveWatchAddress(cfd.escrowAddress)
        }
      }
      else
        stay()

    case Event(TxBroadcast(tx), cfd: CertifiedPayment) =>
      escrowWalletMgrRef ! EscrowWalletManager.BroadcastSignedTx(tx)
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
            context.parent ! br
            escrowWalletMgrRef ! RemoveWatchAddress(cfd.escrowAddress)
        }
      }
      else
        stay()
  }

  when(BTCBUYER_FUNDED) {
    case Event(Start, cst: CertifiedSettledTrade) =>
      startBtcBuyerFunded(cst)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedPayment) =>
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

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedPayment) =>
      //log.warning("Received escrow tx update after btc seller refunded")
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