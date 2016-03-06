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

package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.{Sha256Hash, Wallet}
import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money
import org.joda.time.DateTime

case class CertifyFiatEvidence(fundedTrade: FundedTrade,
                               evidence: Seq[Array[Byte]] = Seq()) extends Template with TradeData {

  override val id: UUID = fundedTrade.id
  override val btcAmount: Money = fundedTrade.btcAmount
  override val fiatAmount: Money = fundedTrade.fiatAmount
  override val contract: Contract = fundedTrade.contract

  override val text: String = fundedTrade.text
  override val keyValues: Map[String, Option[String]] = fundedTrade.keyValues

  val escrowAddress = fundedTrade.escrowAddress

  val signedTakenOffer = fundedTrade.openedTrade.signedTakenOffer
  val takenOffer = signedTakenOffer.takenOffer
  val sellOffer = takenOffer.sellOffer
  val seller = signedTakenOffer.seller
  val buyer = signedTakenOffer.buyer
  val fullySignedOpenTx = signedTakenOffer.fullySignedOpenTx

  def unsignedFiatSentPayoutTx: PayoutTx = super.unsignedFiatSentPayoutTx(seller, buyer, fullySignedOpenTx,
    takenOffer.buyerFundPayoutTxo)

  def unsignedFiatNotSentPayoutTx: PayoutTx = super.unsignedFiatNotSentPayoutTx(seller, buyer, fullySignedOpenTx,
    takenOffer.buyerFundPayoutTxo)

  def withNotarizedFiatSentSigs(notaryPayoutTxSigs: Seq[TxSig]) =
    CertifiedFiatDelivery(this, notaryPayoutTxSigs)

  def withNotarizedFiatNotSentSigs(notaryPayoutTxSigs: Seq[TxSig]) =
    CertifiedFiatDelivery(this, notaryPayoutTxSigs)

  def certifyFiatSent(implicit notaryWallet: Wallet): CertifiedFiatDelivery = {
    val notarizedFiatSentPayoutTx: PayoutTx = unsignedFiatSentPayoutTx.sign(notary.escrowPubKey)

    CertifiedFiatDelivery(this, notarizedFiatSentPayoutTx.inputSigs)
  }

  def certifyFiatNotSent(implicit notaryWallet: Wallet): CertifiedFiatDelivery = {
    val notarizedFiatNotSentPayoutTx: PayoutTx = unsignedFiatNotSentPayoutTx.sign(notary.escrowPubKey)

    CertifiedFiatDelivery(this, notarizedFiatNotSentPayoutTx.inputSigs)
  }

  def addCertifyDeliveryRequest(evidence: Option[Array[Byte]]) =
    this.copy(evidence = this.evidence ++ evidence.toSeq)
}
