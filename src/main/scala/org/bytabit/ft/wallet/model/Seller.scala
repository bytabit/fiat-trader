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

package org.bytabit.ft.wallet.model

import org.bitcoinj.core._
import org.bitcoinj.wallet.Wallet

object Seller extends WalletTools {

  def apply(coinToOpenEscrow: Coin)(implicit w: Wallet): Seller = {

    val openTxUtxo = selected(coinToOpenEscrow, unspent)

    apply(coinToOpenEscrow, openTxUtxo)
  }

  def apply(coinToOpenEscrow: Coin, openTxUtxo: List[TransactionOutput])(implicit w: Wallet): Seller =

    new Seller(w.getParams, freshEscrowKey, freshChangeAddress, freshPayoutAddress, openTxUtxo)

}

case class Seller(netParams: NetworkParameters, escrowPubKey: PubECKey, changeAddr: Address, payoutAddr: Address,
                  openTxUtxo: Seq[TransactionOutput]) extends ParticipantDetails


