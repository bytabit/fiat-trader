package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.wallet.model.{PayoutTx, TxSig}
import org.joda.money.Money
import org.joda.time.DateTime

case class CertifiedFiatDelivery(certifyFiatEvidence: CertifyFiatEvidence,
                                 notaryPayoutTxSigs: Seq[TxSig]) extends Template with TradeData {

  override val id: UUID = certifyFiatEvidence.id
  override val btcAmount: Money = certifyFiatEvidence.btcAmount
  override val fiatAmount: Money = certifyFiatEvidence.fiatAmount
  override val contract: Contract = certifyFiatEvidence.contract

  override val text: String = certifyFiatEvidence.text
  override val keyValues: Map[String, Option[String]] = certifyFiatEvidence.keyValues

  val escrowAddress = certifyFiatEvidence.fundedTrade.escrowAddress

  val seller = certifyFiatEvidence.seller
  val buyer = certifyFiatEvidence.buyer

  val sellOffer = certifyFiatEvidence.sellOffer
  val fullySignedOpenTx = certifyFiatEvidence.fullySignedOpenTx

  def unsignedFiatSentPayoutTx: PayoutTx = certifyFiatEvidence.unsignedFiatSentPayoutTx

  def notarySignedFiatSentPayoutTx: PayoutTx = unsignedFiatSentPayoutTx.addInputSigs(notaryPayoutTxSigs)

  def unsignedFiatNotSentPayoutTx: PayoutTx = certifyFiatEvidence.unsignedFiatNotSentPayoutTx

  def notarySignedFiatNotSentPayoutTx: PayoutTx = unsignedFiatNotSentPayoutTx.addInputSigs(notaryPayoutTxSigs)

  def withPayoutTx(payoutTxHash: Sha256Hash, payoutTxUpdateTime: DateTime) =
    CertifiedSettledTrade(this, payoutTxHash, payoutTxUpdateTime)
}
