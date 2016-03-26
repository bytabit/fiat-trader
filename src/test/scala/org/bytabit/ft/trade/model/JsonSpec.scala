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

package org.bytabit.ft.trade.model

import java.net.URL
import java.util.UUID

import org.bitcoinj.core._
import org.bytabit.ft.trade.TradeFSM.SellerCreatedOffer
import org.bytabit.ft.trade.{TradeFSM, TradeFSMJsonProtocol}
import org.bytabit.ft.util.{BTCMoney, CurrencyUnits, FiatMoney}
import org.bytabit.ft.wallet.model.{Arbitrator, Seller}
import org.scalatest._
import spray.json._

class JsonSpec extends FlatSpec with Matchers with TradeFSMJsonProtocol {

  val params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET)

  Context.propagate(new Context(params))

  val arbitratorWallet = new Wallet(params)
  val sellerWallet = new Wallet(params)

  val btcAmt = BTCMoney(5, 0)
  val fiatAmt = FiatMoney(CurrencyUnits.USD, "1500.00")
  val bondPercent = 0.20
  val btcArbitratorFee = BTCMoney(1, 0)
  val arbitratorURL = new URL("http://bytabit.org")
  val fiatDeliveryMethod = "CASH DEPOSIT"
  val fiatCurrencyUnit = CurrencyUnits.USD

  val arbitrator = Arbitrator(arbitratorURL, bondPercent, btcArbitratorFee)(arbitratorWallet)

  val contract = Contract(arbitrator, fiatCurrencyUnit, fiatDeliveryMethod)

  val offer = Offer(UUID.randomUUID(), contract, fiatAmt, btcAmt)

  val sellOffer = SellOffer(offer, Seller(offer.coinToOpenEscrow)(sellerWallet))

  it should "serialize Offer to json" in {

    val evt: TradeFSM.PostedEvent = SellerCreatedOffer(UUID.randomUUID(), sellOffer)

    val json: String = tradePostedEventJsonFormat.write(evt).toString()
    //System.out.println(json)

    val obj: TradeFSM.PostedEvent = tradePostedEventJsonFormat.read(json.parseJson)
    //System.out.println(obj)

    json should equal(tradePostedEventJsonFormat.write(obj).toString())
  }

}
