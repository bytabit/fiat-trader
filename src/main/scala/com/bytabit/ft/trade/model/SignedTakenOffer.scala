package com.bytabit.ft.trade.model

import java.util.UUID

import com.bytabit.ft.wallet.model.{FundTx, OpenTx, PayoutTx, TxSig}
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

}
