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

import org.bitcoinj.core.{Coin, TransactionOutput}
import org.bitcoinj.wallet.{KeyChain, Wallet}

import scala.collection.JavaConversions._

trait WalletTools {

  def freshEscrowKey(implicit w: Wallet) = PubECKey(w.freshKey(KeyChain.KeyPurpose.AUTHENTICATION))

  def freshChangeAddress(implicit w: Wallet) = w.freshAddress(KeyChain.KeyPurpose.CHANGE)

  def freshPayoutAddress(implicit w: Wallet) = w.freshAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS)

  def unspent(implicit w: Wallet): List[TransactionOutput] = w.calculateAllSpendCandidates(true, true).toList

  def selected(coinAmt: Coin, from: List[TransactionOutput])(implicit w: Wallet) = {
    w.getCoinSelector.select(coinAmt, from).gathered.toList
  }

  def unselected(all: List[TransactionOutput], selected: List[TransactionOutput]) =
    all.filterNot(selected.toSet)
}
