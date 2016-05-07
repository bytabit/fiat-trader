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
import java.util
import java.util.Date

import akka.actor._
import akka.event.Logging
import org.bitcoinj.core.Wallet.SendRequest
import org.bitcoinj.core.{Address, _}
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.KeyChain
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.client.EventClient
import org.bytabit.ft.trade.model._
import org.bytabit.ft.util._
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.model._
import org.joda.money.Money
import org.joda.time.LocalDateTime

import scala.collection.JavaConversions._
import scala.util.Try

object WalletManager {

  val props = Props(new WalletManager)
  val name = s"walletManager"

  def actorOf(implicit system: ActorSystem) = system.actorOf(props, name)

  // bitcoinj context

  val netParams = NetworkParameters.fromID(Config.walletNet)

  // wallet commands

  sealed trait Command

  case object Start extends Command

  case object FindTransactions extends Command

  case object FindBalance extends Command

  case class FindCurrentAddress(purpose: KeyChain.KeyPurpose) extends Command

  case class CreateArbitrator(url: URL, bondPercent: Double, btcNotaryFee: Money) extends Command

  case class CreateSellOffer(offer: Offer) extends Command

  case class TakeSellOffer(sellOffer: SellOffer, deliveryDetails: String) extends Command

  case class SignTakenOffer(takenOffer: TakenOffer) extends Command

  case class CertifyFiatSent(certifyFiatEvidence: CertifyFiatEvidence) extends Command

  case class CertifyFiatNotSent(certifyFiatEvidence: CertifyFiatEvidence) extends Command

  case class AddWatchEscrowAddress(escrowAddress: Address) extends Command

  case class RemoveWatchEscrowAddress(escrowAddress: Address) extends Command

  case class BroadcastTx(tx: Tx, escrowPubKey: Option[PubECKey] = None) extends Command

  case class WithdrawXBT(toAddress: String, amount: Money) extends Command

  // wallet events

  sealed trait Event

  case object Started

  case class DownloadProgress(pct: Double, blocksSoFar: Int, date: LocalDateTime) extends Event

  case object DownloadDone extends Event

  case class TransactionUpdated(tx: Transaction, amt: Coin) extends Event

  case class EscrowTransactionUpdated(tx: Transaction) extends Event

  case class BalanceFound(balance: Coin) extends Event

  case class CurrentAddressFound(a: Address) extends Event

  case class ArbitratorCreated(arbitrator: Arbitrator) extends Event

  case class SellOfferCreated(sellOffer: SellOffer) extends Event

  case class SellOfferTaken(takenOffer: TakenOffer) extends Event

  case class TakenOfferSigned(signedTakenOffer: SignedTakenOffer) extends Event

  case class FiatSentCertified(certifiedFiatSent: CertifiedFiatDelivery) extends Event

  case class FiatNotSentCertified(certifiedFiatNotSent: CertifiedFiatDelivery) extends Event

}

class WalletManager extends Actor with ListenerUpdater {

  val log = Logging(context.system, this)

  val dispatcher = context.system.dispatcher

  var addressListeners = Map[Address, ActorRef]()

