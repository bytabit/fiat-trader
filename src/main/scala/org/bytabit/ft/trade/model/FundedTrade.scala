package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.Sha256Hash
import org.joda.money.Money
import org.joda.time.DateTime

case class FundedTrade(openedTrade: OpenedTrade, fundTxHash: Sha256Hash, fundTxUpdateTime: DateTime,
                       fiatDeliveryDetailsKey: Option[Array[Byte]])

  extends Template with TradeData {

  override val id: UUID = openedTrade.id
  override val btcAmount: Money = openedTrade.btcAmount
  override val fiatAmount: Money = openedTrade.fiatAmount
  override val contract: Contract = openedTrade.contract

  override val text: String = openedTrade.text
  override val keyValues: Map[String, Option[String]] = openedTrade.keyValues

  val escrowAddress = openedTrade.escrowAddress

  val sellerSignedPayoutTx = openedTrade.signedTakenOffer.sellerSignedPayoutTx

  val unsignedPayoutTx = openedTrade.signedTakenOffer.unsignedPayoutTx

  val buyer = openedTrade.signedTakenOffer.buyer

  def certifyFiatRequested(evidence: Option[Array[Byte]]) =
    CertifyFiatEvidence(this, evidence.toSeq)

  def withFiatDeliveryDetailsKey(fiatDeliveryDetailsKey: Array[Byte]) =
    this.copy(fiatDeliveryDetailsKey = Some(fiatDeliveryDetailsKey))


  def withPayoutTx(payoutTxHash: Sha256Hash, payoutTxUpdateTime: DateTime) =
    SettledTrade(this, payoutTxHash, payoutTxUpdateTime)
}
