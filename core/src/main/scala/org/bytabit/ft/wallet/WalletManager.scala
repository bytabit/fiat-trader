/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.bytabit.ft.wallet

import java.io.{File, IOException}
import java.util.Date

import akka.actor._
import akka.persistence.fsm.PersistentFSM.FSMState
import com.google.common.util.concurrent.Service.Listener
import org.bitcoinj.core.TransactionConfidence.ConfidenceType
import org.bitcoinj.core.listeners.{DownloadProgressTracker, TransactionConfidenceEventListener}
import org.bitcoinj.core.{Address, _}
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.wallet.Wallet
import org.bytabit.ft.trade.model._
import org.bytabit.ft.util._
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.model._
import org.joda.money.Money
import org.joda.time.{DateTime, LocalDateTime}

object WalletManager {

  // states

  sealed trait State extends FSMState

  case object STARTING extends State {
    override val identifier: String = "STARTING"
  }

  case object RUNNING extends State {
    override val identifier: String = "RUNNING"
  }

  // data

  case class Data(kit: WalletAppKit) {

    def wallet = kit.wallet()
  }

  // wallet events

  trait Error

  sealed trait Event

  case object TradeWalletRunning extends Event

  case object EscrowWalletRunning extends Event

  case class TransactionUpdated(tx: Transaction, amt: Coin, confidenceType: ConfidenceType, blockDepth: Int) extends Event

  case class EscrowTransactionUpdated(p2shAddresses: List[Address], tx: Transaction, confidenceType: ConfidenceType) extends Event

  case class BalanceFound(balance: Coin) extends Event

  case class CurrentAddressFound(a: Address) extends Event

  case class ClientProfileIdCreated(clientProfileId: PubECKey) extends Event

  case class ArbitratorCreated(arbitrator: Arbitrator) extends Event

  case class BtcBuyOfferCreated(btcBuyOffer: BtcBuyOffer) extends Event

  case class BtcBuyOfferTaken(takenOffer: TakenOffer) extends Event

  case class TakenOfferSigned(signedTakenOffer: SignedTakenOffer) extends Event

  case class FiatSentCertified(certifiedFiatSent: CertifiedPayment) extends Event

  case class FiatNotSentCertified(certifiedFiatNotSent: CertifiedPayment) extends Event

  case class TxBroadcast(tx: Tx) extends Event

  case class BackupCodeGenerated(code: List[String], seedCreationTime: DateTime) extends Event

  case class WalletRestored() extends Event

  case class InsufficientBtc(c: TradeWalletManager.Command, required: Money, available: Money) extends Event with Error

  // block chain events

  sealed trait BlockChainEvent extends Event

  case class DownloadProgress(pct: Double, blocksSoFar: Int, date: LocalDateTime) extends BlockChainEvent

  case class BlockDownloaded(peer: Peer, block: Block, filteredBlock: FilteredBlock, blocksLeft: Int) extends BlockChainEvent

  case object DownloadDone extends BlockChainEvent

}

trait WalletManager extends FSM[State, Data] {

  // config

  val config = context.system.settings.config
  val walletDir = new File(config.getString(ConfigKeys.WALLET_DIR))
  val configName = config.getString(ConfigKeys.CONFIG_NAME)
  val walletNet = config.getString(ConfigKeys.WALLET_NET)
  val version = config.getString(ConfigKeys.VERSION)

  // create empty wallets dir
  if (!walletDir.isDirectory) {
    // try to create the directory, on failure double check if someone else beat us to it
    if (!walletDir.mkdirs() && !walletDir.isDirectory) {
      throw new IOException(s"Failed to create directory [${walletDir.getAbsolutePath}]")
    }
  }


  val dispatcher = context.system.dispatcher

  val netParams = NetworkParameters.fromID(walletNet)
  val btcContext = Context.getOrCreate(netParams)

  def kit: WalletAppKit

  def kitListener: Listener

  def txConfidenceEventListener = new TransactionConfidenceEventListener {

    override def onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction): Unit = {
      Context.propagate(btcContext)
      self ! TransactionUpdated(tx, tx.getValue(wallet),
        tx.getConfidence.getConfidenceType,
        tx.getConfidence.getDepthInBlocks)
    }
  }

  def downloadProgressTracker = new DownloadProgressTracker {

    override def onBlocksDownloaded(peer: Peer, block: Block, filteredBlock: FilteredBlock, blocksLeft: Int): Unit = {
      super.onBlocksDownloaded(peer, block, filteredBlock, blocksLeft)
      self ! BlockDownloaded(peer, block, filteredBlock, blocksLeft)
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

  def startWallet(k: WalletAppKit, dpt: DownloadProgressTracker): WalletAppKit = {
    Context.propagate(btcContext)
    // setup wallet app kit
    k.setAutoSave(true)
    k.setBlockingStartup(false)
    k.setUserAgent("org.bytabit.ft", version)
    k.setDownloadListener(dpt)
    k.addListener(kitListener, dispatcher)
    if (netParams == RegTestParams.get) k.connectToLocalHost()
    // start wallet app kit
    k.startAsync()
    k
  }

  def stopWallet(k: WalletAppKit): Unit = {
    Context.propagate(btcContext)
    k.stopAsync()
  }

  def broadcastOpenTx(k: WalletAppKit, ot: OpenTx): OpenTx = {
    Context.propagate(btcContext)
    val signed = ot.sign(k.wallet)
    broadcastSignedTx(k, signed)
    log.info(s"OpenTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
      s"size ${signed.tx.getMessageSize} bytes")
    signed
  }

  def broadcastFundTx(k: WalletAppKit, ft: FundTx): FundTx = {
    Context.propagate(btcContext)
    val signed = ft.sign(k.wallet)
    broadcastSignedTx(k, signed)
    log.info(s"FundTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
      s"size ${signed.tx.getMessageSize} bytes")
    signed
  }

  def broadcastPayoutTx(k: WalletAppKit, pt: PayoutTx, pk: PubECKey): PayoutTx = {
    Context.propagate(btcContext)
    val signed = pt.sign(pk)(k.wallet)
    broadcastSignedTx(k, signed)
    log.info(s"PayoutTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
      s"size ${signed.tx.getMessageSize} bytes")
    signed
  }

  def broadcastSignedTx(k: WalletAppKit, signed: Tx): Unit = {
    assert(signed.fullySigned)
    k.wallet.commitTx(signed.tx)
    k.peerGroup.broadcastTransaction(signed.tx)
  }
}

