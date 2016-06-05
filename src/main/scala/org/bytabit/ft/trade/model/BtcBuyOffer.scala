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

import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.wallet.Wallet
import org.bytabit.ft.util.BTCMoney
import org.bytabit.ft.wallet.model._
import org.joda.money.Money
import org.joda.time.DateTime

case class BtcBuyOffer(offer: Offer, btcBuyer: BtcBuyer, posted: Option[DateTime] = None) extends Template with TradeData {

  override val id: UUID = offer.id
  override val btcAmount: Money = offer.btcAmount
  override val fiatAmount: Money = offer.fiatAmount
  override val contract: Contract = offer.contract

  override val text: String = offer.text
  override val keyValues = offer.keyValues ++ btcBuyerKeyValues(btcBuyer)

  val amountOK = Tx.coinTotalOutputValue(btcBuyer.openTxUtxo).compareTo(BTCMoney.toCoin(btcToOpenEscrow)) >= 0

  def unsignedOpenTx(btcSeller: BtcSeller): OpenTx = super.unsignedOpenTx(btcBuyer, btcSeller)

  def unsignedFundTx(btcSeller: BtcSeller, paymentDetailsKey: Array[Byte]): FundTx = super.unsignedFundTx(btcBuyer, btcSeller, paymentDetailsKey)

  def withBtcSeller(btcSeller: BtcSeller, btcSellerOpenTxSigs: Seq[TxSig], btcSellerFundPayoutTxo: Seq[TransactionOutput],
                    cipherPaymentDetails: Array[Byte], paymentDetailsKey: Option[Array[Byte]] = None) =
    TakenOffer(this, btcSeller, btcSellerOpenTxSigs, btcSellerFundPayoutTxo, cipherPaymentDetails, paymentDetailsKey)

  def take(paymentDetails: String, paymentDetailsKey: Array[Byte])(implicit btcSellerWallet: Wallet): TakenOffer = {

    val btcSeller = BtcSeller(coinToOpenEscrow, coinToFundEscrow)(btcSellerWallet)
    val btcSellerOpenTxSigs: Seq[TxSig] = unsignedOpenTx(btcSeller).sign(btcSellerWallet).inputSigs
    val btcSellerFundPayoutTxo: Seq[TransactionOutput] = unsignedFundTx(btcSeller, paymentDetailsKey).sign(btcSellerWallet).outputsToEscrow
    val cipherPaymentDetails: Array[Byte] =
      cipher(paymentDetailsKey, btcBuyer, btcSeller).encrypt(paymentDetails.map(_.toByte).toArray)

    withBtcSeller(btcSeller, btcSellerOpenTxSigs, btcSellerFundPayoutTxo, cipherPaymentDetails, Some(paymentDetailsKey))
  }

  def withPosted(posted: DateTime) = this.copy(posted = Some(posted))

}
