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

import com.bytabit.ft.util.BTCMoney._
import org.bitcoinj.core.Coin
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.PropertyChecks

class MoneySpec extends FlatSpec with Matchers with PropertyChecks with UtilJsonProtocol {

  it should "not throw exception if creating BTC Money with the correct number of decimal places" in {

    val satoshi: Long = Coin.COIN.value

    val value = for {
      maj <- Gen.choose[Long](0, 10)
      min <- Gen.choose[Long](0, satoshi)
    } yield (maj, min)

    forAll(value) { v: (Long, Long) =>

      val btc = BTCMoney((BigDecimal(v._1) + (BigDecimal(v._2) / BigDecimal(satoshi))).bigDecimal)
    }
  }

  it should "throw an exception when BTC Money created with incorrect number of decimal places" in {

    a[ArithmeticException] should be thrownBy {
      val btc = BTCMoney(BigDecimal(1.000000001).bigDecimal)
    }
  }

  it should "throw an exception when BTC Money created using the FiatMoney factory" in {

    a[AssertionError] should be thrownBy {
      val btc = FiatMoney(CurrencyUnits.XBT, BigDecimal(2.000001).bigDecimal)
    }
  }

  it should "convert to and from BTC Money and Coin types" in {
    val satoshi: Int = Coin.COIN.value.toInt

    val value = for {
      maj <- Gen.choose[Int](0, 10)
      min <- Gen.choose[Int](0, satoshi)
    } yield (maj, min)

    forAll(value) { v: (Int, Int) =>

      val btc = BTCMoney(v._1, v._2)
      val coin = Coin.COIN.multiply(v._1).add(Coin.valueOf(v._2))

      BTCMoney(coin) should equal(btc)
      coin should equal(toCoin(btc))
    }
  }

  it should "throw an exception when non BTC Money converted to Coin" in {

    a[AssertionError] should be thrownBy {
      val coin = toCoin(FiatMoney(CurrencyUnits.USD, BigDecimal(2.01).bigDecimal))
    }
  }

  it should "create the same BTC Money from Double or BigDecimal" in {

    val BTC_Double = BTCMoney(12.34)
    val BTC_BigDec = BTCMoney(BigDecimal(12.34))
    BTC_Double should equal(BTC_BigDec)
  }
}
