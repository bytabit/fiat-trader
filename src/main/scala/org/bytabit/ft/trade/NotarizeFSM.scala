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
import org.bytabit.ft.trade.NotarizeFSM.Start
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model.{CertifyFiatEvidence, SellOffer, SignedTakenOffer, TakenOffer}
import org.bytabit.ft.wallet.WalletManager.{AddWatchEscrowAddress, EscrowTransactionUpdated, RemoveWatchEscrowAddress}

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
          walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
          context.parent ! sso
      }
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

    case Event(cfr: CertifyFiatRequested, sto: SignedTakenOffer) if cfr.posted.isDefined =>
      goto(CERT_FIAT_REQD) applying cfr andThen {
        case sto: CertifyFiatEvidence =>
          context.parent ! cfr
      }

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

  when(CERT_FIAT_REQD) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerFundedEscrow(sto.id)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()
  }

  when(TRADED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! SellerReceivedPayout(sto.id)
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
    case e =>
      log.error(s"Received event after fiat sent certified by notary: $e")
      stay()
  }

  when(FIAT_NOT_SENT_CERTD) {
    case e =>
      log.error(s"Received event after fiat not sent certified by notary: $e")
      stay()
  }

  initialize()

}



