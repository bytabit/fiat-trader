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

import org.bitcoinj.core.TransactionOutput
import org.bytabit.ft.util.{AESCipher, BTCMoney, Monies}
import org.bytabit.ft.wallet.model.TxTools.{BTC_MINER_FEE, BTC_OP_RETURN_FEE}
import org.bytabit.ft.wallet.model._
import org.joda.money.Money

trait TradeData {

  val id: UUID
  val contract: Contract
  lazy val arbitrator = contract.arbitrator

  val fiatAmount: Money
  val btcAmount: Money

  lazy val url = arbitrator.url

  lazy val bondPercent = arbitrator.bondPercent
  lazy val btcArbitratorFee = arbitrator.btcArbitratorFee
  lazy val btcMinerFee = BTC_MINER_FEE

  lazy val btcBond = btcAmount.multipliedBy(arbitrator.bondPercent, Monies.roundingMode)

  // BTC to Open Escrow Amount
  lazy val btcToOpenEscrow = btcArbitratorFee.plus(BTC_MINER_FEE).plus(btcBond)
  lazy val coinToOpenEscrow = BTCMoney.toCoin(btcToOpenEscrow)

  // BTC Fund Escrow Amount
  lazy val btcToFundEscrow = BTC_MINER_FEE.plus(BTC_OP_RETURN_FEE).plus(btcAmount)
  lazy val coinToFundEscrow = BTCMoney.toCoin(btcToFundEscrow)

  // BTC Payout to Seller Amount (happy path)
  lazy val btcSellerPayout = btcArbitratorFee.plus(btcBond).plus(btcAmount)

  // BTC Payout to Buyer Amount (happy path)
  lazy val btcBuyerPayout = btcArbitratorFee.plus(btcBond)

  // BTC Dispute Payout to Buyer or Seller Amount (dispute path)
  lazy val btcDisputeWinnerPayout = btcArbitratorFee.plus(btcBond).plus(btcBond).plus(btcAmount)

  // BTC Cancel Trade Payout to Buyer Amount (cancel path)
  lazy val btcBuyerCancelPayout = btcArbitratorFee.plus(btcBond)

  // BTC Cancel Trade Payout to Seller Amount (cancel path)
  lazy val btcSellerCancelPayout = btcArbitratorFee.plus(btcBond)

  // AES cipher for fiat delivery details, init Vector is first 16 bytes of escrow address hash
  def cipher(key: Array[Byte], seller: Seller, buyer: Buyer) =
    AESCipher(key, unsignedOpenTx(seller, buyer).escrowAddr.getHash160.slice(0, AESCipher.AES_IV_LEN))

  // unsigned open escrow tx
  def unsignedOpenTx(seller: Seller, buyer: Buyer) =
    OpenTx(BTCMoney.toCoin(btcToOpenEscrow), arbitrator, seller, buyer)

  // unsigned fund escrow tx
  def unsignedFundTx(seller: Seller, buyer: Buyer, deliveryDetailsKey: Array[Byte]) =
    FundTx(BTCMoney.toCoin(btcToFundEscrow), arbitrator, seller, buyer, deliveryDetailsKey)

  // unsigned happy path payout escrow tx
  def unsignedPayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]) =
    PayoutTx(arbitrator, seller, buyer, BTCMoney.toCoin(Some(btcSellerPayout)), BTCMoney.toCoin(Some(btcBuyerPayout)),
      None, signedOpenTx, Some(signedFundTxOutputs))

  // unsigned cancel path payout escrow tx
  def unsignedCancelPayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx) =
    PayoutTx(arbitrator, seller, buyer, BTCMoney.toCoin(Some(btcSellerCancelPayout)), BTCMoney.toCoin(Some(btcBuyerCancelPayout)),
      None, signedOpenTx, None)

  // unsigned seller wins dispute path payout escrow tx
  def unsignedFiatSentPayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]) =
    PayoutTx(arbitrator, seller, buyer, BTCMoney.toCoin(Some(btcDisputeWinnerPayout)), None, BTCMoney.toCoin(Some(btcArbitratorFee)),
      signedOpenTx, Some(signedFundTxOutputs))

  // unsigned buyer wins dispute path payout escrow tx
  def unsignedFiatNotSentPayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]) =
    PayoutTx(arbitrator, seller, buyer, None, BTCMoney.toCoin(Some(btcDisputeWinnerPayout)), BTCMoney.toCoin(Some(btcArbitratorFee)),
      signedOpenTx, Some(signedFundTxOutputs))
}


