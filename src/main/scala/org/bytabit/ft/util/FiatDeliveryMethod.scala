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

object FiatDeliveryMethod {

  val moneygram: FiatDeliveryMethod =
    FiatDeliveryMethod("Moneygram", Seq(CurrencyUnits.USD, CurrencyUnits.EUR), Seq("Moneygram Info..."))

  val swish: FiatDeliveryMethod =
    FiatDeliveryMethod("Swish", Seq(CurrencyUnits.SEK), Seq("Swish Phone Number"))

  val westernUnion: FiatDeliveryMethod =
    FiatDeliveryMethod("Western Union", Seq(CurrencyUnits.USD, CurrencyUnits.EUR), Seq("Western Union Info..."))

  val all = Seq(moneygram, swish, westernUnion)

  def forCurrencyUnit(cu: CurrencyUnit) = all.filter(_.currencyUnits.contains(cu))

  def getInstance(name:String):Option[FiatDeliveryMethod] = all.find(fdm => fdm.name == name)
}

case class FiatDeliveryMethod(name: String, currencyUnits: Seq[CurrencyUnit],
                              requiredDetails: Seq[String]) {

  currencyUnits.foreach(cu => assert(Monies.isFiat(cu)))
}
