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
import org.bytabit.ft.trade.model.SellOffer
import org.joda.time.DateTime

import scala.collection.JavaConversions._

trait TradeFxService extends ActorFxService {

  // UI Data

  val trades: ObservableList[TradeUIModel] = FXCollections.observableArrayList[TradeUIModel]
  val sellCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellDeliveryMethods: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellBondPercent = new SimpleStringProperty()
  val sellNotaryFee = new SimpleStringProperty()

  // UI update functions

  def addOrUpdateTradeUIModel(role: Role, state: State, offer: SellOffer,
                              posted: Option[DateTime] = None): Unit = {

    trades.find(t => t.getId == offer.id) match {
      case Some(t) =>
        val newTradeUI = t.copy(state = state, offer = offer, posted = posted)
        trades.set(trades.indexOf(t), newTradeUI)
      case None =>
        trades.add(TradeUIModel(role, state, offer, posted))
    }
  }

  def updateStateTradeUIModel(state: State, id: UUID) {
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