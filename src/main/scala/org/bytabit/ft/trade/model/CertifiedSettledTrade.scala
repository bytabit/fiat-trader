package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.Sha256Hash
import org.joda.money.Money
import org.joda.time.DateTime

case class CertifiedSettledTrade(certifiedFiatDelivery: CertifiedFiatDelivery, payoutTxHash: Sha256Hash, payoutTxUpdateTime: DateTime)
  extends Template with TradeData {

  override val id: UUID = certifiedFiatDelivery.id
  override val btcAmount: Money = certifiedFiatDelivery.btcAmount
  override val fiatAmount: Money = certifiedFiatDelivery.fiatAmount
  override val contract: Contract = certifiedFiatDelivery.contract

  override val text: String = certifiedFiatDelivery.text
  override val keyValues: Map[String, Option[String]] = certifiedFiatDelivery.keyValues

  val escrowAddress = certifiedFiatDelivery.escrowAddress
}
