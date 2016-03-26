package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money
import org.joda.time.DateTime

case class CertifiedFiatDelivery(certifyFiatEvidence: CertifyFiatEvidence,
                                 arbitratorPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = certifyFiatEvidence.id
  override val btcAmount: Money = certifyFiatEvidence.btcAmount
  override val fiatAmount: Money = certifyFiatEvidence.fiatAmount
  override val contract: Contract = certifyFiatEvidence.contract

  override val text: String = certifyFiatEvidence.text
  override val keyValues: Map[String, Option[String]] = certifyFiatEvidence.keyValues

  val escrowAddress = certifyFiatEvidence.escrowAddress

  val seller = certifyFiatEvidence.seller
  val buyer = certifyFiatEvidence.buyer

  val sellOffer = certifyFiatEvidence.sellOffer
  val fullySignedOpenTx = certifyFiatEvidence.fullySignedOpenTx

  def unsignedFiatSentPayoutTx: PayoutTx = certifyFiatEvidence.unsignedFiatSentPayoutTx

  def arbitratorSignedFiatSentPayoutTx: PayoutTx = unsignedFiatSentPayoutTx.addInputSigs(arbitratorPayoutTxSigs)

  def unsignedFiatNotSentPayoutTx: PayoutTx = certifyFiatEvidence.unsignedFiatNotSentPayoutTx

  def arbitratorSignedFiatNotSentPayoutTx: PayoutTx = unsignedFiatNotSentPayoutTx.addInputSigs(arbitratorPayoutTxSigs)

  def withPayoutTx(payoutTxHash: Sha256Hash, payoutTxUpdateTime: DateTime) =
    CertifiedSettledTrade(this, payoutTxHash, payoutTxUpdateTime)
}
