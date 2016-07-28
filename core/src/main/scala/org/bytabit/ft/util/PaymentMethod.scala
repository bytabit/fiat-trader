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

package org.bytabit.ft.util

import org.joda.money.CurrencyUnit

object PaymentMethod {

  val moneygram: PaymentMethod =
    PaymentMethod("Moneygram", Seq(CurrencyUnits.USD, CurrencyUnits.EUR), Seq("Moneygram Info..."), "Moneygram Reference #")

  val swish: PaymentMethod =
    PaymentMethod("Swish", Seq(CurrencyUnits.SEK), Seq("Swish Phone Number"), "Swish Reference #")

  val westernUnion: PaymentMethod =
    PaymentMethod("Western Union", Seq(CurrencyUnits.USD, CurrencyUnits.EUR), Seq("Western Union Info..."), "Western Union Reference #")

  val all = Seq(moneygram, swish, westernUnion)

  def forCurrencyUnit(cu: CurrencyUnit) = all.filter(_.currencyUnits.contains(cu))

  def getInstance(name: String): Option[PaymentMethod] = all.find(fdm => fdm.name == name)
}

case class PaymentMethod(name: String, currencyUnits: Seq[CurrencyUnit],
                         requiredDetails: Seq[String], requiredReference: String) {

  currencyUnits.foreach(cu => assert(Monies.isFiat(cu)))
}
