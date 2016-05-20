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

package org.bytabit.ft.wallet

import org.bitcoinj.core._
import org.bitcoinj.crypto.TransactionSignature
import org.bytabit.ft.util.UtilJsonProtocol
import org.bytabit.ft.wallet.model._
import spray.json._

trait WalletJsonProtocol extends UtilJsonProtocol {

  implicit def buyerJsonFormat = jsonFormat(Buyer.apply, "netParams", "escrowPubKey", "changeAddr", "payoutAddr",
    "openTxUtxo", "fundTxUtxo")

  implicit def sellerJsonFormat = jsonFormat(Seller.apply, "netParams", "escrowPubKey", "changeAddr", "payoutAddr",
    "openTxUtxo")

  implicit def NotaryJsonFormat = jsonFormat(Arbitrator.apply, "url", "netParams", "escrowPubKey", "feesAddr",
    "bondPercent", "btcNotaryFee")

  implicit object Sha256HashJsonFormat extends JsonFormat[Sha256Hash] {

    def read(value: JsValue) = value match {
      case JsString(h) => Sha256Hash.wrap(h)
      case _ => deserializationError("Sha256Hash expected")
    }

    def write(h: Sha256Hash) = JsString(h.toString)
  }

  implicit object AddressJsonFormat extends JsonFormat[Address] {

    def read(value: JsValue) = value match {
      case JsArray(Vector(JsString(netId), JsString(addr))) => Address.fromBase58(NetworkParameters.fromID(netId), addr)
      case _ => deserializationError("Address expected")
    }

    def write(addr: Address) = JsArray(JsString(addr.getParameters.getId), JsString(addr.toString))
  }

  implicit object NetworkParametersJsonFormat extends JsonFormat[NetworkParameters] {

    def read(value: JsValue) = value match {
      case JsString(netId) => NetworkParameters.fromID(netId)
      case _ => deserializationError("NetworkParameters id expected")
    }

    def write(netParams: NetworkParameters) = JsString(netParams.getId)
  }

  implicit object PubECKeyJsonFormat extends JsonFormat[PubECKey] {

    def read(value: JsValue) = value match {
      case JsString(pk) => PubECKey(pk)
      case _ => deserializationError("PubECKey expected")
    }

    def write(pk: PubECKey) = JsString(pk.toString)
  }

  implicit object TransactionOutputJsonFormat extends JsonFormat[TransactionOutput] {

    override def read(value: JsValue): TransactionOutput = value match {
      case JsArray(Vector(JsString(netParams), JsNumber(index), JsString(txHex))) =>
        val np = NetworkParameters.fromID(netParams)
        val txb = Utils.HEX.decode(txHex)
        val tx = new Transaction(np, txb)
        tx.getOutput(index.toInt)

      case _ => deserializationError("TransactionInput expected")
    }

    override def write(txOutput: TransactionOutput): JsValue = {
      val np = JsString(txOutput.getParentTransaction.getParams.getId)
      val idx = JsNumber(txOutput.getIndex)
      val txb = JsString(Utils.HEX.encode(txOutput.getParentTransaction.bitcoinSerialize()))
      JsArray(Vector(np, idx, txb))
    }
  }

  implicit object TxSigJsonFormat extends JsonFormat[TxSig] {

    override def read(value: JsValue): TxSig = value match {
      case JsArray(Vector(JsNumber(n), pek, JsString(txs))) =>
        val inputIdx = n.toInt
        val pubECKey = PubECKeyJsonFormat.read(pek)
        val inputSig = TransactionSignature.decodeFromBitcoin(Utils.HEX.decode(txs), false, false)
        TxSig(inputIdx, pubECKey, inputSig)
      case _ => deserializationError("TxSig expected")
    }

    override def write(txSig: TxSig): JsValue = {
      val inputIdx = JsNumber(txSig.inputIndex)
      val pubECKey = PubECKeyJsonFormat.write(txSig.pubECKey)
      val inputSig = JsString(Utils.HEX.encode(txSig.inputSig.encodeToBitcoin()))
      JsArray(Vector(inputIdx, pubECKey, inputSig))
    }
  }

}
