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

import java.util.UUID

import org.bytabit.ft.util.{PaymentMethod, SignedHashId}
import org.bytabit.ft.wallet.model.Arbitrator
import org.joda.money.{CurrencyUnit, Money}

object Contract {

  val text = "Fiat Trade Contract \n\n" +
    "1. The transactions in this contract are based on contract id $contractId published on the $btcNetworkName Bitcoin network.\n" +
    "2. In case of a dispute the arbitrator at URL $arbitratorURL and id $arbitratorId will decide the outcome. \n" +
    "3. The arbitrator fee will be $btcArbitratorFee and will be paid to BTC address $arbitratorFeeAddress. \n" +
    "4. Buyer with key ID $buyerId will transfer $btcAmount to the seller.\n" +
    "5. Seller with key ID $sellerId will transfer $fiatAmount to the buyer.\n" +
    "6. Seller will transfer the $fiatAmount to the buyer using the $paymentMethod payment method.\n" +
    "7. The buyer payment details are: $buyerPaymentDetails.\n"

  def apply(arbitrator: Arbitrator, fiatCurrencyUnit: CurrencyUnit, paymentMethod: PaymentMethod): Contract =
    Contract(text, arbitrator, fiatCurrencyUnit, paymentMethod)
}

case class Contract(text: String, arbitrator: Arbitrator,
                    fiatCurrencyUnit: CurrencyUnit, paymentMethod: PaymentMethod) extends SignedHashId with Template {

  val netParams = arbitrator.netParams

  val id = hashId(netParams.getId, text, fiatCurrencyUnit.toString, paymentMethod.toString)

  val btcNetworkName = netParams.getId.split('.')(2).toUpperCase

  val keyValues = contractKeyValues(id, fiatCurrencyUnit, paymentMethod, arbitrator, btcNetworkName)

  def offer(id: UUID, fiatAmount: Money, btcAmount: Money): Offer =
    Offer(id, this, fiatAmount, btcAmount)
}

