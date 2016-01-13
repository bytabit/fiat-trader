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

import java.net.URL
import javafx.beans.property.SimpleStringProperty

import org.bitcoinj.core.Sha256Hash
import org.joda.money.CurrencyUnit

case class ContractUIModel(notaryUrl: URL, id: Sha256Hash, fiatCurrencyUnit: CurrencyUnit, deliveryMethod: String) {

  val urlProperty = new SimpleStringProperty(notaryUrl.toString)
  val idProperty = new SimpleStringProperty(id.toString)
  val fiatCurrencyUnitProperty = new SimpleStringProperty(fiatCurrencyUnit.toString)
  val deliveryMethodProperty = new SimpleStringProperty(deliveryMethod)

  def getId = idProperty.get
}