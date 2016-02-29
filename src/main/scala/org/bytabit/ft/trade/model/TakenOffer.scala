package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.{TransactionOutput, Wallet}
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
  override val keyValues = sellOffer.keyValues ++ Map[String, Option[String]](
    "buyerId" -> Some(buyer.id.toString),
    "buyerPayoutAddress" -> Some(buyer.payoutAddr.toString),
    "buyerFiatDeliveryDetails" -> fiatDeliveryDetails
  )

  val seller = sellOffer.seller

  val openAmountOK = Tx.coinTotalOutputValue(buyer.openTxUtxo).compareTo(BTCMoney.toCoin(btcToOpenEscrow)) >= 0
  val fundAmountOK = Tx.coinTotalOutputValue(buyer.fundTxUtxo).compareTo(BTCMoney.toCoin(btcToFundEscrow)) >= 0
  val amountOk = openAmountOK && fundAmountOK

  def unsignedOpenTx: OpenTx = unsignedOpenTx(sellOffer.seller, buyer)

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
