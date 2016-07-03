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

package org.bytabit.ft.fxui.client

import javafx.beans.property.SimpleStringProperty

import org.bytabit.ft.client.model.PaymentDetails
import org.bytabit.ft.util.PaymentMethod
import org.joda.money.CurrencyUnit

object PaymentDetailsUIModel {
  def apply(pd: PaymentDetails) = new PaymentDetailsUIModel(pd.currencyUnit, pd.paymentMethod, pd.paymentDetails)
}

case class PaymentDetailsUIModel(currencyUnit: CurrencyUnit, paymentMethod: PaymentMethod, paymentDetails: String) {

  val currencyUnitProperty = new SimpleStringProperty(currencyUnit.getCode)
  val paymentMethodProperty = new SimpleStringProperty(paymentMethod.name)
  val paymentDetailsProperty = new SimpleStringProperty(paymentDetails)
}
