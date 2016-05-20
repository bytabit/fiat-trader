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

object Buyer extends WalletTools {

  def apply(coinToOpenEscrow: Coin, coinToFundEscrow: Coin)(implicit w: Wallet): Buyer = {

    val allUnspent = unspent

    // TODO FT-27: need to make sure deposits are broken into multiple outputs for each tx
    val openTxUtxo = selected(coinToOpenEscrow, allUnspent)
    val fundTxUtxo = selected(coinToFundEscrow, unselected(allUnspent, openTxUtxo))

    apply(coinToOpenEscrow, coinToFundEscrow, openTxUtxo, fundTxUtxo)
  }

  def apply(coinToOpenEscrow: Coin, coinToFundEscrow: Coin, openTxUtxo: List[TransactionOutput],
            fundTxUtxo: List[TransactionOutput])(implicit w: Wallet): Buyer =

    new Buyer(w.getParams, freshEscrowKey, freshChangeAddress, freshPayoutAddress, openTxUtxo, fundTxUtxo)
}

case class Buyer(netParams: NetworkParameters, escrowPubKey: PubECKey,
                 changeAddr: Address, payoutAddr: Address,
                 openTxUtxo: Seq[TransactionOutput], fundTxUtxo: Seq[TransactionOutput]) extends ParticipantDetails
