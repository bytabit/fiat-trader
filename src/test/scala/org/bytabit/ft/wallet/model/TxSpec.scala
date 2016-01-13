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

package org.bytabit.ft.wallet.model

import java.net.URL
import java.util.UUID

import org.bitcoinj.core._
import org.bytabit.ft.trade.model.{Contract, SignedTakenOffer}
import org.bytabit.ft.util.{BTCMoney, CurrencyUnits, FiatMoney}
import org.bytabit.ft.wallet.WalletJsonProtocol
import org.bytabit.ft.wallet.model.TxTools.{COIN_MINER_FEE, COIN_OP_RETURN_FEE}
import org.joda.money.CurrencyUnit
import org.scalatest._


class TxSpec extends FlatSpec with Matchers with WalletJsonProtocol {

  val params = NetworkParameters.fromID(NetworkParameters.ID_UNITTESTNET)

  Context.propagate(new Context(params))

  // notary
  val notaryWallet = new Wallet(params)

  // buyer
  val buyerWallet = new Wallet(params)

  // seller
  val sellerWallet = new Wallet(params)

  val coinTraded = Coin.COIN.multiply(10)
  val coinBond = Coin.COIN.multiply(2)
  val coinNotaryFee = Coin.COIN

  val coinBuyerOpenIn = coinBond.add(coinNotaryFee).add(COIN_MINER_FEE)
  val coinBuyerFundIn = coinTraded.add(COIN_OP_RETURN_FEE).add(COIN_MINER_FEE)

  val coinSellerOpenIn1 = coinBond
  val coinSellerOpenIn2 = coinNotaryFee.add(COIN_MINER_FEE)

  it should "create open escrow transaction" in {
    val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
    val openTx = sto.fullySignedOpenTx
    assert(openTx.verified)
  }

  it should "create fund escrow transaction" in {
    val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
    val fundTx = sto.unsignedFundTx
    assert(fundTx.verified)
  }

  it should "create escrow payout transaction" in {
    val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
    val payoutTx = sto.sellerSignedPayoutTx
    assert(payoutTx.verified)
  }

  it should "sign open escrow transaction" in {
    val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
    val openTx = sto.fullySignedOpenTx
    openTx shouldBe 'fullySigned
  }

  it should "sign fund escrow transaction" in {
    val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
    val fundTx = sto.unsignedFundTx.sign(buyerWallet)
    fundTx shouldBe 'fullySigned
  }

  //  it should "sign cancel trade escrow payout transaction with seller and buyer" in {
  //
  //    val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
  //
  //    val signedOpenTx = sto.fullySignedOpenTx
  //    val payoutTx = trade.cancelPayoutTx(openTx)
  //
  //    val signedPayoutTx = for {
  //      potx <- payoutTx
  //      seller <- trade.offer.contract.seller
  //      buyer <- trade.offer.contract.buyer
  //    } yield potx.sign(seller.escrowPubKey)(sellerWallet).sign(buyer.escrowPubKey)(buyerWallet)
  //
  //    signedPayoutTx.foreach(_ shouldBe 'fullySigned)
  //  }

