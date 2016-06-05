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
import org.bytabit.ft.util.AESCipher
import org.bytabit.ft.wallet.model.{FundTx, OpenTx, PayoutTx, TxSig}
import org.joda.money.Money
import org.joda.time.DateTime

case class SignedTakenOffer(takenOffer: TakenOffer, btcBuyerOpenTxSigs: Seq[TxSig],
                            btcBuyerPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = takenOffer.id
  override val btcAmount: Money = takenOffer.btcAmount
  override val fiatAmount: Money = takenOffer.fiatAmount
  override val contract: Contract = takenOffer.contract

  override val text: String = takenOffer.text
  override val keyValues: Map[String, Option[String]] = takenOffer.keyValues

  val btcBuyer = takenOffer.btcBuyer
  val btcSeller = takenOffer.btcSeller

  def unsignedOpenTx: OpenTx = takenOffer.unsignedOpenTx

  def escrowAddress = takenOffer.escrowAddress

  def fullySignedOpenTx: OpenTx = takenOffer.btcSellerSignedOpenTx.addInputSigs(btcBuyerOpenTxSigs)

  def unsignedFundTx: FundTx = super.unsignedFundTx(btcBuyer, btcSeller,
    takenOffer.paymentDetailsKey.getOrElse(Array.fill[Byte](AESCipher.AES_KEY_LEN)(0)))

  def unsignedPayoutTx: PayoutTx = takenOffer.unsignedPayoutTx(fullySignedOpenTx)

  def btcBuyerSignedPayoutTx: PayoutTx = unsignedPayoutTx.addInputSigs(btcBuyerPayoutTxSigs)

  def withOpenTx(openTxHash: Sha256Hash, openTxUpdateTime: DateTime) = OpenedTrade(this, openTxHash, openTxUpdateTime)
}
