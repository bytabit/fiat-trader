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
import org.bytabit.ft.util.{BTCMoney, CurrencyUnits}
import org.bytabit.ft.wallet.model.Notary
import org.scalatest._

class ContractSpec extends FlatSpec with Matchers {

  val params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET)
  Context.propagate(new Context(params))

  // notary
  val notaryWallet = new Wallet(params)

  val bondPercent = 0.20
  val btcNotaryFee = BTCMoney(1, 0)
  val notaryURL = new URL("http://bytabit.org")
  val fiatDeliveryMethod = "CASH DEPOSIT"
  val fiatCurrencyUnit = CurrencyUnits.USD
  val notary = Notary(notaryURL, bondPercent, btcNotaryFee)(notaryWallet)

  "Contract" should "substitute template parameters" in {

    val contract = Contract(notary, fiatCurrencyUnit, fiatDeliveryMethod)

    val contractId = contract.id.toString
    val notaryId = contract.notary.id.toString
    val btcNetworkName = contract.btcNetworkName
    val notaryFeeAddress = contract.notary.feesAddr.toString
    val NONE = "<NONE>"
    val buyerId = NONE
    val btcAmount = NONE
    val sellerId = NONE
    val fiatAmount = NONE
    val buyerFiatDeliveryDetails = NONE

    contract.toString should equal(
      "Fiat Trade Contract \n\n" +
        s"1. The transactions in this contract are based on contract id $contractId published on the $btcNetworkName Bitcoin network.\n" +
        s"2. In case of a dispute the notary at URL $notaryURL and id $notaryId will decide the outcome. \n" +
        s"3. The notary fee will be $btcNotaryFee and will be paid to BTC address $notaryFeeAddress. \n" +
        "4. Buyer with key ID $buyerId will transfer $btcAmount to the seller.\n" +
        "5. Seller with key ID $sellerId will transfer $fiatAmount to the buyer.\n" +
        "6. Seller will transfer the $fiatAmount to the buyer using the" + s" $fiatDeliveryMethod fiat delivery method.\n" +
        "7. The buyer payment details are: $buyerFiatDeliveryDetails.\n")
  }

  "ContractTemplate" should "have the same id given the same text and parameters" in {
    val t1 = Contract(notary, fiatCurrencyUnit, fiatDeliveryMethod)
    val t2 = Contract(notary, fiatCurrencyUnit, fiatDeliveryMethod)

    t1.id should equal(t2.id)
  }
}
