package org.bytabit.ft.trade.model

import java.util.UUID

import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money

case class CertifiedFiatSent(certifyFiatEvidence: CertifyFiatEvidence,
                             notaryPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = certifyFiatEvidence.id
  override val btcAmount: Money = certifyFiatEvidence.btcAmount
  override val fiatAmount: Money = certifyFiatEvidence.fiatAmount
  override val contract: Contract = certifyFiatEvidence.contract

  override val text: String = certifyFiatEvidence.text
  override val keyValues: Map[String, Option[String]] = certifyFiatEvidence.keyValues

  val sellOffer = certifyFiatEvidence.sellOffer

  def unsignedPayoutTx: PayoutTx = certifyFiatEvidence.unsignedFiatSentPayoutTx

  def notarySignedPayoutTx: PayoutTx = unsignedPayoutTx.addInputSigs(notaryPayoutTxSigs)

}
