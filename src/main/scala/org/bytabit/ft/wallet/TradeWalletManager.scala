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
package org.bytabit.ft.wallet

import java.io.File
import java.net.URL

import akka.actor.Props
import com.google.common.util.concurrent.Service.Listener
import org.bitcoinj.core._
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.wallet.{DeterministicSeed, KeyChain, SendRequest, Wallet}
import org.bytabit.ft.trade.model.{BtcBuyOffer, CertifyPaymentEvidence, Offer, TakenOffer}
import org.bytabit.ft.util.{AESCipher, BTCMoney, Config, Monies}
import org.bytabit.ft.wallet.TradeWalletManager._
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.model._
import org.joda.money.Money
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.util.Try

object TradeWalletManager {

  val props = Props(new TradeWalletManager)
  val name = s"tradeWalletManager"

  // def actorOf(system: ActorSystem) = system.actorOf(props, name)

  // wallet commands

  sealed trait Command

  case object FindTransactions extends Command

  case object FindBalance extends Command

  case class FindCurrentAddress(purpose: KeyChain.KeyPurpose) extends Command

  case object CreateClientProfileId extends Command

  case class CreateArbitrator(url: URL, bondPercent: Double, btcNotaryFee: Money) extends Command

  case class CreateBtcBuyOffer(offer: Offer) extends Command

  case class TakeBtcBuyOffer(btcBuyOffer: BtcBuyOffer, paymentDetails: String) extends Command

  case class SignTakenOffer(takenOffer: TakenOffer) extends Command

  case class CertifyFiatSent(certifyFiatEvidence: CertifyPaymentEvidence) extends Command

  case class CertifyFiatNotSent(certifyFiatEvidence: CertifyPaymentEvidence) extends Command

  case class BroadcastTx(tx: Tx, escrowPubKey: Option[PubECKey] = None) extends Command

  case class WithdrawXBT(toAddress: String, amount: Money) extends Command

  case class GenerateBackupCode() extends Command

  case class RestoreWallet(code: List[String], seedCreationTime: DateTime) extends Command

  case class SetTransactionMemo(hash: Sha256Hash, memo: String) extends Command

}

class TradeWalletManager extends WalletManager with WalletTools {

  def kit: WalletAppKit = new WalletAppKit(btcContext, new File(Config.walletDir), Config.config)

  def kitListener = new Listener {

    override def running(): Unit = {
      self ! TradeWalletRunning
    }
  }

  startWith(STARTING, Data(startWallet(kit, downloadProgressTracker)))

  when(STARTING) {

    case Event(TradeWalletRunning, Data(k)) =>
      Context.propagate(btcContext)
      k.wallet().addTransactionConfidenceEventListener(txConfidenceEventListener)
      context.system.eventStream.publish(TradeWalletRunning)
      goto(RUNNING) using Data(k)

    case Event(e: TransactionUpdated, Data(k)) =>
      stay()

    // publish block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      context.system.eventStream.publish(e)
      stay()

    // ignore any commands
    case Event(e: TradeWalletManager.Command, d) =>
      stay()
  }

