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
import org.bytabit.ft.trade.model.{SignedTakenOffer, TradeData, TakenOffer, SellOffer}

import scala.collection.JavaConversions._

trait TradeFxService extends ActorFxService {

  // UI Data

  val trades: ObservableList[TradeUIModel] = FXCollections.observableArrayList[TradeUIModel]
  val sellCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellDeliveryMethods: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellBondPercent = new SimpleStringProperty()
  val sellNotaryFee = new SimpleStringProperty()

  // UI update functions

//  def addOrUpdateTradeUIModel(role: Role, state: State, offer: SellOffer): Unit = {
//
//    trades.find(t => t.getId == offer.id) match {
//      case Some(t) =>
//        val newTradeUI = t.copy(state = state, offer = offer)
//        trades.set(trades.indexOf(t), newTradeUI)
//      case None =>
//        trades.add(TradeUIModel(role, state, offer))
//    }
//  }

  def createOffer(role: Role, sellOffer: SellOffer): Unit = {
    trades.add(TradeUIModel(role, CREATED, sellOffer))
  }

  def takeOffer(bto:BuyerTookOffer):Unit = {
    trades.find(t => t.getId == bto.id) match {
      case Some(TradeUIModel(role, state, so:SellOffer)) =>
        val taken = TradeUIModel(role, TAKEN, so.withBuyer(bto.buyer, bto.buyerOpenTxSigs, bto.buyerFundPayoutTxo,
          bto.cipherBuyerDeliveryDetails))
        trades.set(trades.indexOf(TradeUIModel(role, state, so:SellOffer)), taken)
      case _ =>
        log.error("No offer found to take.")
    }
  }

  def signOffer(sso: SellerSignedOffer):Unit = {
    trades.find(t => t.getId == sso.id) match {
      case Some(TradeUIModel(role, state, to:TakenOffer)) =>
        val signed = TradeUIModel(role, SIGNED, to.withSellerSigs(sso.openSigs, sso.payoutSigs))
        trades.set(trades.indexOf(TradeUIModel(role, state, to:TakenOffer)), signed)
      case _ =>
        log.error("No offer found to take.")
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