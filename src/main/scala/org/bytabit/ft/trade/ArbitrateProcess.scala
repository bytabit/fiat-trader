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
import org.bytabit.ft.trade.ArbitrateProcess._
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.wallet.TradeWalletManager.SetTransactionMemo
import org.bytabit.ft.wallet.WalletManager.EscrowTransactionUpdated
import org.bytabit.ft.wallet.{EscrowWalletManager, TradeWalletManager, WalletManager}
import org.joda.time.DateTime

import scala.language.postfixOps

object ArbitrateProcess {

  // commands

  sealed trait Command {
    val url: URL
    val id: UUID
  }

  final case class Start(url: URL, id: UUID) extends Command

  final case class CertifyFiatSent(url: URL, id: UUID) extends Command

  final case class CertifyFiatNotSent(url: URL, id: UUID) extends Command

}

case class ArbitrateProcess(sellOffer: SellOffer, tradeWalletMgrRef: ActorRef, escrowWalletMgrRef: ActorRef) extends TradeProcess {

  override val id = sellOffer.id

  // logging

  override val log = Logging(context.system, this)

  startWith(CREATED, sellOffer)

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

    // someone took the offer
    case Event(bto: BuyerTookOffer, so: SellOffer) if bto.posted.isDefined =>
      goto(TAKEN) applying bto andThen { to =>
        context.parent ! bto
      }
  }

  when(TAKEN) {
    case Event(Start, to: TakenOffer) =>
      startTaken(to)
      stay()

    // seller signed
    case Event(sso: SellerSignedOffer, to: TakenOffer) if sso.posted.isDefined =>
      goto(SIGNED) applying sso andThen {
        case sto: SignedTakenOffer =>
          escrowWalletMgrRef ! EscrowWalletManager.AddWatchAddress(sto.fullySignedOpenTx.escrowAddr, to.sellOffer.posted.get)
          context.parent ! sso
      }
  }

  when(SIGNED) {
    case Event(Start, sto: SignedTakenOffer) =>
      startSigned(sto)
      stay()

    case Event(etu: WalletManager.EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      if (outputsEqual(sto.unsignedOpenTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val boe = BuyerOpenedEscrow(sto.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(OPENED) applying boe andThen {
          case ot: OpenedTrade =>
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

    case Event(etu: WalletManager.EscrowTransactionUpdated, ot: OpenedTrade) =>
      if (outputsEqual(ot.signedTakenOffer.unsignedFundTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val bfe = BuyerFundedEscrow(ot.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime),
          fiatDeliveryDetailsKey(etu.tx))
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

    case Event(cfr: CertifyDeliveryRequested, ft: FundedTrade) if cfr.posted.isDefined =>
      goto(CERT_DELIVERY_REQD) applying cfr andThen {
        case sto: CertifyFiatEvidence =>
          context.parent ! cfr
      }

    case Event(etu: WalletManager.EscrowTransactionUpdated, ft: FundedTrade) =>
      if (outputsEqual(ft.unsignedPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val brp = BuyerReceivedPayout(ft.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(TRADED) applying brp andThen {
          case st: SettledTrade =>
            context.parent ! brp
            escrowWalletMgrRef ! EscrowWalletManager.RemoveWatchAddress(ft.escrowAddress)
        }
      }
      else
        stay()
  }

  // happy path

  when(TRADED) {
    case Event(Start, st: SettledTrade) =>
      startSellerTraded(st)
      stay()

    case Event(etu: WalletManager.EscrowTransactionUpdated, sto: SignedTakenOffer) =>
      stay()

    case e =>
      log.warning(s"Received event after being traded: ${e.getClass}")
      stay()
  }

  // unhappy path

  when(CERT_DELIVERY_REQD) {
    case Event(Start, cfe: CertifyFiatEvidence) =>
      startCertDeliveryReqd(cfe)
      stay()

    // certify fiat sent

    case Event(cfs: CertifyFiatSent, cfe: CertifyFiatEvidence) =>
      tradeWalletMgrRef ! TradeWalletManager.CertifyFiatSent(cfe)
      stay()

    case Event(WalletManager.FiatSentCertified(cfs), cfe: CertifyFiatEvidence) =>
      postTradeEvent(cfs.url, FiatSentCertified(cfs.id, cfs.arbitratorPayoutTxSigs), self)
      stay()

    case Event(fsc: FiatSentCertified, cfe: CertifyFiatEvidence) if fsc.posted.isDefined =>
      goto(FIAT_SENT_CERTD) applying fsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fsc
      }

    // certify fiat not sent

    case Event(cfns: CertifyFiatNotSent, cfe: CertifyFiatEvidence) =>
      tradeWalletMgrRef ! TradeWalletManager.CertifyFiatNotSent(cfe)
      stay()

    case Event(WalletManager.FiatNotSentCertified(cfd), cfe: CertifyFiatEvidence) =>
      postTradeEvent(cfd.url, FiatNotSentCertified(cfd.id, cfd.arbitratorPayoutTxSigs), self)
      stay()

    case Event(fnsc: FiatNotSentCertified, cfe: CertifyFiatEvidence) if fnsc.posted.isDefined =>
      goto(FIAT_NOT_SENT_CERTD) applying fnsc andThen {
        case cfd: CertifiedFiatDelivery =>
          context.parent ! fnsc
      }
  }

  when(FIAT_SENT_CERTD) {
    case Event(Start, cfs: CertifiedFiatDelivery) =>
      startFiatSentCertd(cfs)
      stay()

    case Event(etu: EscrowTransactionUpdated, cfd: CertifiedFiatDelivery) =>
      if (outputsEqual(cfd.unsignedFiatSentPayoutTx, etu.tx) &&
        etu.confidenceType == ConfidenceType.BUILDING) {
        val sf = SellerFunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(SELLER_FUNDED) applying sf andThen {
          case cst: CertifiedSettledTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Arbitrate Fee Trade $id")
            context.parent ! sf
            escrowWalletMgrRef ! EscrowWalletManager.RemoveWatchAddress(cst.escrowAddress)
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
        etu.confidenceType == ConfidenceType.BUILDING) {
        val br = BuyerRefunded(cfd.id, etu.tx.getHash, new DateTime(etu.tx.getUpdateTime))
        goto(BUYER_REFUNDED) applying br andThen {
          case cst: CertifiedSettledTrade =>
            tradeWalletMgrRef ! SetTransactionMemo(etu.tx.getHash, s"Arbitrate Fee Trade $id")
            context.parent ! br
            escrowWalletMgrRef ! EscrowWalletManager.RemoveWatchAddress(cfd.escrowAddress)
        }
      }
      else
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



