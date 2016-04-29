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

package org.bytabit.ft.fxui.model

import javafx.beans.property._

import org.bytabit.ft.fxui.model.TradeUIActionTableCell.TradeOriginState
import org.bytabit.ft.trade.TradeProcess
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.util.Monies

case class TradeUIModel(role: Role, state: TradeProcess.State, trade: TradeData) {

  val url = trade.contract.arbitrator.url
  val id = trade.id
  val contract = trade.contract
  val fiatCurrencyUnit = contract.fiatCurrencyUnit
  val deliveryMethod = contract.fiatDeliveryMethod
  val fiatAmount = trade.fiatAmount
  val btcAmount = trade.btcAmount
  val exchangeRate = fiatAmount.dividedBy(btcAmount.getAmount, Monies.roundingMode)
  val bondPercent = contract.arbitrator.bondPercent
  val arbitratorFee = contract.arbitrator.btcArbitratorFee

  val actionProperty = new SimpleObjectProperty[TradeOriginState](TradeOriginState(url, id, role, state))
  val roleProperty = new SimpleStringProperty(role.toString)
  val statusProperty = new SimpleStringProperty(stateToString(state, role))
  val fiatCurrencyUnitProperty = new SimpleStringProperty(fiatCurrencyUnit.getCode)
  val fiatAmountProperty = new SimpleStringProperty(fiatAmount.toString)
  val btcAmountProperty = new SimpleStringProperty(btcAmount.toString)
  val exchangeRateProperty = new SimpleStringProperty(exchangeRate.toString)
  val deliveryMethodProperty = new SimpleStringProperty(deliveryMethod.name)

  val bondPercentProperty = new SimpleStringProperty(f"${bondPercent * 100}%f")
  val arbitratorFeeProperty = new SimpleStringProperty(arbitratorFee.toString)

  val uncommitted = (role, state) match {
    case (SELLER, s) if Seq(CREATED, TAKEN, SIGNED, OPENED).contains(s) => true
    case (BUYER, s) if Seq(TAKEN, SIGNED, OPENED).contains(s) => true

    case _ => false
  }

  def getId = id

  def stateToString(state: TradeProcess.State, role: Role): String = {
    (state, role) match {
      case (CREATED, _) => "OFFERED"
      case (CANCELED, _) => "CANCELED"
      case (TAKEN, _) => "TAKEN"
      case (SIGNED, _) => "SIGNED"
      case (OPENED, _) => "OPENED"
      case (FUNDED, _) => "FUNDED"
      case (FIAT_SENT, _) => "FIAT SENT"
      case (FIAT_RCVD, _) => "FIAT RCVD"
      case (TRADED, SELLER) => "SOLD"
      case (TRADED, BUYER) => "BOUGHT"
      case (TRADED, ARBITRATOR) => "TRADED"
      case (CERT_DELIVERY_REQD, _) => "CERT REQD"
      case (FIAT_SENT_CERTD, _) => "FIAT SENT"
      case (FIAT_NOT_SENT_CERTD, _) => "FIAT NOT SENT"
      case (BUYER_REFUNDED, _) => "*REFUNDED"
      case (SELLER_FUNDED, SELLER) => "*SOLD"
      case (SELLER_FUNDED, BUYER) => "*BOUGHT"
      case (SELLER_FUNDED, ARBITRATOR) => "*TRADED"

      case _ => "ERROR!"
    }
  }

}
