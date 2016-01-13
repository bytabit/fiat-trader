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

package com.bytabit.ft.trade.model

import java.util.UUID

import com.bytabit.ft.util.{BTCMoney, Monies}
import com.bytabit.ft.wallet.model.TxTools.{BTC_MINER_FEE, BTC_OP_RETURN_FEE}
import com.bytabit.ft.wallet.model._
import org.bitcoinj.core.TransactionOutput
import org.joda.money.Money

trait TradeData {

  val id: UUID
  val contract: Contract
  lazy val notary = contract.notary

  val fiatAmount: Money
  val btcAmount: Money

  lazy val url = notary.url

  lazy val bondPercent = notary.bondPercent
  lazy val btcNotaryFee = notary.btcNotaryFee

  lazy val btcBond = btcAmount.multipliedBy(notary.bondPercent, Monies.roundingMode)

  // BTC to Open Escrow Amount
  lazy val btcToOpenEscrow = btcNotaryFee.plus(BTC_MINER_FEE).plus(btcBond)
  lazy val coinToOpenEscrow = BTCMoney.toCoin(btcToOpenEscrow)

  // BTC Fund Escrow Amount
  lazy val btcToFundEscrow = BTC_MINER_FEE.plus(BTC_OP_RETURN_FEE).plus(btcAmount)
  lazy val coinToFundEscrow = BTCMoney.toCoin(btcToFundEscrow)

  // BTC Payout to Seller Amount (happy path)
  lazy val btcSellerPayout = btcNotaryFee.plus(btcBond).plus(btcAmount)

  // BTC Payout to Buyer Amount (happy path)
  lazy val btcBuyerPayout = btcNotaryFee.plus(btcBond)

  // BTC Dispute Payout to Buyer or Seller Amount (dispute path)
  lazy val btcDisputeWinnerPayout = btcNotaryFee.plus(btcBond).plus(btcBond).plus(btcAmount)

  // BTC Cancel Trade Payout to Buyer Amount (cancel path)
  lazy val btcBuyerCancelPayout = btcNotaryFee.plus(btcBond)

  // BTC Cancel Trade Payout to Seller Amount (cancel path)
  lazy val btcSellerCancelPayout = btcNotaryFee.plus(btcBond)

  // unsigned open escrow tx
  def unsignedOpenTx(seller: Seller, buyer: Buyer) =
    OpenTx(BTCMoney.toCoin(btcToOpenEscrow), notary, seller, buyer)

  // unsigned fund escrow tx
  def unsignedFundTx(seller: Seller, buyer: Buyer) =
    FundTx(BTCMoney.toCoin(btcToFundEscrow), notary, seller, buyer)

  // unsigned happy path payout escrow tx
  def unsignedPayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]) =
    PayoutTx(notary, seller, buyer, BTCMoney.toCoin(Some(btcSellerPayout)), BTCMoney.toCoin(Some(btcBuyerPayout)),
      None, signedOpenTx, Some(signedFundTxOutputs))

  // unsigned cancel path payout escrow tx
  def unsignedCancelPayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx) =
    PayoutTx(notary, seller, buyer, BTCMoney.toCoin(Some(btcSellerCancelPayout)), BTCMoney.toCoin(Some(btcBuyerCancelPayout)),
      None, signedOpenTx, None)

  // unsigned seller wins dispute path payout escrow tx
  def unsignedSellerWinsDisputePayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx, signedFundTxOutputs: List[TransactionOutput]) =
    PayoutTx(notary, seller, buyer, BTCMoney.toCoin(Some(btcDisputeWinnerPayout)), None, BTCMoney.toCoin(Some(btcNotaryFee)),
      signedOpenTx, Some(signedFundTxOutputs))

  // unsigned buyer wins dispute path payout escrow tx
  def unsignedBuyerWinsDisputePayoutTx(seller: Seller, buyer: Buyer, signedOpenTx: OpenTx, signedFundTxOutputs: List[TransactionOutput]) =
    PayoutTx(notary, seller, buyer, None, BTCMoney.toCoin(Some(btcDisputeWinnerPayout)), BTCMoney.toCoin(Some(btcNotaryFee)),
      signedOpenTx, Some(signedFundTxOutputs))
}

