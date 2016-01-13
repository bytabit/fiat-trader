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

import org.bitcoinj.core.Wallet
import org.bytabit.ft.util.Monies
import org.bytabit.ft.wallet.model.Seller
import org.joda.money.Money

case class Offer(id: UUID, contract: Contract, fiatAmount: Money, btcAmount: Money)
  extends Template with TradeData {

  assert(Monies.isFiat(fiatAmount))
  assert(Monies.isBTC(btcAmount))

  override val text = contract.text
  override val keyValues = contract.keyValues ++ Map[String, Option[String]](
    "fiatAmount" -> Some(fiatAmount.toString),
    "btcAmount" -> Some(btcAmount.toString),
    "btcBond" -> Some(btcAmount.multipliedBy(contract.notary.bondPercent, Monies.roundingMode).toString)
  )

  def withSeller(seller: Seller) = SellOffer(this, seller)

  def withSeller(implicit sellerWallet: Wallet): SellOffer = {
    withSeller(Seller(coinToOpenEscrow)(sellerWallet))
  }
}
