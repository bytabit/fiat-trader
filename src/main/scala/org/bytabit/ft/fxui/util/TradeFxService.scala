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

import org.bytabit.ft.fxui.model.TradeUIModel
import org.bytabit.ft.fxui.model.TradeUIModel.Role
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model.{SellOffer, SignedTakenOffer, TakenOffer}

import scala.collection.JavaConversions._

trait TradeFxService extends ActorFxService {

  // UI Data

  val trades: ObservableList[TradeUIModel] = FXCollections.observableArrayList[TradeUIModel]
  val sellCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellDeliveryMethods: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellBondPercent = new SimpleStringProperty()
  val sellNotaryFee = new SimpleStringProperty()

  // UI update functions

  def createOffer(role: Role, sellOffer: SellOffer): Unit = {
    trades.add(TradeUIModel(role, CREATED, sellOffer))
  }

  def findTrade(id: UUID): Option[TradeUIModel] =
    trades.find(t => t.getId == id)

  def updateTrade(t: TradeUIModel, ut: TradeUIModel): Unit =
    trades.set(trades.indexOf(t), ut)

  def takeOffer(bto: BuyerTookOffer): Unit = {
    findTrade(bto.id) match {
      case Some(TradeUIModel(r, s, so: SellOffer)) =>
        updateTrade(TradeUIModel(r, s, so), TradeUIModel(r, TAKEN, so.withBuyer(bto.buyer, bto.buyerOpenTxSigs, bto.buyerFundPayoutTxo,
          bto.cipherBuyerDeliveryDetails)))
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

  def fundEscrow(bfe: BuyerFundedEscrow): Unit = {
    bfe.fiatDeliveryDetailsKey match {
      case Some(k) =>
        findTrade(bfe.id) match {
          case Some(TradeUIModel(r, s, sto: SignedTakenOffer)) =>
            updateTrade(TradeUIModel(r, s, sto), TradeUIModel(r, FUNDED, sto.withFiatDeliveryDetailsKey(k)))
          case _ =>
            log.error("No signed offer found to fund.")
        }
      case None =>
        updateTradeState(FUNDED, bfe.id)
        log.error("No fiat delivery details key found in funding tx.")
    }
  }

  def reqCertDelivery(cdr: CertifyDeliveryRequested): Unit = {
    findTrade(cdr.id) match {
      case Some(TradeUIModel(r, s, sto: SignedTakenOffer)) =>
        updateTrade(TradeUIModel(r, s, sto), TradeUIModel(r, CERT_DELIVERY_REQD, sto.certifyFiatRequested(cdr.evidence)))
      case _ =>
        log.error("No sell offer found to take.")
    }
  }

  def updateTradeState(state: State, id: UUID) {
    trades.find(t => t.getId == id) match {
      case Some(t) =>
        val newTradeUI = t.copy(state = state)
        trades.set(trades.indexOf(t), newTradeUI)
      case None =>
        log.error(s"trade error, id not found: $id")
    }
  }

  def cancelTradeUIModel(id: UUID) = {
    trades.removeIf(new Predicate[TradeUIModel] {
      override def test(a: TradeUIModel): Boolean = {
        a.getId == id
      }
    })
  }
}