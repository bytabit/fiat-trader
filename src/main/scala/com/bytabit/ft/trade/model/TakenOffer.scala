package com.bytabit.ft.trade.model

import java.util.UUID

import com.bytabit.ft.util.BTCMoney
import com.bytabit.ft.wallet.model._
import org.bitcoinj.core.{TransactionOutput, Wallet}
import org.joda.money.Money

case class TakenOffer(sellOffer: SellOffer, buyer: Buyer, buyerOpenTxSigs: Seq[TxSig],
                      buyerFundPayoutTxo: Seq[TransactionOutput]) extends Template with TradeData {

  override val id: UUID = sellOffer.id
  override val btcAmount: Money = sellOffer.btcAmount
  override val fiatAmount: Money = sellOffer.fiatAmount
  override val contract: Contract = sellOffer.contract

  override val text: String = sellOffer.text
  override val keyValues = sellOffer.keyValues ++ Map[String, Option[String]](
    "buyerId" -> Some(buyer.id.toString),
    "buyerPayoutAddress" -> Some(buyer.payoutAddr.toString),
    "buyerFiatDeliveryDetails" -> Some(buyer.fiatDeliveryDetails)
  )

  val seller = sellOffer.seller

  val openAmountOK = Tx.coinTotalOutputValue(buyer.openTxUtxo).compareTo(BTCMoney.toCoin(btcToOpenEscrow)) >= 0
  val fundAmountOK = Tx.coinTotalOutputValue(buyer.fundTxUtxo).compareTo(BTCMoney.toCoin(btcToFundEscrow)) >= 0
  val amountOk = openAmountOK && fundAmountOK

  def unsignedOpenTx: OpenTx = unsignedOpenTx(sellOffer.seller, buyer)

  def buyerSignedOpenTx: OpenTx = unsignedOpenTx.addInputSigs(buyerOpenTxSigs)

  def unsignedPayoutTx(fullySignedOpenTx: OpenTx): PayoutTx =
    super.unsignedPayoutTx(sellOffer.seller, buyer, fullySignedOpenTx, buyerFundPayoutTxo)

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
