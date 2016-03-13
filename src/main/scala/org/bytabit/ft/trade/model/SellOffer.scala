package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.{TransactionOutput, Wallet}
import org.bytabit.ft.util.BTCMoney
import org.bytabit.ft.wallet.model._
import org.joda.money.Money

case class SellOffer(offer: Offer, seller: Seller) extends Template with TradeData {

  override val id: UUID = offer.id
  override val btcAmount: Money = offer.btcAmount
  override val fiatAmount: Money = offer.fiatAmount
  override val contract: Contract = offer.contract

  override val text: String = offer.text
  override val keyValues = offer.keyValues ++ sellerKeyValues(seller)

  val amountOK = Tx.coinTotalOutputValue(seller.openTxUtxo).compareTo(BTCMoney.toCoin(btcToOpenEscrow)) >= 0

  def unsignedOpenTx(buyer: Buyer): OpenTx = super.unsignedOpenTx(seller, buyer)

  def unsignedFundTx(buyer: Buyer, deliveryDetailsKey: Array[Byte]): FundTx = super.unsignedFundTx(seller, buyer, deliveryDetailsKey)

  def withBuyer(buyer: Buyer, buyerOpenTxSigs: Seq[TxSig], buyerFundPayoutTxo: Seq[TransactionOutput],
                cipherFiatDeliveryDetails: Array[Byte], fiatDeliveryDetailsKey: Option[Array[Byte]] = None) =
    TakenOffer(this, buyer, buyerOpenTxSigs, buyerFundPayoutTxo, cipherFiatDeliveryDetails, fiatDeliveryDetailsKey)

  def take(fiatDeliveryDetails: String, fiatDeliveryDetailsKey: Array[Byte])(implicit buyerWallet: Wallet): TakenOffer = {

    val buyer = Buyer(coinToOpenEscrow, coinToFundEscrow)(buyerWallet)
    val buyerOpenTxSigs: Seq[TxSig] = unsignedOpenTx(buyer).sign(buyerWallet).inputSigs
    val buyerFundPayoutTxo: Seq[TransactionOutput] = unsignedFundTx(buyer, fiatDeliveryDetailsKey).sign(buyerWallet).outputsToEscrow
    val cipherFiatDeliveryDetails: Array[Byte] =
      cipher(fiatDeliveryDetailsKey, seller, buyer).encrypt(fiatDeliveryDetails.map(_.toByte).toArray)

    withBuyer(buyer, buyerOpenTxSigs, buyerFundPayoutTxo, cipherFiatDeliveryDetails, Some(fiatDeliveryDetailsKey))
  }

}
