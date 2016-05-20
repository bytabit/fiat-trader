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

case class TakenOffer(sellOffer: SellOffer, buyer: Buyer, buyerOpenTxSigs: Seq[TxSig],
                      buyerFundPayoutTxo: Seq[TransactionOutput], cipherFiatDeliveryDetails: Array[Byte],
                      fiatDeliveryDetailsKey: Option[Array[Byte]] = None) extends Template with TradeData {

  override val id: UUID = sellOffer.id
  override val btcAmount: Money = sellOffer.btcAmount
  override val fiatAmount: Money = sellOffer.fiatAmount
  override val contract: Contract = sellOffer.contract

  // decrypt delivery details with buyer provided AES key
  val fiatDeliveryDetails: Option[String] = fiatDeliveryDetailsKey.map { k =>
    new String(cipher(k, sellOffer.seller, buyer).decrypt(cipherFiatDeliveryDetails).map(b => b.toChar))
  }

  override val text: String = sellOffer.text
  override val keyValues = sellOffer.keyValues ++ buyerKeyValues(buyer) ++ fiatDeliveryDetailsKeyValues(fiatDeliveryDetails)

  val seller = sellOffer.seller

  val openAmountOK = Tx.coinTotalOutputValue(buyer.openTxUtxo).compareTo(BTCMoney.toCoin(btcToOpenEscrow)) >= 0
  val fundAmountOK = Tx.coinTotalOutputValue(buyer.fundTxUtxo).compareTo(BTCMoney.toCoin(btcToFundEscrow)) >= 0
  val amountOk = openAmountOK && fundAmountOK

  def unsignedOpenTx: OpenTx = unsignedOpenTx(sellOffer.seller, buyer)

  def escrowAddress = unsignedOpenTx.escrowAddr

  def buyerSignedOpenTx: OpenTx = unsignedOpenTx.addInputSigs(buyerOpenTxSigs)

  def unsignedPayoutTx(fullySignedOpenTx: OpenTx): PayoutTx =
    super.unsignedPayoutTx(sellOffer.seller, buyer, fullySignedOpenTx, buyerFundPayoutTxo)

  def withFiatDeliveryDetailsKey(fiatDeliveryDetailsKey: Array[Byte]) =
    this.copy(fiatDeliveryDetailsKey = Some(fiatDeliveryDetailsKey))

  def withSellerSigs(sellerOpenTxSigs: Seq[TxSig], sellerPayoutTxSigs: Seq[TxSig]): SignedTakenOffer = {
    SignedTakenOffer(this, sellerOpenTxSigs, sellerPayoutTxSigs)
  }

  def sign(implicit sellerWallet: Wallet): SignedTakenOffer = {

    val sellerOpenTxSigs: Seq[TxSig] = unsignedOpenTx.sign(sellerWallet).inputSigs
    val fullySignedOpenTx = buyerSignedOpenTx.addInputSigs(sellerOpenTxSigs)
    val sellerPayoutTxSigs: Seq[TxSig] = unsignedPayoutTx(fullySignedOpenTx)
      .sign(seller.escrowPubKey)(sellerWallet).inputSigs

    withSellerSigs(sellerOpenTxSigs, sellerPayoutTxSigs)
  }

}
