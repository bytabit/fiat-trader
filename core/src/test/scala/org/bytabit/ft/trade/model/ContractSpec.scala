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

import org.bitcoinj.core.{Context, NetworkParameters}
import org.bitcoinj.wallet.Wallet
import org.bytabit.ft.util.{BTCMoney, CurrencyUnits, PaymentMethod}
import org.bytabit.ft.wallet.model.Arbitrator

class ContractSpec extends FlatSpec with Matchers {

  val params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET)
  Context.propagate(new Context(params))

  // arbitrator
  val arbitratorWallet = new Wallet(params)

  val bondPercent = 0.20
  val btcArbitratorFee = BTCMoney(1, 0)
  val arbitratorURL = new URL("http://bytabit.org")
  val paymentMethod = PaymentMethod.swish
  val fiatCurrencyUnit = CurrencyUnits.USD
  val arbitrator = Arbitrator(arbitratorURL, bondPercent, btcArbitratorFee)(arbitratorWallet)

  "Contract" should "substitute template parameters" in {

    val contract = Contract(arbitrator, fiatCurrencyUnit, paymentMethod)

    val contractId = contract.id.toString
    val arbitratorId = contract.arbitrator.id.toString
    val btcNetworkName = contract.btcNetworkName
    val arbitratorFeeAddress = contract.arbitrator.feesAddr.toString
    val NONE = "<NONE>"
    val btcSellerId = NONE
    val btcAmount = NONE
    val btcBuyerId = NONE
    val fiatAmount = NONE
    val btcSellerPaymentDetails = NONE

    contract.toString should equal(
      "Fiat Trade Contract \n\n" +
        s"1. The transactions in this contract are based on contract id $contractId published on the $btcNetworkName Bitcoin network.\n" +
        s"2. In case of a dispute the arbitrator at URL $arbitratorURL and id $arbitratorId will decide the outcome. \n" +
        s"3. The arbitrator fee will be $btcArbitratorFee and will be paid to BTC address $arbitratorFeeAddress. \n" +
        "4. BTC seller with key ID $btcSellerId will transfer $btcAmount to the BTC buyer.\n" +
        "5. BTC buyer with key ID $btcBuyerId will transfer $fiatAmount to the BTC seller.\n" +
        "6. BTC buyer will transfer the $fiatAmount to the BTC seller using the "+ s"$paymentMethod payment method.\n" +
        "7. The BTC seller payment details are: $btcSellerPaymentDetails.\n")
  }

  "ContractTemplate" should "have the same id given the same text and parameters" in {
    val t1 = Contract(arbitrator, fiatCurrencyUnit, paymentMethod)
    val t2 = Contract(arbitrator, fiatCurrencyUnit, paymentMethod)

    t1.id should equal(t2.id)
  }
}
