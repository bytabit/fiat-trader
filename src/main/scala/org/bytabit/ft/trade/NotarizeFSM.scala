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
import org.bytabit.ft.trade.NotarizeFSM._
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.wallet.WalletManager
import org.bytabit.ft.wallet.WalletManager.{EscrowTransactionUpdated, RemoveWatchEscrowAddress}

import scala.language.postfixOps

object NotarizeFSM {

  // commands

  sealed trait Command

  case object Start extends Command

  final case class CertifyFiatSent(notaryUrl: URL, id: UUID) extends Command

  final case class CertifyFiatNotSent(notaryUrl: URL, id: UUID) extends Command

}

class NotarizeFSM(sellOffer: SellOffer, walletMgrRef: ActorRef) extends TradeFSM(sellOffer.id) {

  // logging

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

    // someone took the offer
    case Event(bto: BuyerTookOffer, so: SellOffer) if bto.posted.isDefined =>
      goto(TAKEN) applying bto
  }

  when(TAKEN) {
    case Event(Start, to: TakenOffer) =>
      context.parent ! SellerCreatedOffer(to.id, to.sellOffer)
      context.parent ! BuyerTookOffer(to.id, to.buyer, Seq(), Seq())
      stay()

    // seller signed
    case Event(sso: SellerSignedOffer, to: TakenOffer) if sso.posted.isDefined =>
      goto(SIGNED) applying sso andThen {
        case sto: SignedTakenOffer =>
          walletMgrRef ! WalletManager.AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
          context.parent ! sso
      }
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! SellerSignedOffer(sto.id, sto.buyer.id, Seq(), Seq())
      walletMgrRef ! WalletManager.AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: WalletManager.EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedOpenTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(OPENED) andThen {
          case usto: SignedTakenOffer =>
            context.parent ! BuyerOpenedEscrow(sto.id, Seq())
        }
      }
      else
        stay()
  }

  when(OPENED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerOpenedEscrow(sto.id, Seq())
      walletMgrRef ! WalletManager.AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: WalletManager.EscrowTransactionUpdated, sto: SignedTakenOffer) =>
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
      walletMgrRef ! WalletManager.AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(cfr: CertifyDeliveryRequested, sto: SignedTakenOffer) if cfr.posted.isDefined =>
      goto(CERT_DELIVERY_REQD) applying cfr andThen {
        case sto: CertifyFiatEvidence =>
          context.parent ! cfr
      }

    case Event(etu: WalletManager.EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(TRADED) andThen {
          case usto: SignedTakenOffer =>
            context.parent ! BuyerReceivedPayout(sto.id)
            walletMgrRef ! WalletManager.RemoveWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
        }
      }
      else
        stay()
  }

  when(CERT_DELIVERY_REQD) {
    case Event(Start, cfe: CertifyFiatEvidence) =>
      context.parent ! SellerCreatedOffer(cfe.id, cfe.takenOffer.sellOffer)
      context.parent ! CertifyDeliveryRequested(cfe.id)
      walletMgrRef ! WalletManager.AddWatchEscrowAddress(cfe.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(cfs: CertifyFiatSent, cfe: CertifyFiatEvidence) =>
      walletMgrRef ! WalletManager.CertifyFiatSent(cfe)
      stay()

    case Event(WalletManager.FiatSentCertified(cfs), cfe: CertifyFiatEvidence) =>
      postTradeEvent(cfs.url, FiatSentCertified(cfs.id, cfs.notaryPayoutTxSigs), self)
      stay()

    case Event(fsc: FiatSentCertified, cfe: CertifyFiatEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fsc
      }

    case Event(cfns: CertifyFiatNotSent, cfe: CertifyFiatEvidence) =>
      walletMgrRef ! WalletManager.CertifyFiatNotSent(cfe)
      stay()

    case Event(WalletManager.FiatNotSentCertified(cfd), cfe: CertifyFiatEvidence) =>
      postTradeEvent(cfd.url, FiatNotSentCertified(cfd.id, cfd.notaryPayoutTxSigs), self)
      stay()

    case Event(fnsc: FiatNotSentCertified, cfe: CertifyFiatEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fnsc
      }
  }

  when(TRADED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! SellerReceivedPayout(sto.id)
      stay()

    case Event(etu: WalletManager.EscrowTransactionUpdated, sto: SignedTakenOffer) =>
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
          case cfd: CertifiedFiatDelivery =>
            context.parent ! BuyerRefunded(cfd.id)
            walletMgrRef ! RemoveWatchEscrowAddress(cfd.fullySignedOpenTx.escrowAddr)
        }
      }
      else
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



