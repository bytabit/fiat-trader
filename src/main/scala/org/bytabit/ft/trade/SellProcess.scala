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
import org.bytabit.ft.trade.SellProcess.{CancelSellOffer, RequestCertifyDelivery, Start}
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model.{SellOffer, SignedTakenOffer, TakenOffer, _}
import org.bytabit.ft.wallet.WalletManager
import org.bytabit.ft.wallet.WalletManager.{AddWatchEscrowAddress, EscrowTransactionUpdated, RemoveWatchEscrowAddress}

import scala.language.postfixOps

object SellProcess {

  // commands

  sealed trait Command

  case object Start extends Command

  final case class AddSellOffer(offer: Offer) extends Command

  final case class CancelSellOffer(notaryUrl: URL, id: UUID) extends Command

  final case class SendFiat(notaryUrl: URL, id: UUID) extends Command

  final case class RequestCertifyDelivery(notaryUrl: URL, id: UUID, evidence: Option[Array[Byte]] = None) extends Command

}

class SellProcess(offer: Offer, walletMgrRef: ActorRef) extends TradeFSM {

  override val id = offer.id

  override val log = Logging(context.system, this)

  startWith(ADDED, offer)

  when(ADDED) {

    case Event(Start, o: Offer) =>
      walletMgrRef ! WalletManager.CreateSellOffer(o)
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
          walletMgrRef ! WalletManager.SignTakenOffer(to)
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
          walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
          context.parent ! sso
      }
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      startSigned(sto)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>

      if (outputsEqual(sto.fullySignedOpenTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {

        goto(OPENED) andThen { usto =>
          context.parent ! BuyerOpenedEscrow(usto.id)
        }
      }
      else
        stay()
  }

  when(OPENED) {
    case Event(Start, sto: SignedTakenOffer) =>
      startOpened(sto)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>

      if (outputsEqual(sto.unsignedFundTx, etu.tx, 0, etu.tx.getOutputs.size() - 1) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(FUNDED) applying BuyerSetFiatDeliveryDetailsKey(sto.id, fiatDeliveryDetailsKey(etu.tx)) andThen {
          case usto: SignedTakenOffer =>
            context.parent ! BuyerFundedEscrow(usto.id, usto.takenOffer.fiatDeliveryDetails.getOrElse(NO_DELIVERY_DETAILS))
        }
      }
      else
        stay()
  }

  when(FUNDED) {

    case Event(Start, sto: SignedTakenOffer) =>
      startFunded(sto)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(rcf: RequestCertifyDelivery, sto: SignedTakenOffer) =>
      postTradeEvent(rcf.notaryUrl, CertifyDeliveryRequested(sto.id, rcf.evidence), self)
      stay()

    case Event(cdr: CertifyDeliveryRequested, sto: SignedTakenOffer) if cdr.posted.isDefined =>
      goto(CERT_DELIVERY_REQD) applying cdr andThen {
        case cfe: CertifyFiatEvidence =>
          context.parent ! cdr
      }

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>

      if (outputsEqual(sto.sellerSignedPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(TRADED) andThen { usto =>
          context.parent ! SellerReceivedPayout(usto.id)
          walletMgrRef ! RemoveWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
        }
      }
      else
        stay()
  }

  when(CERT_DELIVERY_REQD) {

    case Event(Start, cfe: CertifyFiatEvidence) =>
      startCertDeliveryReqd(cfe)
      walletMgrRef ! AddWatchEscrowAddress(cfe.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(fsc: FiatSentCertified, cfe: CertifyFiatEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fsc
          walletMgrRef ! WalletManager.BroadcastTx(cfd.notarySignedFiatSentPayoutTx, Some(cfd.seller.escrowPubKey))
      }

    case Event(fnsc: FiatNotSentCertified, cfe: CertifyFiatEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fnsc
      }

    case Event(etu: EscrowTransactionUpdated, cfe: CertifyFiatEvidence) =>
      if (outputsEqual(cfe.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(SELLER_FUNDED) andThen {
          case cfd: CertifiedFiatDelivery =>
            context.parent ! SellerFunded(cfd.id)
            walletMgrRef ! RemoveWatchEscrowAddress(cfd.fullySignedOpenTx.escrowAddr)
        }
      }
      else if (outputsEqual(cfe.unsignedFiatNotSentPayoutTx, etu.tx) &&
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

  when(TRADED) {
    case Event(Start, sto: SignedTakenOffer) =>
      startTraded(sto)
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
      startFiatSentCertd(cfs)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      if (outputsEqual(cfd.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(SELLER_FUNDED) andThen {
          case cfd: CertifiedFiatDelivery =>
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
      startFiatNotSentCertd(cfd)
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
      startSellerFunded(cfd)
      stay()

    case e =>
      log.error(s"Received event after seller funded: $e")
      stay()
  }

  when(BUYER_REFUNDED) {
    case Event(Start, cfd: CertifiedFiatDelivery) =>
      startBuyerRefunded(cfd)
      stay()

    case e =>
      log.error(s"Received event after buyer refunded: $e")
      stay()
  }

  initialize()
}