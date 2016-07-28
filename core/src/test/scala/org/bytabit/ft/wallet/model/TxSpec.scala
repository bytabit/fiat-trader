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
import org.bitcoinj.wallet.Wallet
import org.bytabit.ft.trade.model.{BtcBuyOffer, Contract, SignedTakenOffer, TakenOffer}
import org.bytabit.ft.util._
import org.bytabit.ft.wallet.WalletJsonProtocol
import org.bytabit.ft.wallet.model.TxTools.{COIN_MINER_FEE, COIN_OP_RETURN_FEE}
import org.joda.money.CurrencyUnit
import org.joda.time.DateTime


class TxSpec extends FlatSpec with Matchers with WalletJsonProtocol {

  val params = NetworkParameters.fromID(NetworkParameters.ID_UNITTESTNET)

  Context.propagate(new Context(params))

  // arbitrator
  val arbitratorWallet = new Wallet(params)

  // btc seller
  val btcSellerWallet = new Wallet(params)

  // btc buyer
  val btcBuyerWallet = new Wallet(params)

  val coinTraded = Coin.COIN.multiply(10)
  val coinBond = Coin.COIN.multiply(2)
  val coinArbitratorFee = Coin.COIN

  val coinBuyerOpenIn = coinBond.add(coinArbitratorFee).add(COIN_MINER_FEE)
  val coinBuyerFundIn = coinTraded.add(COIN_OP_RETURN_FEE).add(COIN_MINER_FEE)

  val coinBtcBuyerOpenIn1 = coinBond
  val coinBtcBuyerOpenIn2 = coinArbitratorFee.add(COIN_MINER_FEE)

  // payment details key
  val paymentDetailsKey = AESCipher.genRanData(AESCipher.AES_KEY_LEN)

