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

final object CurrencyUnits {

  val XBT = CurrencyUnit.getInstance("XBT")

  val AUD = CurrencyUnit.AUD
  val CAD = CurrencyUnit.CAD
  val CHF = CurrencyUnit.CHF
  val EUR = CurrencyUnit.EUR
  val GBP = CurrencyUnit.GBP
  val JPY = CurrencyUnit.JPY
  val SEK = CurrencyUnit.getInstance("SEK")
  val USD = CurrencyUnit.USD

  val FIAT = Seq(AUD, CAD, CHF, EUR, GBP, JPY, SEK, USD)
}
