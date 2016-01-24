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
import org.bytabit.ft.trade.SellFSM.{CancelSellOffer, Start}
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model.{SellOffer, SignedTakenOffer, TakenOffer, _}
import org.bytabit.ft.wallet.WalletManager
import org.bytabit.ft.wallet.WalletManager.{AddWatchEscrowAddress, EscrowTransactionUpdated, RemoveWatchEscrowAddress}

import scala.concurrent.duration._
import scala.language.postfixOps

object SellFSM {

  // commands

  sealed trait Command

  case object Start extends Command

  final case class AddSellOffer(offer: Offer) extends Command

  final case class CancelSellOffer(notaryUrl: URL, id: UUID) extends Command

  final case class SendFiat(notaryUrl: URL, id: UUID) extends Command

}

class SellFSM(offer: Offer, walletMgrRef: ActorRef) extends TradeFSM(offer.id) {

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

  when(CREATED) {

    case Event(Start, so: SellOffer) =>
      context.parent ! LocalSellerCreatedOffer(so.id, so)
      stay()

    case Event(cso: CancelSellOffer, so: SellOffer) =>
      postTradeEvent(so.url, SellerCanceledOffer(so.id), self)
      stay()

    case Event(soc: SellerCanceledOffer, so: SellOffer) if soc.posted.isDefined =>
      goto(CANCELED) andThen { uso =>
        context.parent ! soc
      }

    case Event(bto: BuyerTookOffer, so: SellOffer) if bto.posted.isDefined =>
      goto(TAKEN) applying bto andThen { case to: TakenOffer =>
        context.parent ! bto
        walletMgrRef ! WalletManager.SignTakenOffer(to)
      }
  }

  when(TAKEN, stateTimeout = 5 seconds) {
    case Event(Start, to: TakenOffer) =>
      context.parent ! LocalSellerCreatedOffer(to.id, to.sellOffer)
      context.parent ! BuyerTookOffer(to.id, to.buyer, Seq(), Seq())
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

    case Event(StateTimeout, to: TakenOffer) =>
      walletMgrRef ! WalletManager.SignTakenOffer(to)
      stay()
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! LocalSellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! SellerSignedOffer(sto.id, sto.buyer.id, Seq(), Seq())
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>

      if (outputsEqual(sto.fullySignedOpenTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {

        goto(OPENED) andThen { usto =>
          context.parent ! BuyerOpenedEscrow(usto.id, Seq())
        }
      }
      else
        stay()
  }

  when(OPENED) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! LocalSellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerOpenedEscrow(sto.id, Seq())
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>

      if (outputsEqual(sto.unsignedFundTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {

        goto(FUNDED) andThen { usto =>
          context.parent ! BuyerFundedEscrow(usto.id)
        }
      }
      else
        stay()
  }

  when(FUNDED) {

    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! LocalSellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! BuyerFundedEscrow(sto.id)
      walletMgrRef ! AddWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
      stay()

    case Event(etu: EscrowTransactionUpdated, sto: SignedTakenOffer) =>

      if (outputsEqual(sto.sellerSignedPayoutTx, etu.tx) &&
        etu.tx.getConfidence.getConfidenceType == ConfidenceType.BUILDING) {
        goto(SOLD) andThen { usto =>
          context.parent ! SellerReceivedPayout(usto.id)
          walletMgrRef ! RemoveWatchEscrowAddress(sto.fullySignedOpenTx.escrowAddr)
        }
      }
      else
        stay()
  }

  when(SOLD) {
    case Event(Start, sto: SignedTakenOffer) =>
      context.parent ! LocalSellerCreatedOffer(sto.id, sto.takenOffer.sellOffer)
      context.parent ! SellerReceivedPayout(sto.id)
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