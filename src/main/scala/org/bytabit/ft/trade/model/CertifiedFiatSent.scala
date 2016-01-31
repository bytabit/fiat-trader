package org.bytabit.ft.trade.model

import java.util.UUID

import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money

case class CertifiedFiatSent(certifyFiatRequested: CertifyFiatEvidence,
                             notaryPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = certifyFiatRequested.id
  override val btcAmount: Money = certifyFiatRequested.btcAmount
  override val fiatAmount: Money = certifyFiatRequested.fiatAmount
  override val contract: Contract = certifyFiatRequested.contract

  override val text: String = certifyFiatRequested.text
  override val keyValues: Map[String, Option[String]] = certifyFiatRequested.keyValues

  def unsignedPayoutTx: PayoutTx = certifyFiatRequested.unsignedFiatSentPayoutTx

  def notarySignedPayoutTx: PayoutTx = unsignedPayoutTx.addInputSigs(notaryPayoutTxSigs)

}
