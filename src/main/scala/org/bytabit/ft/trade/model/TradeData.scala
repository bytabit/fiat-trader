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

  // BTC Payout to btc buyer Amount (happy path)
  lazy val btcBuyerPayout = btcArbitratorFee.plus(btcBond).plus(btcAmount)

  // BTC Payout to seller Amount (happy path)
  lazy val btcSellerPayout = btcArbitratorFee.plus(btcBond)

  // BTC Dispute Payout to BTC Buyer or Seller Amount (dispute path)
  lazy val btcDisputeWinnerPayout = btcArbitratorFee.plus(btcBond).plus(btcBond).plus(btcAmount)

  // BTC Cancel Trade Payout to btc seller Amount (cancel path)
  lazy val btcSellerCancelPayout = btcArbitratorFee.plus(btcBond)

  // BTC Cancel Trade Payout to btc buyer Amount (cancel path)
  lazy val btcBuyerCancelPayout = btcArbitratorFee.plus(btcBond)

  // AES cipher for fiat payment details, init Vector is first 16 bytes of escrow address hash
  def cipher(key: Array[Byte], btcBuyer: BtcBuyer, btcSeller: BtcSeller) =
    AESCipher(key, unsignedOpenTx(btcBuyer, btcSeller).escrowAddr.getHash160.slice(0, AESCipher.AES_IV_LEN))

  // unsigned open escrow tx
  def unsignedOpenTx(btcBuyer: BtcBuyer, btcSeller: BtcSeller) =
    OpenTx(BTCMoney.toCoin(btcToOpenEscrow), arbitrator, btcBuyer, btcSeller)

  // unsigned fund escrow tx
  def unsignedFundTx(btcBuyer: BtcBuyer, btcSeller: BtcSeller, paymentDetailsKey: Array[Byte]) =
    FundTx(BTCMoney.toCoin(btcToFundEscrow), arbitrator, btcBuyer, btcSeller, paymentDetailsKey)

  // unsigned happy path payout escrow tx
  def unsignedPayoutTx(btcBuyer: BtcBuyer, btcSeller: BtcSeller, signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]) =
    PayoutTx(arbitrator, btcBuyer, btcSeller, BTCMoney.toCoin(Some(btcBuyerPayout)), BTCMoney.toCoin(Some(btcSellerPayout)),
      None, signedOpenTx, Some(signedFundTxOutputs))

  // unsigned cancel path payout escrow tx
  def unsignedCancelPayoutTx(btcBuyer: BtcBuyer, btcSeller: BtcSeller, signedOpenTx: OpenTx) =
    PayoutTx(arbitrator, btcBuyer, btcSeller, BTCMoney.toCoin(Some(btcBuyerCancelPayout)), BTCMoney.toCoin(Some(btcSellerCancelPayout)),
      None, signedOpenTx, None)

  // unsigned btc buyer wins dispute path payout escrow tx
  def unsignedFiatSentPayoutTx(btcBuyer: BtcBuyer, btcSeller: BtcSeller, signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]) =
    PayoutTx(arbitrator, btcBuyer, btcSeller, BTCMoney.toCoin(Some(btcDisputeWinnerPayout)), None, BTCMoney.toCoin(Some(btcArbitratorFee)),
      signedOpenTx, Some(signedFundTxOutputs))

  // unsigned btc seller wins dispute path payout escrow tx
  def unsignedFiatNotSentPayoutTx(btcBuyer: BtcBuyer, btcSeller: BtcSeller, signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]) =
    PayoutTx(arbitrator, btcBuyer, btcSeller, None, BTCMoney.toCoin(Some(btcDisputeWinnerPayout)), BTCMoney.toCoin(Some(btcArbitratorFee)),
      signedOpenTx, Some(signedFundTxOutputs))
}