  override def receive: Receive = {

    // handlers for listener registration

    case c: ListenerUpdater.Command => handleListenerCommand(c)

    // handlers for wallet manager commands

    case Start =>
      self ! FindTransactions
      startWallet(downloadProgressTracker, walletEventListener, escrowWalletEventListener)

    case FindBalance =>
      val c = wallet.getBalance
      sendToListeners(BalanceFound(c))

    case FindTransactions =>
      val txs = wallet.getTransactions(false)
      txs.foreach(tx => sender ! TransactionUpdated(tx, tx.getValue(wallet)))

    case FindCurrentAddress(p) =>
      val a = wallet.currentAddress(p)
      log.debug(s"current wallet address: $a")
      sender ! CurrentAddressFound(a)

    case CreateArbitrator(u, bp, nf) =>
      sender ! ArbitratorCreated(Arbitrator(u, bp, nf))

    case CreateSellOffer(offer: Offer) =>
      sender ! SellOfferCreated(offer.withSeller)

    case TakeSellOffer(sellOffer: SellOffer, deliveryDetails: String) =>
      val key = AESCipher.genRanData(AESCipher.AES_KEY_LEN)
      sender ! SellOfferTaken(sellOffer.take(deliveryDetails, key))

    case SignTakenOffer(takenOffer: TakenOffer) =>
      sender ! TakenOfferSigned(takenOffer.sign)

    case CertifyFiatSent(fiatEvidence) =>
      sender ! FiatSentCertified(fiatEvidence.certifyFiatSent)

    case CertifyFiatNotSent(fiatEvidence) =>
      sender ! FiatNotSentCertified(fiatEvidence.certifyFiatNotSent)

    case AddWatchEscrowAddress(escrowAddress: Address) =>
      assert(escrowAddress.isP2SHAddress)
      addressListeners = addressListeners + (escrowAddress -> context.sender())
      escrowWallet.addWatchedAddress(escrowAddress)
    //log.info(s"ADDED event listener for address: $escrowAddress listener: ${context.sender()}")

    case RemoveWatchEscrowAddress(escrowAddress: Address) =>
      assert(escrowAddress.isP2SHAddress)
      addressListeners.get(escrowAddress).foreach { ar =>
        escrowWallet.removeWatchedAddress(escrowAddress)
        addressListeners = addressListeners - escrowAddress
        //log.info(s"REMOVED event listener for address: $escrowAddress listener: $ar")
      }

    case BroadcastTx(ot: OpenTx, None) =>
      val signed = ot.sign
      assert(signed.fullySigned)
      wallet.commitTx(signed.tx)
      kit.peerGroup.broadcastTransaction(signed.tx)
      escrowKit.peerGroup.broadcastTransaction(signed.copy().tx)
      log.info(s"OpenTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
        s"size ${signed.tx.getMessageSize} bytes")

    case BroadcastTx(ft: FundTx, None) =>
      val signed = ft.sign
      assert(signed.fullySigned)
      wallet.commitTx(signed.tx)
      kit.peerGroup.broadcastTransaction(signed.tx)
      escrowKit.peerGroup.broadcastTransaction(signed.copy().tx)
      log.info(s"FundTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
        s"size ${signed.tx.getMessageSize} bytes")

    case BroadcastTx(pt: PayoutTx, Some(pk: PubECKey)) =>
      val signed = pt.sign(pk)
      assert(signed.fullySigned)
      wallet.commitTx(signed.tx)
      kit.peerGroup.broadcastTransaction(signed.tx)
      escrowKit.peerGroup.broadcastTransaction(signed.copy().tx)
      log.info(s"PayoutTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
        s"size ${signed.tx.getMessageSize} bytes")

    case WithdrawXBT(withdrawAddress, withdrawAmount) =>
      assert(Monies.isBTC(withdrawAmount))
      val coinAmt = BTCMoney.toCoin(withdrawAmount)
      val btcAddr: Option[Address] = Try(new Address(netParams, withdrawAddress)).toOption
      if (btcAddr.isEmpty) log.error(s"Can't withdraw XBT, invalid address: $withdrawAddress")
      btcAddr.map { a =>
        val sr = SendRequest.to(a, coinAmt)
        sr.memo = s"Withdraw to $a"
        sr
      }.foreach(wallet.sendCoins)

    // handlers for wallet generated events

    case e: DownloadProgress =>
      sendToListeners(e)

    case e: TransactionUpdated =>
      self ! FindBalance
      sendToListeners(e)

    case DownloadDone =>
      self ! FindBalance
      sendToListeners(DownloadDone)

    case _ => Unit
  }

  val escrowWalletEventListener = new WalletEventListener {

    override def onCoinsReceived(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin): Unit = {}

    override def onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction): Unit = {
      // find P2SH addresses in inputs and outputs
      val foundAddrs: List[Address] = (tx.getInputs.toList.map(i => p2shAddress(i.getConnectedOutput))
        ++ tx.getOutputs.toList.map(o => p2shAddress(o))).flatten

      // send TX to actor ref listening for that P2SH address
      foundAddrs.foreach { a =>
        addressListeners.get(a) match {
          case Some(ar) =>
            ar ! EscrowTransactionUpdated(tx: Transaction)
          //log.info(s"EscrowTransactionUpdated for $a sent to $ar")
          case _ =>
          // do nothing
        }
      }
    }

