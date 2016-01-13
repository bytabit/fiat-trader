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
  override val keyValues = offer.keyValues ++ Map[String, Option[String]](
    "sellerId" -> Some(seller.id.toString),
    "sellerPayoutAddress" -> Some(seller.payoutAddr.toString)
  )

  val amountOK = Tx.coinTotalOutputValue(seller.openTxUtxo).compareTo(BTCMoney.toCoin(btcToOpenEscrow)) >= 0

  def unsignedOpenTx(buyer: Buyer): OpenTx = super.unsignedOpenTx(seller, buyer)

  def unsignedFundTx(buyer: Buyer): FundTx = super.unsignedFundTx(seller, buyer)

  def withBuyer(buyer: Buyer, buyerOpenTxSigs: Seq[TxSig], buyerFundPayoutTxo: Seq[TransactionOutput]) =
    TakenOffer(this, buyer, buyerOpenTxSigs, buyerFundPayoutTxo)

  def take(deliveryDetails: String)(implicit buyerWallet: Wallet): TakenOffer = {

    val buyer = Buyer(coinToOpenEscrow, coinToFundEscrow, deliveryDetails)(buyerWallet)
    val buyerOpenTxSigs: Seq[TxSig] = unsignedOpenTx(buyer).sign(buyerWallet).inputSigs
    val buyerFundPayoutTxo: Seq[TransactionOutput] = unsignedFundTx(buyer).sign(buyerWallet).outputsToEscrow

    withBuyer(buyer, buyerOpenTxSigs, buyerFundPayoutTxo)
  }
}
