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

import org.bitcoinj.core.Transaction.SigHash
import org.bitcoinj.core._
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.script.{Script, ScriptBuilder}

import scala.collection.JavaConversions._

object Tx {
  def fullySigned(tx: Transaction): Boolean = {
    try {
      tx.getInputs.foreach(i => i.verify(i.getConnectedOutput))
    }
    catch {
      case e: VerificationException =>
        //println(s"signature verification exception: ${e.getMessage}\nfor tx: $signedTx")
        return false
    }
    true
  }

  def coinTotalOutputValue(txOutputs: Seq[TransactionOutput]): Coin = txOutputs.foldLeft(Coin.ZERO)((c, o) => c.add(o.getValue))
}

trait Tx {

  val netParams: NetworkParameters

  protected[wallet] val tx = new Transaction(netParams)

  //def coinTotalOutputValue(txOutputs: List[TransactionOutput]): Coin = txOutputs.foldLeft(Coin.ZERO)((c, o) => c.add(o.getValue))

  //def pubKeyCompare(k1: PubECKey, k2: PubECKey) = PubECKey.lt(k1,k2) //ECKey.PUBKEY_COMPARATOR.compare(k1.eckey, k2.eckey)

  def outputsToP2SH(a: Address) = tx.getOutputs.filter(o => a.equals(o.getAddressFromP2SH(tx.getParams))).toList

  def addInputSigsP2PKH(w: Wallet, currentSigs: Seq[TxSig], anyoneCanPay: Boolean = false) = {

    val newSigs = signInputs(w, currentSigs, anyoneCanPay)
    addInputSigs(newSigs, currentSigs)
  }

  def signInputs(w: Wallet, inputSigs: Seq[TxSig], anyoneCanPay: Boolean = false) = {

    tx.getInputs.indices.map { i =>
      val lockScript = tx.getInput(i).getConnectedOutput.getScriptPubKey
      val key = w.findKeyFromPubHash(lockScript.getPubKeyHash)
      (i, key)
    }.filter(s => s._2 != null).map(s => TxSig(s._1, PubECKey(s._2), unlockSigP2PKH(s._1, s._2, anyoneCanPay)))
  }

  def addInputSigs(newSigs: Seq[TxSig], currentSigs: Seq[TxSig]): Seq[TxSig] = {

    // add new signatures, replace signatures with same input index and key
    newSigs ++ currentSigs.filter(is =>
      !newSigs.map(ns => (ns.inputIndex, ns.pubECKey)).contains((is.inputIndex, is.pubECKey)))
  }

  // set input unlock scripts, use first available signature
  def setInputUnlockScriptsP2PKH(inputSigs: Seq[TxSig]) = {

    tx.getInputs.indices.filter(i => inputSigs.map(_.inputIndex).contains(i)) foreach { i =>
      val sig = inputSigs.filter(_.inputIndex == i).map(_.inputSig).head
      val pubkey = inputSigs.filter(_.inputIndex == i).map(_.pubECKey).head
      val unlockScript = ScriptBuilder.createInputScript(sig, pubkey.eckey)
      tx.getInput(i).setScriptSig(unlockScript)
    }
  }

  def unlockSigP2PKH(inputIndex: Int, key: ECKey, anyoneCanPay: Boolean = false) = {

    val input = tx.getInput(inputIndex)
    val lockScript = input.getConnectedOutput.getScriptPubKey

    // set empty unlock script prior to calculating tx input signature
    val emptyUnlockInputScript = ScriptBuilder.createInputScript(null, key)
    //println(s"input[$inputIndex], emptyUnlockScript: ${emptyUnlockScript.toString}")
    input.setScriptSig(emptyUnlockInputScript)

    // calculate and set tx input signature
    tx.calculateSignature(inputIndex, key, lockScript.getProgram, SigHash.ALL, anyoneCanPay)
  }

  def unlockScriptsP2SH(inputIndex: Int, redeemScript: Script, escrowKey: ECKey): TransactionSignature = {
    val input = tx.getInput(inputIndex)

    if (input.getScriptSig.getChunks.size == 0) {
      val emptyUnlockScript = ScriptBuilder.createP2SHMultiSigInputScript(null, redeemScript)
      input.setScriptSig(emptyUnlockScript)
    }

    //val lockScript = input.getConnectedOutput.getScriptPubKey
    val unlockSigHash = tx.hashForSignature(inputIndex, redeemScript, Transaction.SigHash.ALL, false)
    new TransactionSignature(escrowKey.sign(unlockSigHash), Transaction.SigHash.ALL, false)
  }

  def fullySigned: Boolean = Tx.fullySigned(tx)

  def inputs: Seq[TransactionInput] = tx.getInputs.map(_.duplicateDetached())

  def outputs: Seq[TransactionOutput] = tx.getOutputs.map(_.duplicateDetached())

  def verified: Boolean = {
    try {
      tx.verify()
    }
    catch {
      case e: VerificationException =>
        return false
    }
    true
  }
}