  it should "create open escrow transaction" in {
    val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)
    val openTx = sto.fullySignedOpenTx
    assert(openTx.verified)
  }

  it should "create fund escrow transaction" in {
    val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)
    val fundTx = sto.unsignedFundTx
    assert(fundTx.verified)
  }

  it should "create escrow payout transaction" in {
    val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)
    val payoutTx = sto.btcBuyerSignedPayoutTx
    assert(payoutTx.verified)
  }

  it should "sign open escrow transaction" in {
    val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)
    val openTx = sto.fullySignedOpenTx
    openTx shouldBe 'fullySigned
  }

  it should "buyer sign fund escrow transaction" in {
    val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)
    val fundTx = sto.unsignedFundTx.sign(btcSellerWallet)
    fundTx shouldBe 'fullySigned
  }

  //  it should "sign cancel trade escrow payout transaction with btc buyer and buyer" in {
  //
  //    val sto = signedTakenOffer(arbitratorWallet, buyerWallet, btcBuyerWallet)
  //
  //    val signedOpenTx = sto.fullySignedOpenTx
  //    val payoutTx = trade.cancelPayoutTx(openTx)
  //
  //    val signedPayoutTx = for {
  //      potx <- payoutTx
  //      btcBuyer <- trade.offer.contract.btcBuyer
  //      buyer <- trade.offer.contract.buyer
  //    } yield potx.sign(btcBuyer.escrowPubKey)(btcBuyerWallet).sign(buyer.escrowPubKey)(buyerWallet)
  //
  //    signedPayoutTx.foreach(_ shouldBe 'fullySigned)
  //  }

  it should "sign happy path escrow payout transaction with btc buyer and buyer" in {

    (0 to 10).foreach { i =>
      // sign multiple times to ensure signature ordering is always correct
      val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)

      val signedPayoutTx = sto.btcBuyerSignedPayoutTx.sign(sto.btcSeller.escrowPubKey)(btcSellerWallet)

      signedPayoutTx shouldBe 'fullySigned
    }
  }

  it should "sign arbitrated fiat sent escrow payout transaction with btc buyer and arbitrator" in {

    (0 to 10).foreach { i =>
      // sign multiple times to ensure signature ordering is always correct
      val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)
      val ot = sto.withOpenTx(sto.unsignedOpenTx.tx.getHash, new DateTime(sto.unsignedOpenTx.tx.getUpdateTime))
      val ft = ot.withFundTx(sto.unsignedFundTx.tx.getHash, new DateTime(sto.unsignedFundTx.tx.getUpdateTime), Some(paymentDetailsKey))
      val cfr = ft.certifyFiatRequested(None)
      val cfs = cfr.certifyFiatSent(arbitratorWallet)

      val signedPayoutTx = cfs.arbitratorSignedFiatSentPayoutTx.sign(sto.btcBuyer.escrowPubKey)(btcBuyerWallet)

      signedPayoutTx shouldBe 'fullySigned
    }
  }

  it should "sign arbitrated fiat not sent escrow payout transaction with buyer and arbitrator" in {

    (0 to 10).foreach { i =>
      // sign multiple times to ensure signature ordering is always correct
      val sto = signedTakenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet)
      val ot = sto.withOpenTx(sto.unsignedOpenTx.tx.getHash, new DateTime(sto.unsignedOpenTx.tx.getUpdateTime))
      val ft = ot.withFundTx(sto.unsignedFundTx.tx.getHash, new DateTime(sto.unsignedFundTx.tx.getUpdateTime), Some(paymentDetailsKey))
      val cfr = ft.certifyFiatRequested(None)
      val cfns = cfr.certifyFiatNotSent(arbitratorWallet)

      val signedPayoutTx = cfns.arbitratorSignedFiatNotSentPayoutTx.sign(sto.btcSeller.escrowPubKey)(btcSellerWallet)

      signedPayoutTx shouldBe 'fullySigned
    }
  }

  it should "serialize tx output to json" in {

    val sto = signedTakenOffer(arbitratorWallet, btcSellerWallet, btcBuyerWallet)

    val testOutputs: Seq[TransactionOutput] = sto.takenOffer.btcSeller.fundTxUtxo

    val txOut0: TransactionOutput = testOutputs.head

    val json = TransactionOutputJsonFormat.write(txOut0)
    //println(s"json:\n$json")

    val obj = TransactionOutputJsonFormat.read(json)
    //println(s"obj:\n${obj.toString}")

    txOut0.getParentTransactionHash shouldEqual obj.getParentTransactionHash
  }

  def offer(arbitratorWallet: Wallet) = {

    val arbitratorUrl = new URL("http://bytabit.com/arbitrator")
    val paymentMethod = PaymentMethod.swish
    val fiatCurrencyUnit = CurrencyUnits.USD
    val bondPercent = 0.20
    val btcArbitratorFee = BTCMoney(0, 10)
    val fiatTraded = FiatMoney(CurrencyUnit.USD, BigDecimal(250.00))

    val arbitrator = Arbitrator(arbitratorUrl, bondPercent, btcArbitratorFee)(arbitratorWallet)
    val contract = Contract(arbitrator, fiatCurrencyUnit, paymentMethod)

    contract.offer(UUID.randomUUID(), fiatTraded, BTCMoney(coinTraded))
  }

  def btcBuyOffer(arbitratorWallet: Wallet, btcBuyerWallet: Wallet): BtcBuyOffer = {

    val btcBuyerInput1Key = btcBuyerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val btcBuyerInput2Key = btcBuyerWallet.freshReceiveKey().dropParent.dropPrivateBytes

    val btcBuyerOpenUtxo = List(unspentTx(None, coinBtcBuyerOpenIn1, btcBuyerInput1Key),
      unspentTx(None, coinBtcBuyerOpenIn2, btcBuyerInput2Key)).map(_.getOutput(0))

    val o = offer(arbitratorWallet)
    val btcBuyer = BtcBuyer(o.coinToOpenEscrow, btcBuyerOpenUtxo)(btcBuyerWallet)

    o.withBtcBuyer(btcBuyer)
  }

  def takenOffer(arbitratorWallet: Wallet, btcBuyerWallet: Wallet, btcSellerWallet: Wallet): TakenOffer = {

    val paymentDetails = "Bank Name: Citibank, Account Holder: Fred Flintstone, Account Number: 12345-678910"

    val buyerInput1Key = btcSellerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val buyerInput2Key = btcSellerWallet.freshReceiveKey().dropParent.dropPrivateBytes

    val buyerOpenUtxo = List(unspentTx(None, coinBuyerOpenIn, buyerInput1Key)).map(_.getOutput(0))
    val buyerFundUtxo = List(unspentTx(None, coinBuyerFundIn, buyerInput2Key)).map(_.getOutput(0))

    val so = btcBuyOffer(arbitratorWallet, btcBuyerWallet)

    val btcSeller = BtcSeller(so.offer.coinToOpenEscrow, so.offer.coinToFundEscrow, buyerOpenUtxo, buyerFundUtxo)(btcSellerWallet)
    val btcSellerOpenTxSigs: Seq[TxSig] = so.unsignedOpenTx(btcSeller).sign(btcSellerWallet).inputSigs
    val btcSellerFundPayoutTxo: Seq[TransactionOutput] = so.unsignedFundTx(btcSeller, paymentDetailsKey).sign(btcSellerWallet).outputsToEscrow

    // cipher payment details
    val cipher = so.cipher(paymentDetailsKey, so.btcBuyer, btcSeller)
    val cipherPaymentDetails = cipher.encrypt(paymentDetails.map(_.toByte).toArray)

    so.withBtcSeller(btcSeller, btcSellerOpenTxSigs, btcSellerFundPayoutTxo, cipherPaymentDetails)
  }

  def signedTakenOffer(arbitratorWallet: Wallet, btcBuyerWallet: Wallet, btcSellerWallet: Wallet): SignedTakenOffer = {

    takenOffer(arbitratorWallet, btcBuyerWallet, btcSellerWallet).sign(btcBuyerWallet)
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
