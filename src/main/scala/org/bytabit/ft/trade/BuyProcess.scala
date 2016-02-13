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

import scala.language.postfixOps

object BuyProcess {

  // commands

  sealed trait Command

  case object Start extends Command

  final case class TakeSellOffer(notaryUrl: URL, id: UUID, fiatDeliveryDetails: String) extends Command

  final case class ReceiveFiat(notaryUrl: URL, id: UUID) extends Command

  final case class RequestCertifyDelivery(notaryUrl: URL, id: UUID, evidence: Option[Array[Byte]] = None) extends Command

}

class BuyProcess(sellOffer: SellOffer, walletMgrRef: ActorRef) extends TradeFSM(sellOffer.id) {

  override val log = Logging(context.system, this)

  startWith(CREATED, sellOffer)

  when(CREATED) {

    case Event(Start, so: SellOffer) =>
      context.parent ! SellerCreatedOffer(so.id, so)
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

    case Event(WalletManager.SellOfferTaken(to), so: SellOffer) =>

      if (to.amountOk) {
        val bto = BuyerTookOffer(to.id, to.buyer, to.buyerOpenTxSigs, to.buyerFundPayoutTxo)
        stay applying bto andThen {
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
      context.parent ! SellerCreatedOffer(to.id, to.sellOffer)
      context.parent ! BuyerTookOffer(to.id, to.buyer, Seq(), Seq())
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
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! SellerSignedOffer(sto.id, sto.buyer.id, Seq(), Seq())
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedOpenTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(OPENED) andThen {
          case usto: SignedTakenOffer =>
            context.parent ! BuyerOpenedEscrow(sto.id, Seq())
            walletMgrRef ! BroadcastTx(sto.unsignedFundTx)
        }
      }
      else
        stay()
  }

  when(OPENED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerOpenedEscrow(sto.id, Seq())
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedFundTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(FUNDED) andThen {
          case usto: SignedTakenOffer =>
            context.parent ! BuyerFundedEscrow(sto.id)
        }
      }
      else
        stay()
  }

  when(FUNDED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerFundedEscrow(sto.id)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(e: ReceiveFiat, sto: SignedTakenOffer) =>
      goto(FIAT_RCVD) andThen {
        case usto: SignedTakenOffer =>
          walletMgrRef ! BroadcastTx(usto.sellerSignedPayoutTx, Some(usto.buyer.escrowPubKey))
          context.parent ! FiatReceived(usto.id)
      }

    case Event(rcf: RequestCertifyDelivery, sto: SignedTakenOffer) =>
      postTradeEvent(rcf.notaryUrl, CertifyDeliveryRequested(sto.id, rcf.evidence), self)
      stay()

    case Event(cdr: CertifyDeliveryRequested, sto: SignedTakenOffer) if cdr.posted.isDefined =>
      goto(CERT_DELIVERY_REQD) applying cdr andThen {
        case cfe: CertifyFiatEvidence =>
          context.parent ! cdr
      }

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      stay()
  }

  when(FIAT_RCVD) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! FiatReceived(sto.id)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(TRADED) andThen {
          case usto: SignedTakenOffer =>
            context.parent ! BuyerReceivedPayout(sto.id)
            walletMgrRef ! RemoveWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
        }
      }
      else
        stay()
  }

  when(CERT_DELIVERY_REQD) {

    case Event(Start, sto: CertifyFiatEvidence) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! CertifyDeliveryRequested(sto.id)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(fsc:FiatSentCertified, cfe:CertifyFiatEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd:CertifiedFiatDelivery =>
          context.parent ! fsc
      }

    case Event(fnsc:FiatNotSentCertified, cfe:CertifyFiatEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd:CertifiedFiatDelivery =>
          context.parent ! fnsc
          walletMgrRef ! WalletManager.BroadcastTx(cfd.notarySignedFiatNotSentPayoutTx, Some(cfd.buyer.escrowPubKey))
      }
  }

  when(TRADED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerReceivedPayout(sto.id)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      stay()

    case e =>
      log.error(s"Received event after being traded: $e")
      stay()
  }

  when(CANCELED) {
    case e =>
      log.error(s"Received event after being canceled: $e")
      stay()
  }

  when(FIAT_SENT_CERTD) {
    case Event(Start, cfs: CertifiedFiatDelivery) =>
      context.parent ! SellerCreatedOffer(cfs.id, cfs.sellOffer)
      context.parent ! FiatSentCertified(cfs.id, Seq())
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      if (outputsEqual(cfd.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(SELLER_FUNDED) andThen {
          case cfd:CertifiedFiatDelivery =>
            context.parent ! SellerFunded(cfd.id)
            walletMgrRef ! RemoveWatchEscrowAddress(cfd.fullySignedOpenTx.escrowAddr)
        }
      }
      else
        stay()

    case e =>
      log.error(s"Received event after fiat sent certified by notary: $e")
      stay()
  }

  when(FIAT_NOT_SENT_CERTD) {
    case Event(Start, cfd: CertifiedFiatDelivery) =>
      context.parent ! SellerCreatedOffer(cfd.id, cfd.sellOffer)
      context.parent ! FiatNotSentCertified(cfd.id, Seq())
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      if (outputsEqual(cfd.unsignedFiatNotSentPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(BUYER_REFUNDED) andThen {
          case cfd:CertifiedFiatDelivery =>
            context.parent ! BuyerRefunded(cfd.id)
            walletMgrRef ! RemoveWatchEscrowAddress(cfd.fullySignedOpenTx.escrowAddr)
        }
      }
      else
        stay()

    case e =>
      log.error(s"Received event after fiat not sent certified by notary: $e")
      stay()
  }

  when(SELLER_FUNDED) {
    case Event(Start, cfd: CertifiedFiatDelivery) =>
      context.parent ! SellerCreatedOffer(cfd.id, cfd.sellOffer)
      context.parent ! SellerFunded(cfd.id)
      stay()

    case e =>
      log.error(s"Received event after seller funded: $e")
      stay()
  }

  when(BUYER_REFUNDED) {
    case Event(Start, cfd: CertifiedFiatDelivery) =>
      context.parent ! SellerCreatedOffer(cfd.id, cfd.sellOffer)
      context.parent ! BuyerRefunded(cfd.id)
      stay()

    case e =>
      log.error(s"Received event after buyer refunded: $e")
      stay()
  }

  initialize()

}



