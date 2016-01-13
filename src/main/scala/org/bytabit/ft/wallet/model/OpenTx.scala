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

import org.bitcoinj.core.Transaction.Purpose
import org.bitcoinj.core._
import org.bytabit.ft.wallet.model.TxTools.COIN_MINER_FEE


object OpenTx extends TxTools {

  // create new unsigned open tx
  def apply(coinOpenEscrow: Coin, n: Notary, s: Seller, b: Buyer) =
    new OpenTx(n.netParams, coinOpenEscrow, escrowAddress(n, s, b), s.openTxUtxo, s.changeAddr,
      b.openTxUtxo, b.changeAddr)
}

case class OpenTx(netParams: NetworkParameters, coinOpenEscrow: Coin,
                  escrowAddr: Address, sellerOpenTxUtxo: Seq[TransactionOutput], sellerChangeAddr: Address,
                  buyerOpenTxUtxo: Seq[TransactionOutput], buyerChangeAddr: Address,
                  inputSigs: Seq[TxSig] = Seq()) extends Tx {

  tx.setPurpose(Purpose.ASSURANCE_CONTRACT_PLEDGE)

  val coinSellerInput = Tx.coinTotalOutputValue(sellerOpenTxUtxo)
  val coinBuyerInput = Tx.coinTotalOutputValue(buyerOpenTxUtxo)

  val coinSellerChg = coinSellerInput.subtract(coinOpenEscrow)
  val coinBuyerChg = coinBuyerInput.subtract(coinOpenEscrow)

  // verify change amounts
  assert(!coinSellerChg.isNegative && !coinBuyerChg.isNegative)

  // add inputs
  buyerOpenTxUtxo.foreach(o => tx.addInput(o))
  sellerOpenTxUtxo.foreach(o => tx.addInput(o))

  // add input unlock input scripts to tx, use first available signature
  setInputUnlockScriptsP2PKH(inputSigs)

  // add escrow outputs
  tx.addOutput(coinOpenEscrow.multiply(2).subtract(COIN_MINER_FEE), escrowAddr)

  // add change outputs
  if (coinSellerChg.isPositive) {
    tx.addOutput(coinSellerChg, sellerChangeAddr)
  }
  if (coinBuyerChg.isPositive) {
    tx.addOutput(coinBuyerChg, buyerChangeAddr)
  }

  // TODO add hash of contract details in op return?

  // TODO add fee or tip to notary for publishing offer?

  assert(verified)

  def outputsToEscrow = outputsToP2SH(escrowAddr)

  def sign(implicit w: Wallet): OpenTx = {
    this.copy(inputSigs = addInputSigsP2PKH(w, inputSigs, anyoneCanPay = true))
  }

  def signInputs(implicit w: Wallet): Seq[TxSig] = signInputs(w, inputSigs, anyoneCanPay = true)

  def addInputSigs(newSigs: Seq[TxSig]): OpenTx = {
    this.copy(inputSigs = addInputSigs(newSigs, inputSigs))
  }
}

