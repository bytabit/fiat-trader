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
import org.bitcoinj.script.{ScriptBuilder, ScriptOpCodes}
import org.bitcoinj.wallet.Wallet
import org.bytabit.ft.util.AESCipher
import org.bytabit.ft.wallet.model.TxTools.{COIN_MINER_FEE, COIN_OP_RETURN_FEE}

object FundTx extends TxTools {

  // create new unsigned fund tx
  def apply(coinFundEscrow: Coin, n: Arbitrator, s: BtcBuyer, b: BtcSeller, ddk: Array[Byte]) =

    new FundTx(n.netParams, coinFundEscrow, escrowAddress(n, s, b), b.fundTxUtxo, b.changeAddr, ddk)
}

case class FundTx(netParams: NetworkParameters, coinFundEscrow: Coin,
                  escrowAddr: Address,
                  btcSellerFundTxUtxo: Seq[TransactionOutput], btcSellerChangeAddr: Address,
                  btcSellerPaymentDetailsKey: Array[Byte],
                  inputSigs: Seq[TxSig] = Seq()) extends Tx {

  tx.setPurpose(Purpose.ASSURANCE_CONTRACT_PLEDGE)

  val coinBtcSellerInput = Tx.coinTotalOutputValue(btcSellerFundTxUtxo)

  val coinBtcSellerChg = coinBtcSellerInput.subtract(coinFundEscrow)

  // verify change amounts
  assert(!coinBtcSellerChg.isNegative)

  // verify payment details key lengths
  assert(btcSellerPaymentDetailsKey.length == AESCipher.AES_KEY_LEN)

  // add inputs
  btcSellerFundTxUtxo.foreach(o => tx.addInput(o))

  // add input unlock input scripts to tx, use first available signature
  setInputUnlockScriptsP2PKH(inputSigs)

  // add escrow outputs
  tx.addOutput(coinFundEscrow.subtract(COIN_MINER_FEE).subtract(COIN_OP_RETURN_FEE), escrowAddr)

  // add change output
  if (coinBtcSellerChg.isPositive) {
    tx.addOutput(coinBtcSellerChg, btcSellerChangeAddr)
  }

  // add encrypted aes key to decrypt payment details
  tx.addOutput(COIN_OP_RETURN_FEE, new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(btcSellerPaymentDetailsKey).build())

  assert(verified)

  def outputsToEscrow = outputsToP2SH(escrowAddr)

  def sign(implicit w: Wallet): FundTx = {
    this.copy(inputSigs = addInputSigsP2PKH(w, inputSigs, anyoneCanPay = false))
  }

  def signInputs(implicit w: Wallet): Seq[TxSig] = signInputs(w, inputSigs, anyoneCanPay = false)

  def addInputSigs(newSigs: Seq[TxSig]): FundTx = {
    this.copy(inputSigs = addInputSigs(newSigs, inputSigs))
  }
}
