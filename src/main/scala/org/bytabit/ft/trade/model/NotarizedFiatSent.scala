package org.bytabit.ft.trade.model

import java.util.UUID

import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money

case class NotarizedFiatSent(signedTakenOffer: SignedTakenOffer,
                             notaryPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = signedTakenOffer.id
  override val btcAmount: Money = signedTakenOffer.btcAmount
  override val fiatAmount: Money = signedTakenOffer.fiatAmount
  override val contract: Contract = signedTakenOffer.contract

  override val text: String = signedTakenOffer.text
  override val keyValues: Map[String, Option[String]] = signedTakenOffer.keyValues

  def unsignedPayoutTx: PayoutTx = signedTakenOffer.unsignedFiatSentPayoutTx

  def notarySignedPayoutTx: PayoutTx = unsignedPayoutTx.addInputSigs(notaryPayoutTxSigs)

}
