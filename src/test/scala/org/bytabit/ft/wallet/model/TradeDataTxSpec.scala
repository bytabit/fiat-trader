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
import org.bytabit.ft.trade.model.{Contract, Offer}
import org.bytabit.ft.util.{BTCMoney, CurrencyUnits, FiatMoney}
import org.bytabit.ft.wallet.WalletJsonProtocol
import org.scalatest._

class TradeDataTxSpec extends FlatSpec with Matchers with WalletJsonProtocol {

  val params = NetworkParameters.fromID(NetworkParameters.ID_UNITTESTNET)

  Context.propagate(new Context(params))

  // notary
  val notaryWallet = new Wallet(params)

  // buyer
  val buyerWallet = new Wallet(params)

  // seller
  val sellerWallet = new Wallet(params)

  it should "create fully signed transactions from complete buy offer" in {

    val deliveryDetails = "Bank Name: Citibank, Account Holder: Fred Flintstone, Account Number: 12345-678910"

    // create test offer
    val offer = createOffer(notaryWallet)

    // add seller to new offer to create sell offer
    val sellerInput1Key = sellerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val sellerInput2Key = sellerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val sellerOpenUtxo = Seq(unspentTx(None, offer.coinToOpenEscrow.divide(2), sellerInput1Key),
      unspentTx(None, offer.coinToOpenEscrow.divide(2), sellerInput2Key)).map(_.getOutput(0))
    val seller = Seller(offer.coinToOpenEscrow)(sellerWallet).copy(openTxUtxo = sellerOpenUtxo)
    val sellOffer = offer.withSeller(seller)
    //val uso = so.copy(seller = so.seller.copy(openTxUtxo = sellerOpenUtxo))

    // add buyer and buyer signed open tx sigs and tx output from signed fund tx
    // to sell offer to create taken sell offer
    val buyerInput1Key = buyerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val buyerInput2Key = buyerWallet.freshReceiveKey().dropParent.dropPrivateBytes
    val buyerOpenUtxo = Seq(unspentTx(None, sellOffer.coinToOpenEscrow, buyerInput1Key)).map(_.getOutput(0))
    val buyerFundUtxo = Seq(unspentTx(None, sellOffer.coinToFundEscrow, buyerInput2Key)).map(_.getOutput(0))
    val buyer = Buyer(sellOffer.coinToOpenEscrow, sellOffer.coinToFundEscrow, deliveryDetails)(buyerWallet)
      .copy(openTxUtxo = buyerOpenUtxo, fundTxUtxo = buyerFundUtxo)
    val buyerOpenTxSigs: Seq[TxSig] =
      sellOffer.unsignedOpenTx(buyer).sign(buyerWallet).inputSigs
    val buyerFundPayoutTxo: Seq[TransactionOutput] =
      sellOffer.unsignedFundTx(buyer).sign(buyerWallet).outputsToEscrow
    val takenSellOffer = sellOffer.withBuyer(buyer, buyerOpenTxSigs, buyerFundPayoutTxo)

    // add seller open tx sigs and payout tx sigs to taken sell offer to create
    // fully signed offer
    val sellerOpenTxSigs: Seq[TxSig] =
      takenSellOffer.unsignedOpenTx.sign(sellerWallet).inputSigs
    val fullySignedOpenTx =
      takenSellOffer.buyerSignedOpenTx.addInputSigs(sellerOpenTxSigs)
    val sellerPayoutTxSigs: Seq[TxSig] =
      takenSellOffer.unsignedPayoutTx(fullySignedOpenTx).sign(seller.escrowPubKey)(sellerWallet).inputSigs
    val sellerSignedOffer = takenSellOffer.withSellerSigs(sellerOpenTxSigs, sellerPayoutTxSigs)

    // add buyer signed open tx, fund tx and payout tx to get fully signed offer
    val buyerSignedOpenTx = sellerSignedOffer.fullySignedOpenTx
    val buyerSignedFundTx = sellerSignedOffer.unsignedFundTx.sign(buyerWallet)
    val buyerSignedPayoutTx = sellerSignedOffer.sellerSignedPayoutTx.sign(buyer.escrowPubKey)(buyerWallet)

    buyerSignedOpenTx shouldBe 'fullySigned
    buyerSignedFundTx shouldBe 'fullySigned
    buyerSignedPayoutTx shouldBe 'fullySigned
  }

  def createOffer(notaryWallet: Wallet): Offer = {

    val notaryUrl = new URL("http://bytabit.com/notary")
    val bondPercent = 0.20
    val notaryFee = BTCMoney(0.10)
    val notary = Notary(notaryUrl, bondPercent, notaryFee)(notaryWallet)

    val deliveryMethod = "CASH DEPOSIT"
    val fiatCurrencyUnit = CurrencyUnits.USD
    val fiatAmount = FiatMoney(fiatCurrencyUnit, "100.00")
    val btcAmount = BTCMoney(0, 20)

    val contract = Contract(notary, fiatCurrencyUnit, deliveryMethod)

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