  it should "sign happy path escrow payout transaction with seller and buyer" in {

    (0 to 10).foreach { i =>
      // sign multiple times to ensure signature ordering is always correct
      val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)

      val signedPayoutTx = sto.sellerSignedPayoutTx.sign(sto.buyer.escrowPubKey)(buyerWallet)

      signedPayoutTx shouldBe 'fullySigned
    }
  }

  //  it should "sign seller wins dispute escrow payout transaction with seller and notary" in {
  //
  //    val trade = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
  //    val notary = trade.offer.contract.notary
  //
  //    val signedOpenTx = trade.openTx.map(_.sign(buyerWallet).sign(sellerWallet))
  //    val signedFundTxOutputs = trade.fundTx.map(_.sign(buyerWallet).outputsToEscrow)
  //    val payoutTx = trade.sellerWinsDisputePayoutTx(signedOpenTx, signedFundTxOutputs)
  //
  //    val signedPayoutTx = for {
  //      potx <- payoutTx
  //      seller <- trade.offer.contract.seller
  //    } yield potx.sign(seller.escrowPubKey)(sellerWallet).sign(notary.escrowPubKey)(notaryWallet)
  //
  //    signedPayoutTx.foreach(_ shouldBe 'fullySigned)
  //  }

  //  it should "sign buyer wins dispute escrow payout transaction with buyer and notary" in {
  //
  //    val trade = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
  //    val notary = trade.offer.contract.notary
  //
  //    val signedOpenTx = trade.openTx.map(_.sign(buyerWallet).sign(sellerWallet))
  //    val signedFundTxOutputs = trade.fundTx.map(_.sign(buyerWallet).outputsToEscrow)
  //    val payoutTx = trade.buyerWinsDisputePayoutTx(signedOpenTx, signedFundTxOutputs)
  //
  //    val signedPayoutTx = for {
  //      potx <- payoutTx
  //      buyer <- trade.offer.contract.buyer
  //    } yield potx.sign(buyer.escrowPubKey)(buyerWallet).sign(notary.escrowPubKey)(notaryWallet)
  //
  //    signedPayoutTx.foreach(_ shouldBe 'fullySigned)
  //  }

  //  it should "sign escrow payout transaction with seller and buyer and replace seller" in {
  //
  //    val trade = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
  //
  //    val signedOpenTx = trade.openTx.map(_.sign(buyerWallet).sign(sellerWallet))
  //    val signedFundTxOutputs = trade.fundTx.map(_.sign(buyerWallet).outputsToEscrow)
  //    val fundTx = trade.fundTx
  //    val payoutTx = trade.payoutTx(signedOpenTx, signedFundTxOutputs)
  //
  //    val signedPayoutTx = for {
  //      potx <- payoutTx
  //      seller <- trade.offer.contract.seller
  //      buyer <- trade.offer.contract.buyer
  //    } yield potx.sign(seller.escrowPubKey)(sellerWallet).sign(buyer.escrowPubKey)(buyerWallet)
  //      .sign(seller.escrowPubKey)(sellerWallet)
  //
  //    signedPayoutTx.foreach(_ shouldBe 'fullySigned)
  //  }

  //  it should "sign escrow payout transaction with all participants" in {
  //
  //    val trade = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
  //    val notary = trade.offer.contract.notary
  //
  //    val signedOpenTx = trade.openTx.map(_.sign(buyerWallet).sign(sellerWallet))
  //    val signedFundTxOutputs = trade.fundTx.map(_.sign(buyerWallet).outputsToEscrow)
  //    val payoutTx = trade.payoutTx(signedOpenTx, signedFundTxOutputs)
  //
  //    val signedPayoutTx = for {
  //      potx <- payoutTx
  //      seller <- trade.offer.contract.seller
  //      buyer <- trade.offer.contract.buyer
  //    } yield potx.sign(seller.escrowPubKey)(sellerWallet).sign(buyer.escrowPubKey)(buyerWallet)
  //      .sign(notary.escrowPubKey)(notaryWallet)
  //
  //    signedPayoutTx.foreach(_ shouldBe 'fullySigned)
  //  }

  //  it should "sign all transactions with seller and buyer signatures" in {
  //
  //    val trade = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)
  //
  //    val openTx = trade.openTx
  //    val fundTx = trade.fundTx
  //
  //    val signedOpenTx = trade.openTx.map(_.sign(buyerWallet).sign(sellerWallet))
  //
  //    signedOpenTx.foreach(_ shouldBe 'fullySigned)
  //
  //    val signedFundTx = for {
  //      ftx <- fundTx
  //      seller <- trade.offer.contract.seller
  //      buyer <- trade.offer.contract.buyer
  //    } yield ftx.sign(buyerWallet)
  //
  //    signedFundTx.foreach(_ shouldBe 'fullySigned)
  //
  //    val payoutTx = trade.payoutTx(signedOpenTx, signedFundTx.map(_.outputsToEscrow))
  //
  //    val signedPayoutTx = for {
  //      potx <- payoutTx
  //      seller <- trade.offer.contract.seller
  //      buyer <- trade.offer.contract.buyer
  //    } yield potx.sign(seller.escrowPubKey)(sellerWallet).sign(buyer.escrowPubKey)(buyerWallet)
  //
  //    signedPayoutTx.foreach(_ shouldBe 'fullySigned)
  //  }

  it should "serialize tx output to json" in {

    val sto = signedTakenOffer(notaryWallet, buyerWallet, sellerWallet)

    val testOutputs: Seq[TransactionOutput] = sto.takenOffer.buyer.fundTxUtxo

    val txOut0: TransactionOutput = testOutputs.head

    val json = TransactionOutputJsonFormat.write(txOut0)
    //println(s"json:\n$json")

    val obj = TransactionOutputJsonFormat.read(json)
    //println(s"obj:\n${obj.toString}")

    txOut0.getParentTransactionHash shouldEqual obj.getParentTransactionHash
  }

  def signedTakenOffer(notaryWallet: Wallet, buyerWallet: Wallet, sellerWallet: Wallet): SignedTakenOffer = {

    val buyerInput1Key = buyerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val buyerInput2Key = buyerWallet.freshReceiveKey().dropParent.dropPrivateBytes

    val buyerOpenUtxo = List(unspentTx(None, coinBuyerOpenIn, buyerInput1Key)).map(_.getOutput(0))
    val buyerFundUtxo = List(unspentTx(None, coinBuyerFundIn, buyerInput2Key)).map(_.getOutput(0))

    val sellerInput1Key = sellerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val sellerInput2Key = sellerWallet.freshReceiveKey().dropParent.dropPrivateBytes

    val sellerOpenUtxo = List(unspentTx(None, coinSellerOpenIn1, sellerInput1Key),
      unspentTx(None, coinSellerOpenIn2, sellerInput2Key)).map(_.getOutput(0))

    val notaryUrl = new URL("http://bytabit.com/notary")
    val deliveryMethod = "CASH DEPOSIT"
    val fiatCurrencyUnit = CurrencyUnits.USD
    val bondPercent = 0.20
    val btcNotaryFee = BTCMoney(0, 10)
    val fiatTraded = FiatMoney(CurrencyUnit.USD, BigDecimal(250.00))
    val deliveryDetails = "Bank Name: Citibank, Account Holder: Fred Flintstone, Account Number: 12345-678910"

    val notary = Notary(notaryUrl, bondPercent, btcNotaryFee)(notaryWallet)
    val contract = Contract(notary, fiatCurrencyUnit, deliveryMethod)
    val offer = contract.offer(UUID.randomUUID(), fiatTraded, BTCMoney(coinTraded))

    val seller = Seller(offer.coinToOpenEscrow, sellerOpenUtxo)(sellerWallet)
    val sellOffer = offer.withSeller(seller)

    val buyer = Buyer(offer.coinToOpenEscrow, offer.coinToFundEscrow, deliveryDetails, buyerOpenUtxo, buyerFundUtxo)(buyerWallet)
    val buyerOpenTxSigs: Seq[TxSig] = sellOffer.unsignedOpenTx(buyer).sign(buyerWallet).inputSigs
    val buyerFundPayoutTxo: Seq[TransactionOutput] = sellOffer.unsignedFundTx(buyer).sign(buyerWallet).outputsToEscrow
    val takenOffer = sellOffer.withBuyer(buyer, buyerOpenTxSigs, buyerFundPayoutTxo)

    takenOffer.sign(sellerWallet)
  }

  def unspentTx(inputTx: Option[Transaction], coin: Coin, key: ECKey): Transaction = {
    val tx = new Transaction(params)
    tx.addOutput(coin, key.toAddress(params))
    inputTx.foreach { in =>
      Tx.fullySigned(tx)
    }
    tx
  }

}
