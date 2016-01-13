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
import org.bytabit.ft.trade.BuyFSM.{ReceiveFiat, Start, TakeSellOffer}
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model.{SellOffer, SignedTakenOffer, TakenOffer}
import org.bytabit.ft.wallet.WalletManager
import org.bytabit.ft.wallet.WalletManager.{AddWatchEscrowAddress, BroadcastTx, EscrowTransactionUpdated, RemoveWatchEscrowAddress}

import scala.language.postfixOps

object BuyFSM {

  // commands

  sealed trait Command

  case object Start extends Command

  //final case class CancelSellOffer(notaryUrl: URL, id: UUID) extends Command

  final case class TakeSellOffer(notaryUrl: URL, id: UUID, fiatDeliveryDetails: String) extends Command

  final case class ReceiveFiat(notaryUrl: URL, id: UUID) extends Command

}

class BuyFSM(sellOffer: SellOffer, walletMgrRef: ActorRef) extends TradeFSM(sellOffer.id) {

  // logging

  override val log = Logging(context.system, this)

  startWith(PUBLISHED, sellOffer)

  when(PUBLISHED) {

    case Event(Start, so: SellOffer) =>
      context.parent ! SellerCreatedOffer(so.id, so)
      stay()

    case Event(sco: SellerCreatedOffer, so: SellOffer) if sco.posted.isDefined =>
      context.parent ! sco
      stay()

    case Event(sco: SellerCanceledOffer, so: SellOffer) if sco.posted.isDefined =>
      goto(CANCELED) andThen { uso =>
        context.parent ! sco
      }

    case Event(tso: TakeSellOffer, so: SellOffer) =>
      walletMgrRef ! WalletManager.TakeSellOffer(so, tso.fiatDeliveryDetails)
      stay()

    case Event(WalletManager.SellOfferTaken(to), so: SellOffer) =>

      if (to.amountOk) {
        val bto = BuyerTookOffer(to.id, to.buyer, to.buyerOpenTxSigs, to.buyerFundPayoutTxo)
        postTradeEvent(to.url, bto, self)
      } else {
        log.error(s"Insufficient btc amount to take offer ${so.id}")
      }
      stay()

    case Event(bto: BuyerTookOffer, so: SellOffer) if bto.posted.isDefined =>
      goto(TAKEN) applying bto andThen {
        case uto: TakenOffer =>
          context.parent ! bto
      }
  }

  when(TAKEN) {
    case Event(Start, to: TakenOffer) =>
      context.parent ! SellerCreatedOffer(to.id, to.sellOffer)
      context.parent ! BuyerTookOffer(to.id, to.buyer, Seq(), Seq())
      stay()

    case Event(sso: SellerSignedOffer, to: TakenOffer) if sso.posted.isDefined =>
      goto(SIGNED) applying sso andThen {
        case sto: SignedTakenOffer =>
          walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
          walletMgrRef ! BroadcastTx(sto.fullySignedOpenTx)
          context.parent ! sso
      }
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! SellerSignedOffer(sto.id, Seq(), Seq())
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
      // TODO need to look for outputs equal to fully signed payout tx??
      if (outputsEqual(sto.unsignedPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(BOUGHT) andThen {
          case usto: SignedTakenOffer =>
            context.parent ! BuyerReceivedPayout(sto.id)
            walletMgrRef ! RemoveWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
        }
      }
      else
        stay()
  }

  when(BOUGHT) {
    // TODO remove escrow wallet
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! SellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerReceivedPayout(sto.id)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      stay()
  }

  when(CANCELED) {
    case e =>
      log.error(s"Received event after being canceled: $e")
      stay()
  }

  initialize()

}



