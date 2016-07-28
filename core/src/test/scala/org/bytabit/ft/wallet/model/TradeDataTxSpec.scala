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
import org.bytabit.ft.trade.model.{Contract, Offer}
import org.bytabit.ft.util._
import org.bytabit.ft.wallet.WalletJsonProtocol

class TradeDataTxSpec extends FlatSpec with Matchers with WalletJsonProtocol {

  val params = NetworkParameters.fromID(NetworkParameters.ID_UNITTESTNET)

  Context.propagate(new Context(params))

  // arbitrator
  val arbitratorWallet = new Wallet(params)

  // btc seller
  val btcSellerWallet = new Wallet(params)

  // btc buyer
  val btcBuyerWallet = new Wallet(params)

  // payment details key
  val paymentDetailsKey = AESCipher.genRanData(AESCipher.AES_KEY_LEN)

  it should "create fully signed transactions from complete btc buy offer" in {

    val paymentDetails = "Bank Name: Citibank, Account Holder: Fred Flintstone, Account Number: 12345-678910"

    // create test offer
    val offer = createOffer(arbitratorWallet)

    // add btcBuyer to new offer to create btc buy offer
    val btcBuyerInput1Key = btcBuyerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val btcBuyerInput2Key = btcBuyerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val btcBuyerOpenUtxo = Seq(unspentTx(None, offer.coinToOpenEscrow.divide(2), btcBuyerInput1Key),
      unspentTx(None, offer.coinToOpenEscrow.divide(2), btcBuyerInput2Key)).map(_.getOutput(0))
    val btcBuyer = BtcBuyer(offer.coinToOpenEscrow)(btcBuyerWallet).copy(openTxUtxo = btcBuyerOpenUtxo)
    val btcBuyOffer = offer.withBtcBuyer(btcBuyer)
    //val uso = so.copy(btcBuyer = so.btcBuyer.copy(openTxUtxo = btcBuyerOpenUtxo))

    // add btcSeller and btcSeller signed open tx sigs and tx output from signed fund tx
    // to btc buy offer to create taken btc sell offer
    val btcSellerInput1Key = btcSellerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val btcSellerInput2Key = btcSellerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val btcSellerOpenUtxo = Seq(unspentTx(None, btcBuyOffer.coinToOpenEscrow, btcSellerInput1Key)).map(_.getOutput(0))
    val btcSellerFundUtxo = Seq(unspentTx(None, btcBuyOffer.coinToFundEscrow, btcSellerInput2Key)).map(_.getOutput(0))
    val btcSeller = BtcSeller(btcBuyOffer.coinToOpenEscrow, btcBuyOffer.coinToFundEscrow)(btcSellerWallet)
      .copy(openTxUtxo = btcSellerOpenUtxo, fundTxUtxo = btcSellerFundUtxo)
    val btcSellerOpenTxSigs: Seq[TxSig] =
      btcBuyOffer.unsignedOpenTx(btcSeller).sign(btcSellerWallet).inputSigs
    val btcSellerFundPayoutTxo: Seq[TransactionOutput] =
      btcBuyOffer.unsignedFundTx(btcSeller, paymentDetailsKey).sign(btcSellerWallet).outputsToEscrow

    // cipher payment details
    val cipher = offer.cipher(paymentDetailsKey, btcBuyer, btcSeller)
    val cipherPaymentDetails = cipher.encrypt(paymentDetails.map(_.toByte).toArray)
    val takenBtcBuyOffer = btcBuyOffer.withBtcSeller(btcSeller, btcSellerOpenTxSigs, btcSellerFundPayoutTxo, cipherPaymentDetails)

    // add btcBuyer open tx sigs and payout tx sigs to taken btc buy offer to create
    // fully signed offer
    val btcBuyerOpenTxSigs: Seq[TxSig] =
      takenBtcBuyOffer.unsignedOpenTx.sign(btcBuyerWallet).inputSigs
    val fullySignedOpenTx =
      takenBtcBuyOffer.btcSellerSignedOpenTx.addInputSigs(btcBuyerOpenTxSigs)
    val btcBuyerPayoutTxSigs: Seq[TxSig] =
      takenBtcBuyOffer.unsignedPayoutTx(fullySignedOpenTx).sign(btcBuyer.escrowPubKey)(btcBuyerWallet).inputSigs
    val btcBuyerSignedOffer = takenBtcBuyOffer.withBtcBuyerSigs(btcBuyerOpenTxSigs, btcBuyerPayoutTxSigs)

    // add btcSeller signed open tx, fund tx and payout tx to get fully signed offer
    val btcSellerSignedOpenTx = btcBuyerSignedOffer.fullySignedOpenTx
    val btcSellerSignedFundTx = btcBuyerSignedOffer.unsignedFundTx.sign(btcSellerWallet)
    val btcSellerSignedPayoutTx = btcBuyerSignedOffer.btcBuyerSignedPayoutTx.sign(btcSeller.escrowPubKey)(btcSellerWallet)

    btcSellerSignedOpenTx shouldBe 'fullySigned
    btcSellerSignedFundTx shouldBe 'fullySigned
    btcSellerSignedPayoutTx shouldBe 'fullySigned
  }

  def createOffer(arbitratorWallet: Wallet): Offer = {

    val arbitratorUrl = new URL("http://bytabit.com/arbitrator")
    val bondPercent = 0.20
    val arbitratorFee = BTCMoney(0.10)
    val arbitrator = Arbitrator(arbitratorUrl, bondPercent, arbitratorFee)(arbitratorWallet)

    val paymentMethod = PaymentMethod.swish
    val fiatCurrencyUnit = CurrencyUnits.USD
    val fiatAmount = FiatMoney(fiatCurrencyUnit, "100.00")
    val btcAmount = BTCMoney(0, 20)

    val contract = Contract(arbitrator, fiatCurrencyUnit, paymentMethod)

    Offer(UUID.randomUUID(), contract, fiatAmount, btcAmount)
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
