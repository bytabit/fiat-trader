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

import org.bitcoinj.core.{Context, NetworkParameters, Wallet}
import org.bytabit.ft.util.{BTCMoney, CurrencyUnits, FiatDeliveryMethod}
import org.bytabit.ft.wallet.model.Arbitrator
import org.scalatest._

class ContractSpec extends FlatSpec with Matchers {

  val params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET)
  Context.propagate(new Context(params))

  // arbitrator
  val arbitratorWallet = new Wallet(params)

  val bondPercent = 0.20
  val btcArbitratorFee = BTCMoney(1, 0)
  val arbitratorURL = new URL("http://bytabit.org")
  val fiatDeliveryMethod = FiatDeliveryMethod.swish
  val fiatCurrencyUnit = CurrencyUnits.USD
  val arbitrator = Arbitrator(arbitratorURL, bondPercent, btcArbitratorFee)(arbitratorWallet)

  "Contract" should "substitute template parameters" in {

    val contract = Contract(arbitrator, fiatCurrencyUnit, fiatDeliveryMethod)

    val contractId = contract.id.toString
    val arbitratorId = contract.arbitrator.id.toString
    val btcNetworkName = contract.btcNetworkName
    val arbitratorFeeAddress = contract.arbitrator.feesAddr.toString
    val NONE = "<NONE>"
    val buyerId = NONE
    val btcAmount = NONE
    val sellerId = NONE
    val fiatAmount = NONE
    val buyerFiatDeliveryDetails = NONE

    contract.toString should equal(
      "Fiat Trade Contract \n\n" +
        s"1. The transactions in this contract are based on contract id $contractId published on the $btcNetworkName Bitcoin network.\n" +
        s"2. In case of a dispute the arbitrator at URL $arbitratorURL and id $arbitratorId will decide the outcome. \n" +
        s"3. The arbitrator fee will be $btcArbitratorFee and will be paid to BTC address $arbitratorFeeAddress. \n" +
        "4. Buyer with key ID $buyerId will transfer $btcAmount to the seller.\n" +
        "5. Seller with key ID $sellerId will transfer $fiatAmount to the buyer.\n" +
        "6. Seller will transfer the $fiatAmount to the buyer using the" + s" $fiatDeliveryMethod fiat delivery method.\n" +
        "7. The buyer payment details are: $buyerFiatDeliveryDetails.\n")
  }

  "ContractTemplate" should "have the same id given the same text and parameters" in {
    val t1 = Contract(arbitrator, fiatCurrencyUnit, fiatDeliveryMethod)
    val t2 = Contract(arbitrator, fiatCurrencyUnit, fiatDeliveryMethod)

    t1.id should equal(t2.id)
  }
}