  when(RUNNING) {

    case Event(FindBalance, Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val c = w.getBalance
      sender ! BalanceFound(c)
      stay()

    case Event(FindTransactions, Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val txs = w.getTransactions(false)
      txs.foreach(tx => sender ! TransactionUpdated(tx, tx.getValue(w),
        tx.getConfidence.getConfidenceType,
        tx.getConfidence.getDepthInBlocks))
      stay()

    case Event(FindCurrentAddress(p), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val a = w.currentAddress(p)
      log.debug(s"current wallet address: $a")
      sender ! CurrentAddressFound(a)
      stay()

    case Event(CreateClientProfileId, Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! ClientProfileIdCreated(freshAuthKey(w))
      stay()

    case Event(CreateArbitrator(u, bp, nf), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! ArbitratorCreated(Arbitrator(u, bp, nf)(w))
      stay()

    case Event(CreateBtcBuyOffer(offer: Offer), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val available = BTCMoney(w.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE))
      val required = offer.btcToOpenEscrow
      if (available.compareTo(required) < 0) {
        sender ! InsufficientBtc(CreateBtcBuyOffer(offer: Offer), required, available)
      }
      else {
        val bo = offer.withBtcBuyer(w)
        sender ! BtcBuyOfferCreated(bo)
      }
      stay()

    case Event(TakeBtcBuyOffer(btcBuyOffer: BtcBuyOffer, paymentDetails: String), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val available = BTCMoney(w.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE))
      val required = btcBuyOffer.btcToOpenEscrow.plus(btcBuyOffer.btcToFundEscrow)
      if (available.compareTo(required) < 0) {
        sender ! InsufficientBtc(TakeBtcBuyOffer(btcBuyOffer, paymentDetails), required, available)
      }
      else {
        val key = AESCipher.genRanData(AESCipher.AES_KEY_LEN)
        val to = btcBuyOffer.take(paymentDetails, key)(w)
        sender ! BtcBuyOfferTaken(to)
      }
      stay()

    case Event(SignTakenOffer(takenOffer: TakenOffer), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! TakenOfferSigned(takenOffer.sign(w))
      stay()

    case Event(CertifyFiatSent(fiatEvidence), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! FiatSentCertified(fiatEvidence.certifyFiatSent(w))
      stay()

    case Event(CertifyFiatNotSent(fiatEvidence), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! FiatNotSentCertified(fiatEvidence.certifyFiatNotSent(w))
      stay()

    case Event(tu: TransactionUpdated, Data(w)) =>
      context.system.eventStream.publish(tu)
      stay()

    case Event(BroadcastTx(ot: OpenTx, None), Data(k)) =>
      val bcTx = broadcastOpenTx(k, ot)
      context.system.eventStream.publish(TxBroadcast(bcTx))
      stay()

    case Event(BroadcastTx(ft: FundTx, None), Data(k)) =>
      val bcTx = broadcastFundTx(k, ft)
      context.system.eventStream.publish(TxBroadcast(bcTx))
      stay()

    case Event(BroadcastTx(pt: PayoutTx, Some(pk: PubECKey)), Data(k)) =>
      val bcTx = broadcastPayoutTx(k, pt, pk)
      context.system.eventStream.publish(TxBroadcast(bcTx))
      stay()

    case Event(WithdrawXBT(withdrawAddress, withdrawAmount), Data(k)) =>
      val w = k.wallet
      Context.propagate(btcContext)
      assert(Monies.isBTC(withdrawAmount))
      val coinAmt = BTCMoney.toCoin(withdrawAmount)
      val btcAddr: Option[Address] = Try(Address.fromBase58(netParams, withdrawAddress)).toOption
      if (btcAddr.isEmpty) log.error(s"Can't withdraw XBT, invalid address: $withdrawAddress")
      btcAddr.map { a =>
        val sr = SendRequest.to(a, coinAmt)
        sr.memo = s"Withdraw to $a"
        sr
      }.foreach(w.sendCoins)
      stay()

    case Event(GenerateBackupCode(), Data(k)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val code = w.getKeyChainSeed.getMnemonicCode
      sender ! BackupCodeGenerated(code.toList, new DateTime(w.getEarliestKeyCreationTime * 1000))
      stay()

    case Event(RestoreWallet(c, dt), Data(k)) =>
      Context.propagate(btcContext)
      k.stopAsync().awaitTerminated()
      val seed = new DeterministicSeed(c, null, "", dt.getMillis / 1000)
      val newKit = kit.restoreWalletFromSeed(seed)
      startWallet(newKit, downloadProgressTracker)
      sender ! WalletRestored
      goto(STARTING) using Data(newKit)

    case Event(SetTransactionMemo(h, m), Data(k)) =>
      Context.propagate(btcContext)
      k.wallet().getTransaction(h).setMemo(m)
      stay()

    // publish block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      context.system.eventStream.publish(e)
      stay()
  }

}
