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

case class TakenOffer(btcBuyOffer: BtcBuyOffer, btcSeller: BtcSeller, btcSellerOpenTxSigs: Seq[TxSig],
                      btcSellerFundPayoutTxo: Seq[TransactionOutput], cipherPaymentDetails: Array[Byte],
                      paymentDetailsKey: Option[Array[Byte]] = None) extends Template with TradeData {

  override val id: UUID = btcBuyOffer.id
  override val btcAmount: Money = btcBuyOffer.btcAmount
  override val fiatAmount: Money = btcBuyOffer.fiatAmount
  override val contract: Contract = btcBuyOffer.contract

  // decrypt payment details with seller provided AES key
  val paymentDetails: Option[String] = paymentDetailsKey.map { k =>
    new String(cipher(k, btcBuyOffer.btcBuyer, btcSeller).decrypt(cipherPaymentDetails).map(b => b.toChar))
  }

  override val text: String = btcBuyOffer.text
  override val keyValues = btcBuyOffer.keyValues ++ btcSellerKeyValues(btcSeller) ++ paymentDetailsKeyValues(paymentDetails)

  val btcBuyer = btcBuyOffer.btcBuyer

  val openAmountOK = Tx.coinTotalOutputValue(btcSeller.openTxUtxo).compareTo(BTCMoney.toCoin(btcToOpenEscrow)) >= 0
  val fundAmountOK = Tx.coinTotalOutputValue(btcSeller.fundTxUtxo).compareTo(BTCMoney.toCoin(btcToFundEscrow)) >= 0
  val amountOK = openAmountOK && fundAmountOK

  def unsignedOpenTx: OpenTx = unsignedOpenTx(btcBuyOffer.btcBuyer, btcSeller)

  def escrowAddress = unsignedOpenTx.escrowAddr

  def btcSellerSignedOpenTx: OpenTx = unsignedOpenTx.addInputSigs(btcSellerOpenTxSigs)

  def unsignedPayoutTx(fullySignedOpenTx: OpenTx): PayoutTx =
    super.unsignedPayoutTx(btcBuyOffer.btcBuyer, btcSeller, fullySignedOpenTx, btcSellerFundPayoutTxo)

  def withPaymentDetailsKey(paymentDetailsKey: Array[Byte]) =
    this.copy(paymentDetailsKey = Some(paymentDetailsKey))

  def withBtcBuyerSigs(btcBuyerOpenTxSigs: Seq[TxSig], btcBuyerPayoutTxSigs: Seq[TxSig]): SignedTakenOffer = {
    SignedTakenOffer(this, btcBuyerOpenTxSigs, btcBuyerPayoutTxSigs)
  }

  def sign(implicit btcBuyerWallet: Wallet): SignedTakenOffer = {

    val btcBuyerOpenTxSigs: Seq[TxSig] = unsignedOpenTx.sign(btcBuyerWallet).inputSigs
    val fullySignedOpenTx = btcSellerSignedOpenTx.addInputSigs(btcBuyerOpenTxSigs)
    val btcBuyerPayoutTxSigs: Seq[TxSig] = unsignedPayoutTx(fullySignedOpenTx)
      .sign(btcBuyer.escrowPubKey)(btcBuyerWallet).inputSigs

    withBtcBuyerSigs(btcBuyerOpenTxSigs, btcBuyerPayoutTxSigs)
  }

}
