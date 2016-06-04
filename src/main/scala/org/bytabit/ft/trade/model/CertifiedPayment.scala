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

import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money
import org.joda.time.DateTime

case class CertifiedPayment(certifyPaymentEvidence: CertifyPaymentEvidence,
                            arbitratorPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = certifyPaymentEvidence.id
  override val btcAmount: Money = certifyPaymentEvidence.btcAmount
  override val fiatAmount: Money = certifyPaymentEvidence.fiatAmount
  override val contract: Contract = certifyPaymentEvidence.contract

  override val text: String = certifyPaymentEvidence.text
  override val keyValues: Map[String, Option[String]] = certifyPaymentEvidence.keyValues

  val escrowAddress = certifyPaymentEvidence.escrowAddress

  val btcBuyer = certifyPaymentEvidence.btcBuyer
  val buyer = certifyPaymentEvidence.buyer

  val btcBuyOffer = certifyPaymentEvidence.btcBuyOffer
  val fullySignedOpenTx = certifyPaymentEvidence.fullySignedOpenTx

  def unsignedFiatSentPayoutTx: PayoutTx = certifyPaymentEvidence.unsignedFiatSentPayoutTx

  def arbitratorSignedFiatSentPayoutTx: PayoutTx = unsignedFiatSentPayoutTx.addInputSigs(arbitratorPayoutTxSigs)

  def unsignedFiatNotSentPayoutTx: PayoutTx = certifyPaymentEvidence.unsignedFiatNotSentPayoutTx

  def arbitratorSignedFiatNotSentPayoutTx: PayoutTx = unsignedFiatNotSentPayoutTx.addInputSigs(arbitratorPayoutTxSigs)

  def withPayoutTx(payoutTxHash: Sha256Hash, payoutTxUpdateTime: DateTime) =
    CertifiedSettledTrade(this, payoutTxHash, payoutTxUpdateTime)
}
