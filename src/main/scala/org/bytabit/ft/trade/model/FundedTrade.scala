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
import org.joda.money.Money
import org.joda.time.DateTime

case class FundedTrade(openedTrade: OpenedTrade, fundTxHash: Sha256Hash, fundTxUpdateTime: DateTime,
                       paymentDetailsKey: Option[Array[Byte]])

  extends Template with TradeData {

  override val id: UUID = openedTrade.id
  override val btcAmount: Money = openedTrade.btcAmount
  override val fiatAmount: Money = openedTrade.fiatAmount
  override val contract: Contract = openedTrade.contract

  override val text: String = openedTrade.text
  override val keyValues: Map[String, Option[String]] = openedTrade.keyValues

  val escrowAddress = openedTrade.escrowAddress

  val sellerSignedPayoutTx = openedTrade.signedTakenOffer.sellerSignedPayoutTx

  val unsignedPayoutTx = openedTrade.signedTakenOffer.unsignedPayoutTx

  val seller = openedTrade.signedTakenOffer.seller
  val buyer = openedTrade.signedTakenOffer.buyer
  val cipherPaymentDetails = openedTrade.signedTakenOffer.takenOffer.cipherPaymentDetails

  // decrypt payment details with buyer provided AES key
  val paymentDetails: String = paymentDetailsKey.map { k =>
    new String(cipher(k, seller, buyer).decrypt(cipherPaymentDetails).map(b => b.toChar))
  }.getOrElse("UNKNOWN")

  def certifyFiatRequested(evidence: Option[Array[Byte]]) =
    CertifyPaymentEvidence(this, evidence.toSeq)

  def withPaymentDetailsKey(paymentDetailsKey: Array[Byte]) =
    this.copy(paymentDetailsKey = Some(paymentDetailsKey))

  def withPayoutTx(payoutTxHash: Sha256Hash, payoutTxUpdateTime: DateTime) =
    SettledTrade(this, payoutTxHash, payoutTxUpdateTime)
}
