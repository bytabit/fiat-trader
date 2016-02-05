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
import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, Role, SELLER}
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model.SellOffer
import org.bytabit.ft.util.Monies
import org.joda.time.DateTime

object TradeUIModel {

  sealed trait Role

  case object NOTARY extends Role

  case object SELLER extends Role

  case object BUYER extends Role

}

case class TradeUIModel(role: Role, state: TradeFSM.State, offer: SellOffer,
                        posted: Option[DateTime] = None) {

  val url = offer.contract.notary.url
  val id = offer.id
  val contract = offer
  val template = contract.contract
  val fiatCurrencyUnit = template.fiatCurrencyUnit
  val deliveryMethod = template.fiatDeliveryMethod
  val fiatAmount = contract.fiatAmount
  val btcAmount = contract.btcAmount
  val exchangeRate = fiatAmount.dividedBy(btcAmount.getAmount, Monies.roundingMode)
  val bondPercent = template.notary.bondPercent
  val notaryFee = template.notary.btcNotaryFee

  val actionProperty = new SimpleObjectProperty[TradeOriginState](TradeOriginState(url, id, role, state))
  val statusProperty = new SimpleStringProperty(stateToString(state, role))
  val fiatCurrencyUnitProperty = new SimpleStringProperty(fiatCurrencyUnit.toString)
  val fiatAmountProperty = new SimpleStringProperty(fiatAmount.toString)
  val btcAmountProperty = new SimpleStringProperty(btcAmount.toString)
  val exchangeRateProperty = new SimpleStringProperty(exchangeRate.toString)
  val deliveryMethodProperty = new SimpleStringProperty(deliveryMethod)

  val bondPercentProperty = new SimpleStringProperty(f"${bondPercent * 100}%f")
  val notaryFeeProperty = new SimpleStringProperty(notaryFee.toString)

  val uncommitted = (role, state) match {
    case (SELLER, s) if Seq(CREATED,TAKEN,SIGNED,OPENED).contains(s) => true
    case (BUYER, s) if Seq(TAKEN,SIGNED,OPENED).contains(s) => true

    case _ => false
  }

  def getId = id

  def stateToString(state: TradeFSM.State, role: Role): String = {
    (state, role) match {
      case (CREATED, _) => "OFFERED"
      case (CANCELED, _) => "CANCELED"
      case (TAKEN, _) => "TAKEN"
      case (SIGNED, _) => "SIGNED"
      case (OPENED, _) => "OPENED"
      case (FUNDED, _) => "FUNDED"
      case (CERT_DELIVERY_REQD, _) => "CERT REQD"
      case (FIAT_SENT_CERTD, _) => "SENT"
      case (FIAT_NOT_SENT_CERTD, _) => "NOT SENT"
      case (FIAT_RCVD, _) => "FIAT RCVD"
      case (TRADED, BUYER) => "BOUGHT"
      case (TRADED, SELLER) => "SOLD"
      case (TRADED, _) => "TRADED"
      case (BUYER_REFUNDED, _) => "*REFUNDED"
      case (SELLER_FUNDED, _) => "*SOLD"

      case _ => "ERROR!"
    }
  }

}
