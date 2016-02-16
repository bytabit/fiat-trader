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
import org.bitcoinj.script.{Script, ScriptBuilder}
import org.bytabit.ft.util.BTCMoney

import scala.collection.JavaConversions._

object TxTools {

  // TODO issue #35, currently fixed at 0.0001, should be calculated
  val COIN_MINER_FEE = Coin.COIN.divide(10000)

  val COIN_OP_RETURN_FEE = Coin.ZERO //Transaction.MIN_NONDUST_OUTPUT

  val BTC_MINER_FEE = BTCMoney(COIN_MINER_FEE)

  val BTC_OP_RETURN_FEE = BTCMoney(COIN_OP_RETURN_FEE)
}

trait TxTools {

  // create p2sh escrow address
  def sortedEscrowKeys(pubKeys: Seq[PubECKey]): Seq[ECKey] = pubKeys.sortWith(PubECKey.lt).map(_.eckey)

  def sortedEscrowKeys(a: Notary, s: Seller, b: Buyer): Seq[ECKey] =
    sortedEscrowKeys(List(a.escrowPubKey, b.escrowPubKey, s.escrowPubKey))

  def escrowPayoutScript(a: Notary, s: Seller, b: Buyer): Script =
    ScriptBuilder.createP2SHOutputScript(2, sortedEscrowKeys(a, s, b))

  def escrowRedeemScript(a: Notary, s: Seller, b: Buyer): Script =
    ScriptBuilder.createRedeemScript(2, sortedEscrowKeys(a, s, b))

  def escrowAddress(a: Notary, s: Seller, b: Buyer) = escrowPayoutScript(a, s, b).getToAddress(a.netParams)
}