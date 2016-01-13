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

package com.bytabit.ft.util

import java.math.RoundingMode

import org.bitcoinj.core.Coin
import org.joda.money.{CurrencyUnit, Money}

case object Monies {
  def roundingMode = RoundingMode.UP

  def isFiat(currencyUnit: CurrencyUnit): Boolean = currencyUnit != CurrencyUnits.XBT

  def isFiat(money: Money): Boolean = isFiat(money.getCurrencyUnit)

  def isBTC(money: Money): Boolean = money.getCurrencyUnit == CurrencyUnits.XBT
}

case object FiatMoney {
  def apply(currencyUnit: CurrencyUnit, amount: BigDecimal) = {
    assert(!currencyUnit.equals(CurrencyUnits.XBT))
    Money.of(currencyUnit, amount.bigDecimal)
  }

  def apply(currencyUnit: CurrencyUnit, amount: String) = {
    assert(!currencyUnit.equals(CurrencyUnits.XBT))
    Money.of(currencyUnit, BigDecimal(amount).bigDecimal)
  }
}

case object BTCMoney {

  def apply(amount: Double): Money = apply(BigDecimal(amount))

  def apply(amount: BigDecimal): Money = Money.of(CurrencyUnits.XBT, amount.bigDecimal)

  def apply(amount: String): Money = Money.of(CurrencyUnits.XBT, BigDecimal(amount).bigDecimal)

  def apply(majAmount: Int, minAmount: Int = 0): Money = Money.ofMajor(CurrencyUnits.XBT, majAmount)
    .plus(Money.ofMinor(CurrencyUnits.XBT, minAmount))

  def apply(coin: Coin): Money = Money.ofMinor(CurrencyUnits.XBT, coin.value)

  def toCoin(money: Money): Coin = {
    assert(money.getCurrencyUnit.equals(CurrencyUnits.XBT))

    Coin.valueOf(money.getAmountMinorLong)
  }

  def toCoin(money: Option[Money]): Option[Coin] = {
    money.map(toCoin)
  }
}

