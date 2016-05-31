/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  val paymentDetailsKey = signedTakenOffer.takenOffer.paymentDetailsKey

  def withFundTx(fundTxHash: Sha256Hash, fundTxUpdateTime: DateTime, paymentDetailsKey: Option[Array[Byte]]) =
    FundedTrade(this, fundTxHash, fundTxUpdateTime, paymentDetailsKey)
}
