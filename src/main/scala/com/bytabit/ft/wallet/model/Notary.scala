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

package com.bytabit.ft.wallet.model

import java.net.URL

import com.bytabit.ft.util.Monies
import org.bitcoinj.core.{Address, NetworkParameters, Wallet}
import org.joda.money.Money


object Notary extends WalletTools with Serializable {

  def apply(url: URL, bondPercent: Double, btcNotaryFee: Money)(implicit w: Wallet): Notary =
    new Notary(url, w.getParams, freshEscrowKey, freshPayoutAddress, bondPercent, btcNotaryFee)
}

case class Notary(url: URL, netParams: NetworkParameters, escrowPubKey: PubECKey, feesAddr: Address,
                  bondPercent: Double, btcNotaryFee: Money)
  extends ParticipantDetails {

  assert(Monies.isBTC(btcNotaryFee))
}