    def p2shAddress(output: TransactionOutput): Option[Address] = Try(output.getAddressFromP2SH(netParams)).toOption

    override def onWalletChanged(wallet: Wallet): Unit = {}

    override def onScriptsChanged(wallet: Wallet, scripts: util.List[Script], isAddingScripts: Boolean): Unit = {}

    override def onCoinsSent(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin): Unit = {}

    override def onReorganize(wallet: Wallet): Unit = {}

    override def onKeysAdded(keys: util.List[ECKey]): Unit = {}
  }

  val walletEventListener = new WalletEventListener {

    override def onCoinsReceived(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin): Unit = {
      //log.info(s"CoinsReceived prevBalance:$prevBalance, newBalance:$newBalance\nCoinsSent tx: $tx")
    }

    override def onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction): Unit = {
      self ! TransactionUpdated(tx, tx.getValue(wallet))
    }

    override def onWalletChanged(wallet: Wallet): Unit = {
      //log.info(s"WalletChanged ${wallet.getRecentTransactions(0, false)}")
    }

    override def onScriptsChanged(wallet: Wallet, scripts: util.List[Script], isAddingScripts: Boolean): Unit = {

    }

    override def onCoinsSent(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin): Unit = {
      //log.info(s"CoinsSent prevBalance:$prevBalance, newBalance:$newBalance\nCoinsSent tx: $tx")
    }

    override def onReorganize(wallet: Wallet): Unit = {

    }

    override def onKeysAdded(keys: util.List[ECKey]): Unit = {

    }
  }

  val downloadProgressTracker = new DownloadProgressTracker {

    override def onBlocksDownloaded(peer: Peer, block: Block, filteredBlock: FilteredBlock, blocksLeft: Int): Unit = {
      super.onBlocksDownloaded(peer, block, filteredBlock, blocksLeft)
      self ! FindTransactions
    }

    override def progress(pct: Double, blocksSoFar: Int, date: Date): Unit = {
      super.progress(pct, blocksSoFar, date)
      self ! DownloadProgress(pct, blocksSoFar, LocalDateTime.fromDateFields(date))
    }

    override def doneDownload(): Unit = {
      super.doneDownload()
      self ! DownloadDone
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    stopWallet()
  }

  private val kit = new WalletAppKit(netParams, new File(Config.walletDir), Config.config)
  protected[this] implicit lazy val wallet = kit.wallet()

  // setup ephemeral wallet to listen for trade transactions to escrow addresses
  private val escrowKit = new WalletAppKit(netParams, new File(Config.walletDir), s"${Config.config}-escrow")
  protected[this] lazy val escrowWallet = escrowKit.wallet()

  def startWallet(dpt: DownloadProgressTracker, wel: WalletEventListener, ewel: WalletEventListener) = {

    // setup wallet app kit
    kit.setAutoSave(true)
    kit.setBlockingStartup(false)
    kit.setUserAgent(Config.config, Config.version)
    kit.setDownloadListener(dpt)
    if (netParams == RegTestParams.get) kit.connectToLocalHost()

    // start wallet app kit

    kit.startAsync()
    kit.awaitRunning()
    kit.wallet().addEventListener(wel)

    // setup escrow wallet app kit

    escrowKit.setAutoSave(true)
    escrowKit.setBlockingStartup(false)
    escrowKit.setUserAgent(Config.config, Config.version)
    if (netParams == RegTestParams.get) escrowKit.connectToLocalHost()

    // start escrow wallet app kit

    escrowKit.startAsync()
    escrowKit.awaitRunning()
    escrowKit.wallet().addEventListener(ewel)
  }

  def stopWallet(): Unit = {
    kit.stopAsync()
    escrowKit.stopAsync()
  }
}

