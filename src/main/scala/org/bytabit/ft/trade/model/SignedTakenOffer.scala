package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.Wallet
import org.bytabit.ft.wallet.model.{FundTx, OpenTx, PayoutTx, TxSig}
import org.joda.money.Money

case class SignedTakenOffer(takenOffer: TakenOffer, sellerOpenTxSigs: Seq[TxSig],
                            sellerPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = takenOffer.id
  override val btcAmount: Money = takenOffer.btcAmount
  override val fiatAmount: Money = takenOffer.fiatAmount
  override val contract: Contract = takenOffer.contract

  override val text: String = takenOffer.text
  override val keyValues: Map[String, Option[String]] = takenOffer.keyValues

  val seller = takenOffer.seller
  val buyer = takenOffer.buyer

  def unsignedOpenTx: OpenTx = takenOffer.unsignedOpenTx

  def fullySignedOpenTx: OpenTx = takenOffer.buyerSignedOpenTx.addInputSigs(sellerOpenTxSigs)

  def unsignedFundTx: FundTx = super.unsignedFundTx(seller, buyer)

  def unsignedPayoutTx: PayoutTx = takenOffer.unsignedPayoutTx(fullySignedOpenTx)

  def sellerSignedPayoutTx: PayoutTx = unsignedPayoutTx.addInputSigs(sellerPayoutTxSigs)

  def unsignedFiatSentPayoutTx: PayoutTx = super.unsignedFiatSentPayoutTx(seller, buyer, fullySignedOpenTx,
    takenOffer.buyerFundPayoutTxo)

  def unsignedFiatNotSentPayoutTx: PayoutTx = super.unsignedFiatNotSentPayoutTx(seller, buyer, fullySignedOpenTx,
    takenOffer.buyerFundPayoutTxo)

  def notarizeFiatSent(implicit notaryWallet: Wallet): NotarizedFiatSent = {
    val notarizedFiatSentPayoutTx: PayoutTx = unsignedFiatSentPayoutTx.sign(notary.escrowPubKey)

    NotarizedFiatSent(this, notarizedFiatSentPayoutTx.inputSigs)
  }

  def notarizeFiatNotSent(implicit notaryWallet: Wallet): NotarizedFiatNotSent = {
    val notarizedFiatSentPayoutTx: PayoutTx = unsignedFiatNotSentPayoutTx.sign(notary.escrowPubKey)

    NotarizedFiatNotSent(this, notarizedFiatSentPayoutTx.inputSigs)
  }
}