//case class OfferData(offer:Offer) extends TradeData {
//
//  override val id = offer.id
//
//  def withSeller(seller: Seller): SellOfferData = {
//    //implicit sellerWallet: Wallet) = {
//    //val seller = Seller(coinToOpenEscrow)
//    val updatedContract = contract.withAmounts(fiatAmount, btcAmount).withSeller(seller)
//    SellOfferData(id, updatedContract, fiatAmount, btcAmount, seller)
//  }
//}
//
//case class SellOfferData(id: UUID, contract: Contract,
//                     fiatAmount: Money, btcAmount: Money,
//                     seller: Seller) extends TradeData {
//
//  //  def withBuyer(fiatDeliveryDetails: String)(implicit buyerWallet: Wallet):TakenSellOffer = {
//  //    val buyer: Buyer = Buyer(coinToOpenEscrow, coinToFundEscrow, fiatDeliveryDetails)
//  //    withBuyer(buyer)(buyerWallet)
//  //  }
//
//  def unsignedOpenTx(buyer: Buyer): OpenTx = super.unsignedOpenTx(seller, buyer)
//
//  def unsignedFundTx(buyer: Buyer): FundTx = super.unsignedFundTx(seller, buyer)
//
//  def withBuyer(buyer: Buyer, buyerOpenTxSigs: Seq[TxSig], buyerFundPayoutTxo: Seq[TransactionOutput]): TakenSellOfferData = {
//    //(implicit buyerWallet: Wallet):TakenSellOffer = {
//    //val signedOpenTx = unsignedOpenTx(seller, buyer).sign
//    //val signedFundTx = unsignedFundTx(seller, buyer).sign
//    //val buyerOpenTxSigs: Seq[TxSig] = signedOpenTx.inputSigs
//    //val buyerFundPayoutTxo: Seq[TransactionOutput] = signedFundTx.outputsToEscrow
//
//    TakenSellOfferData(id, contract.withBuyer(buyer), fiatAmount, btcAmount, seller, buyer,
//      buyerOpenTxSigs, buyerFundPayoutTxo)
//  }
//}
//
//case class TakenSellOfferData(id: UUID, contract: Contract,
//                          fiatAmount: Money, btcAmount: Money,
//                          seller: Seller, buyer: Buyer,
//                          buyerOpenTxSigs: Seq[TxSig],
//                          buyerFundPayoutTxo: Seq[TransactionOutput]) extends TradeData {
//
//  def unsignedOpenTx: OpenTx = unsignedOpenTx(seller, buyer)
//
//  def buyerSignedOpenTx: OpenTx =
//    unsignedOpenTx.addInputSigs(buyerOpenTxSigs)
//
//  def unsignedPayoutTx(signedOpenTx: OpenTx): PayoutTx =
//    super.unsignedPayoutTx(seller, buyer, signedOpenTx, buyerFundPayoutTxo)
//
//  def withSellerSigs(sellerOpenTxSigs: Seq[TxSig], sellerPayoutTxSigs: Seq[TxSig]): SignedSellOfferData = {
//    //(implicit sellerWallet: Wallet) = {
//    //    val signedOpenTx = unsignedOpenTx(seller, buyer).sign.addInputSigs(buyerOpenTxSigs)
//    //    val sellerOpenTxSigs: Seq[TxSig] = signedOpenTx.inputSigs
//    //    val signedPayoutTx = unsignedPayoutTx(seller, buyer, signedOpenTx, buyerFundPayoutTxo).sign(seller.escrowPubKey)
//    //    val sellerPayoutTxSigs: Seq[TxSig] = signedPayoutTx.inputSigs
//
//    SignedSellOfferData(id, contract, fiatAmount, btcAmount, seller, buyer, sellerOpenTxSigs, sellerPayoutTxSigs)
//  }
//}
//
//case class SignedSellOfferData(id: UUID, contract: Contract,
//                             fiatAmount: Money, btcAmount: Money,
//                             seller: Seller, buyer: Buyer,
//                             sellerOpenTxSigs: Seq[TxSig],
//                             sellerPayoutTxSigs: Seq[TxSig]) extends TradeData {
//
//  def sellerSignedOpenTx: OpenTx = super.unsignedOpenTx(seller, buyer).addInputSigs(sellerOpenTxSigs)
//
//  def unsignedFundTx: FundTx = super.unsignedFundTx(seller, buyer)
//
//  def sellerSignedPayoutTx(signedOpenTx: OpenTx, signedFundTxOutputs: Seq[TransactionOutput]): PayoutTx =
//    super.unsignedPayoutTx(seller, buyer, signedOpenTx, signedFundTxOutputs).addInputSigs(sellerPayoutTxSigs)
//
//  //    def withBuyerSigs(signedOpenTx: OpenTx, signedFundTx: FundTx, signedPayoutTx: PayoutTx): FullySignedOffer = {
//  //      //implicit buyerWallet: Wallet) = {
//  //
//  //      //    val signedOpenTx = unsignedOpenTx(seller, buyer).sign.addInputSigs(sellerOpenTxSigs)
//  //      //    val signedFundTx = unsignedFundTx(seller, buyer).sign
//  //      //    val signedPayoutTx = unsignedPayoutTx(seller, buyer, signedOpenTx, signedFundTx.outputsToEscrow)
//  //      //      .addInputSigs(sellerPayoutTxSigs).sign(buyer.escrowPubKey)
//  //
//  //      FullySignedSellOffer(id, contract, fiatAmount, btcAmount, seller, buyer, signedOpenTx, signedFundTx, signedPayoutTx)
//  //    }
//}

//case class FullySignedSellOffer(id: UUID, contract: Contract,
//                            fiatAmount: Money, btcAmount: Money,
//                            seller: Seller, buyer: Buyer,
//                            signedOpenTx: OpenTx, signedFundTx: FundTx,
//                            signedPayoutTx: PayoutTx) extends TradeData {
//
//}


