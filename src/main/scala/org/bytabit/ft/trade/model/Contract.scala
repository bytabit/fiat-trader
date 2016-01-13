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

import org.bytabit.ft.util.SignedHashId
import org.bytabit.ft.wallet.model.Notary
import org.joda.money.{CurrencyUnit, Money}

object Contract {

  val text = "Fiat Trade Contract \n\n" +
    "1. The transactions in this contract are based on contract id $contractId published on the $btcNetworkName Bitcoin network.\n" +
    "2. In case of a dispute the notary at URL $notaryURL and id $notaryId will decide the outcome. \n" +
    "3. The notary fee will be $btcNotaryFee and will be paid to BTC address $notaryFeeAddress. \n" +
    "4. Buyer with key ID $buyerId will transfer $btcAmount to the seller.\n" +
    "5. Seller with key ID $sellerId will transfer $fiatAmount to the buyer.\n" +
    "6. Seller will transfer the $fiatAmount to the buyer using the $fiatDeliveryMethod fiat delivery method.\n" +
    "7. The buyer payment details are: $buyerFiatDeliveryDetails.\n"

  def apply(notary: Notary, fiatCurrencyUnit: CurrencyUnit, fiatDeliveryMethod: String): Contract =
    Contract(text, notary, fiatCurrencyUnit, fiatDeliveryMethod)
}

case class Contract(text: String, notary: Notary,
                    fiatCurrencyUnit: CurrencyUnit, fiatDeliveryMethod: String) extends SignedHashId with Template {

  val netParams = notary.netParams

  val id = hashId(netParams.getId, text, fiatCurrencyUnit.toString, fiatDeliveryMethod)

  val btcNetworkName = netParams.getId.split('.')(2).toUpperCase

  val keyValues = Map[String, Option[String]](
    "contractId" -> Some(id.toString),
    "fiatCurrencyUnit" -> Some(fiatCurrencyUnit.toString),
    "fiatDeliveryMethod" -> Some(fiatDeliveryMethod),

    "notaryId" -> Some(notary.id.toString),
    "btcNetworkName" -> Some(btcNetworkName),
    "notaryFeeAddress" -> Some(notary.feesAddr.toString),
    "notaryURL" -> Some(notary.url.toString),
    "bondPercent" -> Some(notary.bondPercent.toString),
    "btcNotaryFee" -> Some(notary.btcNotaryFee.toString)
  )

  def offer(id: UUID, fiatAmount: Money, btcAmount: Money): Offer =
    Offer(id, this, fiatAmount, btcAmount)
}

