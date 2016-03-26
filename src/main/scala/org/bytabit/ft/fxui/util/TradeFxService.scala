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

package org.bytabit.ft.fxui.util

import java.util.UUID
import java.util.function.Predicate
import javafx.beans.property.SimpleStringProperty
import javafx.collections.{FXCollections, ObservableList}

import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.fxui.model.TradeUIModel
import org.bytabit.ft.fxui.model.TradeUIModel.Role
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model._
import org.joda.time.DateTime

import scala.collection.JavaConversions._

trait TradeFxService extends ActorFxService {

  // UI Data

  val trades: ObservableList[TradeUIModel] = FXCollections.observableArrayList[TradeUIModel]
  val sellCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellDeliveryMethods: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellBondPercent = new SimpleStringProperty()
  val sellArbitratorFee = new SimpleStringProperty()

  // UI update functions

  def findTrade(id: UUID): Option[TradeUIModel] =
    trades.find(t => t.getId == id)

  def updateTrade(t: TradeUIModel, ut: TradeUIModel): Unit =
    trades.set(trades.indexOf(t), ut)

  //  def updateTradeState(state: State, id: UUID) {
  //    trades.find(t => t.getId == id) match {
  //      case Some(t) =>
  //        val newTradeUI = t.copy(state = state)
  //        trades.set(trades.indexOf(t), newTradeUI)
  //      case None =>
  //        log.error(s"trade error, id not found: $id")
  //    }
  //  }

  // common path

  def createOffer(role: Role, sellOffer: SellOffer): Unit = {
    trades.add(TradeUIModel(role, CREATED, sellOffer))
  }

  def takeOffer(bto: BuyerTookOffer): Unit = {
    findTrade(bto.id) match {
      case Some(TradeUIModel(r, s, so: SellOffer)) =>
        updateTrade(TradeUIModel(r, s, so), TradeUIModel(r, TAKEN, so.withBuyer(bto.buyer, bto.buyerOpenTxSigs,
          bto.buyerFundPayoutTxo, bto.cipherBuyerDeliveryDetails)))
      case Some(TradeUIModel(r, s, to: TakenOffer)) =>
        log.warning("Can't take offer that was already taken.")
      case _ =>
        log.error("No sell offer found to take.")
    }
  }

  def signOffer(sso: SellerSignedOffer): Unit = {
    findTrade(sso.id) match {
      case Some(TradeUIModel(r, s, to: TakenOffer)) =>
        updateTrade(TradeUIModel(r, s, to), TradeUIModel(r, SIGNED, to.withSellerSigs(sso.openSigs, sso.payoutSigs)))
      case _ =>
        log.error("No taken offer found to sign.")
    }
  }

  def openEscrow(boe: BuyerOpenedEscrow): Unit = {
    findTrade(boe.id) match {
      case Some(TradeUIModel(r, s, sto: SignedTakenOffer)) =>
        updateTrade(TradeUIModel(r, s, sto), TradeUIModel(r, OPENED, sto.withOpenTx(boe.txHash, boe.updateTime)))
      case _ =>
        log.error("No signed taken offer found to open.")
    }
  }

  def fundEscrow(bfe: BuyerFundedEscrow): Unit = {
    findTrade(bfe.id) match {
      case Some(TradeUIModel(r, s, ot: OpenedTrade)) =>
        updateTrade(TradeUIModel(r, s, ot), TradeUIModel(r, FUNDED, ot.withFundTx(bfe.txHash, bfe.updateTime,
          bfe.fiatDeliveryDetailsKey)))
      case _ =>
        log.error("No opened trade found to fund.")
    }
  }

  // happy path

  def fiatSent(fs: FiatSent) = {
    findTrade(fs.id) match {
      case Some(TradeUIModel(r, s, ft: FundedTrade)) =>
        updateTrade(TradeUIModel(r, s, ft), TradeUIModel(r, FIAT_SENT, ft))
      case _ =>
        log.error("No funded trade found to send fiat.")
    }
  }

