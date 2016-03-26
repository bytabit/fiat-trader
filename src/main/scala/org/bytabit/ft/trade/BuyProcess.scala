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
import org.bytabit.ft.trade.BuyProcess.{ReceiveFiat, RequestCertifyDelivery, Start, TakeSellOffer}
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.wallet.WalletManager
import org.bytabit.ft.wallet.WalletManager.{AddWatchEscrowAddress, BroadcastTx, EscrowTransactionUpdated, RemoveWatchEscrowAddress}
import org.joda.time.DateTime

import scala.language.postfixOps

object BuyProcess {

  // commands

  sealed trait Command

  case object Start extends Command

  final case class TakeSellOffer(arbitratorUrl: URL, id: UUID, fiatDeliveryDetails: String) extends Command

  final case class ReceiveFiat(arbitratorUrl: URL, id: UUID) extends Command

  final case class RequestCertifyDelivery(arbitratorUrl: URL, id: UUID, evidence: Option[Array[Byte]] = None) extends Command

}

class BuyProcess(sellOffer: SellOffer, walletMgrRef: ActorRef) extends TradeFSM {

  override val id = sellOffer.id

  override val log = Logging(context.system, this)

  startWith(CREATED, sellOffer)

  // common path

  when(CREATED) {

    case Event(Start, so: SellOffer) =>
      startCreate(so)
      stay()

    case Event(sco: SellerCreatedOffer, so: SellOffer) if sco.posted.isDefined =>
      context.parent ! sco
      stay()

    case Event(sco: SellerCanceledOffer, _) if sco.posted.isDefined =>
      goto(CANCELED) andThen { uso =>
        context.parent ! sco
      }

    case Event(tso: TakeSellOffer, so: SellOffer) =>
      walletMgrRef ! WalletManager.TakeSellOffer(so, tso.fiatDeliveryDetails)
      stay()

    case Event(WalletManager.SellOfferTaken(to), so: SellOffer) if to.fiatDeliveryDetailsKey.isDefined =>

      if (to.amountOk) {
        val bto = BuyerTookOffer(to.id, to.buyer, to.buyerOpenTxSigs, to.buyerFundPayoutTxo, to.cipherFiatDeliveryDetails)
        val bsk = BuyerSetFiatDeliveryDetailsKey(to.id, to.fiatDeliveryDetailsKey.get)
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
          walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
          walletMgrRef ! BroadcastTx(sto.fullySignedOpenTx)
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
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedOpenTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        val boe = BuyerOpenedEscrow(sto.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(OPENED) applying boe andThen {
          case ot: OpenedTrade =>
            context.parent ! boe
            walletMgrRef ! BroadcastTx(sto.unsignedFundTx)
        }
      }
      else
        stay()
  }

  when(OPENED) {
    case Event(Start, ot: OpenedTrade) =>
      startOpened(ot)
      walletMgrRef ! AddWatchEscrowAddress(ot.escrowAddress)
      stay()

    case Event(etu: EscrowTransactionUpdated, ot: OpenedTrade) =>
      if (outputsEqual(ot.signedTakenOffer.unsignedFundTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        val bfe = BuyerFundedEscrow(ot.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime), ot.fiatDeliveryDetailsKey)
        goto(FUNDED) applying bfe andThen {
          case usto: FundedTrade =>
            context.parent ! bfe
        }
      }
      else
        stay()
  }

  when(FUNDED) {
    case Event(Start, ft: FundedTrade) =>
      startFunded(ft)
      walletMgrRef ! AddWatchEscrowAddress(ft.escrowAddress)
      stay()

    case Event(e: ReceiveFiat, ft: FundedTrade) =>
      goto(FIAT_RCVD) andThen {
        case ft: FundedTrade =>
          walletMgrRef ! BroadcastTx(ft.sellerSignedPayoutTx, Some(ft.buyer.escrowPubKey))
          context.parent ! FiatReceived(ft.id)
      }

    case Event(rcf: RequestCertifyDelivery, ft: FundedTrade) =>
      postTradeEvent(rcf.arbitratorUrl, CertifyDeliveryRequested(ft.id, rcf.evidence), self)
      stay()

    case Event(cdr: CertifyDeliveryRequested, ft: FundedTrade) if cdr.posted.isDefined =>
      goto(CERT_DELIVERY_REQD) applying cdr andThen {
        case cfe: CertifyFiatEvidence =>
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
      walletMgrRef ! AddWatchEscrowAddress(ft.escrowAddress)
      stay()

    case Event(etu: EscrowTransactionUpdated, ft: FundedTrade) =>
      if (outputsEqual(ft.unsignedPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        val brp = BuyerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(TRADED) applying brp andThen {
          case st: SettledTrade =>
            context.parent ! brp
            walletMgrRef ! RemoveWatchEscrowAddress(st.escrowAddress)
        }
      }
      else
        stay()
  }

  // happy path

  override def startTraded(st: SettledTrade) = {
    super.startTraded(st)
    context.parent ! BuyerReceivedPayout(st.id, st.payoutTxHash, st.payoutTxUpdateTime)
  }

  when(TRADED) {
    case Event(Start, st: SettledTrade) =>
      startTraded(st)
      stay()

    case Event(etu: EscrowTransactionUpdated, st: SettledTrade) =>
      stay()

    case e =>
      log.error(s"Received event after being traded: $e")
      stay()
  }

  // unhappy path

  when(CERT_DELIVERY_REQD) {

    case Event(Start, sto: CertifyFiatEvidence) =>
      startCertDeliveryReqd(sto)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(fsc: FiatSentCertified, cfe: CertifyFiatEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fsc
      }

    case Event(fnsc: FiatNotSentCertified, cfe: CertifyFiatEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fnsc
          walletMgrRef ! WalletManager.BroadcastTx(cfd.arbitratorSignedFiatNotSentPayoutTx, Some(cfd.buyer.escrowPubKey))
      }

    case Event(etu: EscrowTransactionUpdated, cfe: CertifyFiatEvidence) =>
      // ignore tx updates until decision event from arbitrator received
      stay()
  }

  when(FIAT_SENT_CERTD) {
    case Event(Start, cfd: CertifiedFiatDelivery) =>
      startFiatSentCertd(cfd)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      if (outputsEqual(cfd.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        val sf = SellerFunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(SELLER_FUNDED) applying sf andThen {
          case cst: CertifiedSettledTrade =>
            context.parent ! sf
            walletMgrRef ! RemoveWatchEscrowAddress(cfd.escrowAddress)
        }
      }
      else
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
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        val br = BuyerRefunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(BUYER_REFUNDED) applying br andThen {
          case cst: CertifiedSettledTrade =>
            context.parent ! br
            walletMgrRef ! RemoveWatchEscrowAddress(cfd.escrowAddress)
        }
      }
      else
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



