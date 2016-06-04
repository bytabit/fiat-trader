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

import org.bitcoinj.wallet.Wallet
import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money

case class CertifyPaymentEvidence(fundedTrade: FundedTrade,
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
  val btcBuyOffer = takenOffer.btcBuyOffer
  val btcBuyer = signedTakenOffer.btcBuyer
  val buyer = signedTakenOffer.buyer
  val fullySignedOpenTx = signedTakenOffer.fullySignedOpenTx

  def unsignedFiatSentPayoutTx: PayoutTx = super.unsignedFiatSentPayoutTx(btcBuyer, buyer, fullySignedOpenTx,
    takenOffer.buyerFundPayoutTxo)

  def unsignedFiatNotSentPayoutTx: PayoutTx = super.unsignedFiatNotSentPayoutTx(btcBuyer, buyer, fullySignedOpenTx,
    takenOffer.buyerFundPayoutTxo)

  def withArbitratedFiatSentSigs(arbitratorPayoutTxSigs: Seq[TxSig]) =
    CertifiedPayment(this, arbitratorPayoutTxSigs)

  def withArbitratedFiatNotSentSigs(arbitratorPayoutTxSigs: Seq[TxSig]) =
    CertifiedPayment(this, arbitratorPayoutTxSigs)

  def certifyFiatSent(implicit arbitratorWallet: Wallet): CertifiedPayment = {
    val arbitratedFiatSentPayoutTx: PayoutTx = unsignedFiatSentPayoutTx.sign(arbitrator.escrowPubKey)

    CertifiedPayment(this, arbitratedFiatSentPayoutTx.inputSigs)
  }

  def certifyFiatNotSent(implicit arbitratorWallet: Wallet): CertifiedPayment = {
    val arbitratorFiatNotSentPayoutTx: PayoutTx = unsignedFiatNotSentPayoutTx.sign(arbitrator.escrowPubKey)

    CertifiedPayment(this, arbitratorFiatNotSentPayoutTx.inputSigs)
  }

  def addCertifyPaymentRequest(evidence: Option[Array[Byte]]) =
    this.copy(evidence = this.evidence ++ evidence.toSeq)
}
