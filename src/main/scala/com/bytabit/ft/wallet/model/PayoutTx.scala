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

import com.bytabit.ft.wallet.model.TxTools.COIN_MINER_FEE
import org.bitcoinj.core.Transaction.Purpose
import org.bitcoinj.core._
import org.bitcoinj.script.{Script, ScriptBuilder}

import scala.collection.JavaConversions._

// create new unsigned payout tx
object PayoutTx extends TxTools {
  def apply(n: Notary, s: Seller, b: Buyer, coinSellerPayout: Option[Coin], coinBuyerPayout: Option[Coin],
            coinNotaryFee: Option[Coin], signedOpenTx: OpenTx, signedFundTxOutputs: Option[Seq[TransactionOutput]]) = {

    assert(signedOpenTx.fullySigned)
    new PayoutTx(n.netParams, coinSellerPayout, coinBuyerPayout, coinNotaryFee,
      signedOpenTx.outputsToEscrow ++ signedFundTxOutputs.getOrElse(Seq()), s.payoutAddr, b.payoutAddr, n.feesAddr,
      escrowRedeemScript(n, s, b))
  }
}

case class PayoutTx(netParams: NetworkParameters, coinSellerPayout: Option[Coin], coinBuyerPayout: Option[Coin],
                    coinNotaryFee: Option[Coin], outputsToEscrow: Seq[TransactionOutput],
                    sellerPayoutAddr: Address, buyerPayoutAddr: Address, notaryFeeAddr: Address,
                    redeemScript: Script, inputSigs: Seq[TxSig] = Seq()) extends Tx {

  tx.setPurpose(Purpose.ASSURANCE_CONTRACT_CLAIM)

  val coinEscrowAmt = outputsToEscrow.foldLeft(Coin.ZERO)((t, o) => t.add(o.getValue))

  // verify inputs from escrow and payout amounts
  assert(coinEscrowAmt.subtract(coinSellerPayout.getOrElse(Coin.ZERO)).subtract(coinBuyerPayout.getOrElse(Coin.ZERO))
    .subtract(coinNotaryFee.getOrElse(Coin.ZERO)).subtract(COIN_MINER_FEE).isZero)

  // add  inputs with empty unlock scripts
  outputsToEscrow.foreach { o =>
    val input = tx.addInput(o)
    val emptyUnlockScript = ScriptBuilder.createP2SHMultiSigInputScript(null, redeemScript)
    input.setScriptSig(emptyUnlockScript)
  }

  // set input unlock scripts, use first two available signatures
  tx.getInputs.indices.filter(i => inputSigs.map(_.inputIndex).contains(i)) foreach { i =>
    val sigs = inputSigs.filter(_.inputIndex == i).sortWith((s1, s2) => PubECKey.lt(s1.pubECKey, s2.pubECKey)).map(_.inputSig).slice(0, 2)
    val unlockScript = ScriptBuilder.createP2SHMultiSigInputScript(sigs, redeemScript)
    tx.getInput(i).setScriptSig(unlockScript)
  }

  // add outputs
  coinSellerPayout.foreach(tx.addOutput(_, sellerPayoutAddr))
  coinBuyerPayout.foreach(tx.addOutput(_, buyerPayoutAddr))
  coinNotaryFee.foreach(tx.addOutput(_, notaryFeeAddr))

  assert(verified)

  def sign(escrowPubKey: PubECKey)(implicit w: Wallet): PayoutTx = {
    this.copy(inputSigs = addInputSigs(signInputs(escrowPubKey), inputSigs))
  }

  def signInputs(escrowPubKey: PubECKey)(implicit w: Wallet): Seq[TxSig] = {
    w.findKeyFromPubKey(escrowPubKey.bytes) match {
      case privKey: ECKey =>
        tx.getInputs.indices map (i => TxSig(i, escrowPubKey, unlockScriptsP2SH(i, redeemScript, privKey)))

      // No private key found
      case _ => Seq()
    }
  }

  def addInputSigs(newSigs: Seq[TxSig]): PayoutTx = {
    this.copy(inputSigs = addInputSigs(newSigs, inputSigs))
  }
}
