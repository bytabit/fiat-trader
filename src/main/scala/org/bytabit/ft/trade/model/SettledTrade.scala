package org.bytabit.ft.trade.model

import java.util.UUID

import org.bitcoinj.core.Sha256Hash
import org.joda.money.Money
import org.joda.time.DateTime

case class SettledTrade(fundedTrade: FundedTrade, payoutTxHash: Sha256Hash, payoutTxUpdateTime: DateTime)
  extends Template with TradeData {

  override val id: UUID = fundedTrade.id
  override val btcAmount: Money = fundedTrade.btcAmount
  override val fiatAmount: Money = fundedTrade.fiatAmount
  override val contract: Contract = fundedTrade.contract

  override val text: String = fundedTrade.text
  override val keyValues: Map[String, Option[String]] = fundedTrade.keyValues

  val escrowAddress = fundedTrade.escrowAddress
}