  def fiatReceived(fr: FiatReceived) = {
    findTrade(fr.id) match {
      case Some(TradeUIModel(r, s, ft: FundedTrade)) =>
        updateTrade(TradeUIModel(r, s, ft), TradeUIModel(r, FIAT_RCVD, ft))
      case _ =>
        log.error("No funded trade found to receive fiat.")
    }
  }

  def payoutEscrow(id: UUID, txHash: Sha256Hash, txUpdateTime: DateTime): Unit = {
    findTrade(id) match {
      case Some(TradeUIModel(r, s, ft: FundedTrade)) =>
        updateTrade(TradeUIModel(r, s, ft), TradeUIModel(r, TRADED, ft.withPayoutTx(txHash, txUpdateTime)))
      case _ =>
        log.error("No funded trade found to payout.")
    }
  }

  // unhappy path

  def reqCertDelivery(cdr: CertifyDeliveryRequested): Unit = {
    findTrade(cdr.id) match {
      case Some(TradeUIModel(r, s, ft: FundedTrade)) =>
        updateTrade(TradeUIModel(r, s, ft), TradeUIModel(r, CERT_DELIVERY_REQD, ft.certifyFiatRequested(cdr.evidence)))
      case Some(TradeUIModel(r, s, cfe: CertifyFiatEvidence)) =>
        // add to existing evidence
        updateTrade(TradeUIModel(r, s, cfe), TradeUIModel(r, CERT_DELIVERY_REQD, cfe.addCertifyDeliveryRequest(cdr.evidence)))
      case _ =>
        log.error("No funded trade found to request fiat delivery certification.")
    }
  }

  def certifyFiatSent(fsc: FiatSentCertified): Unit = {
    findTrade(fsc.id) match {
      case Some(TradeUIModel(r, s, cfe: CertifyFiatEvidence)) =>
        updateTrade(TradeUIModel(r, s, cfe), TradeUIModel(r, FIAT_SENT_CERTD, cfe.withArbitratedFiatSentSigs(fsc.payoutSigs)))
      case _ =>
        log.error("No certified fiat evidence found to certify fiat sent.")
    }
  }

  def certifyFiatNotSent(fnc: FiatNotSentCertified): Unit = {
    findTrade(fnc.id) match {
      case Some(TradeUIModel(r, s, cfe: CertifyFiatEvidence)) =>
        updateTrade(TradeUIModel(r, s, cfe), TradeUIModel(r, FIAT_NOT_SENT_CERTD, cfe.withArbitratedFiatSentSigs(fnc.payoutSigs)))
      case _ =>
        log.error("No certified fiat evidence found to certify fiat not sent.")
    }
  }

  def fundSeller(sf: SellerFunded): Unit = {
    findTrade(sf.id) match {
      case Some(TradeUIModel(r, s, cfd: CertifiedFiatDelivery)) =>
        updateTrade(TradeUIModel(r, s, cfd), TradeUIModel(r, SELLER_FUNDED, cfd.withPayoutTx(sf.txHash, sf.updateTime)))
      case _ =>
        log.error("No certified fiat delivery found to fund seller.")
    }
  }

  def refundBuyer(br: BuyerRefunded): Unit = {
    findTrade(br.id) match {
      case Some(TradeUIModel(r, s, cfd: CertifiedFiatDelivery)) =>
        updateTrade(TradeUIModel(r, s, cfd), TradeUIModel(r, BUYER_REFUNDED, cfd.withPayoutTx(br.txHash, br.updateTime)))
      case _ =>
        log.error("No certified fiat non-delivery found to refund buyer.")
    }
  }

  // cancel path

  def cancelTradeUIModel(id: UUID) = {
    trades.removeIf(new Predicate[TradeUIModel] {
      override def test(a: TradeUIModel): Boolean = {
        a.getId == id
      }
    })
  }
}