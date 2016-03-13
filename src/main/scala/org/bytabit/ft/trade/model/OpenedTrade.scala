package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.Sha256Hash
import org.joda.money.Money
import org.joda.time.DateTime

case class OpenedTrade(signedTakenOffer: SignedTakenOffer, openTxHash: Sha256Hash, openTxUpdateTime: DateTime)
  extends Template with TradeData {

  override val id: UUID = signedTakenOffer.id
  override val btcAmount: Money = signedTakenOffer.btcAmount
  override val fiatAmount: Money = signedTakenOffer.fiatAmount
  override val contract: Contract = signedTakenOffer.contract

  override val text: String = signedTakenOffer.text
  override val keyValues: Map[String, Option[String]] = signedTakenOffer.keyValues

  val escrowAddress = signedTakenOffer.escrowAddress

  val fiatDeliveryDetailsKey = signedTakenOffer.takenOffer.fiatDeliveryDetailsKey

  def withFundTx(fundTxHash: Sha256Hash, fundTxUpdateTime: DateTime, fiatDeliveryDetailsKey: Option[Array[Byte]]) =
    FundedTrade(this, fundTxHash, fundTxUpdateTime, fiatDeliveryDetailsKey)
}
