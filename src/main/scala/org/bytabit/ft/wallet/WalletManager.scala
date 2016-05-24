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
import org.joda.time.LocalDateTime

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

  case class Data(kit: WalletAppKit,
                  walletListeners: Set[ActorRef] = Set(),
                  addressListeners: Map[Address, ActorRef] = Map()) {

    def wallet = kit.wallet()
  }

  // wallet events

  sealed trait Event

  case object TradeWalletRunning extends Event

  case object EscrowWalletRunning extends Event

  case class TransactionUpdated(tx: Transaction, amt: Coin, confidenceType: ConfidenceType, blockDepth: Int) extends Event

  case class EscrowTransactionUpdated(tx: Transaction, confidenceType: ConfidenceType) extends Event

  case class BalanceFound(balance: Coin) extends Event

  case class CurrentAddressFound(a: Address) extends Event

  case class ArbitratorCreated(arbitrator: Arbitrator) extends Event

  case class SellOfferCreated(sellOffer: SellOffer) extends Event

  case class SellOfferTaken(takenOffer: TakenOffer) extends Event

  case class TakenOfferSigned(signedTakenOffer: SignedTakenOffer) extends Event

  case class FiatSentCertified(certifiedFiatSent: CertifiedFiatDelivery) extends Event

  case class FiatNotSentCertified(certifiedFiatNotSent: CertifiedFiatDelivery) extends Event

  case class TxBroadcast(tx: Tx) extends Event

  // block chain events

  sealed trait BlockChainEvent extends Event

  case class DownloadProgress(pct: Double, blocksSoFar: Int, date: LocalDateTime) extends BlockChainEvent

  case class BlockDownloaded(peer: Peer, block: Block, filteredBlock: FilteredBlock, blocksLeft: Int) extends BlockChainEvent

  case object DownloadDone extends BlockChainEvent

}

trait WalletManager extends FSM[State, Data] {

  val dispatcher = context.system.dispatcher

  val netParams = NetworkParameters.fromID(Config.walletNet)
  val btcContext = Context.getOrCreate(netParams)
  val kit: WalletAppKit
  val kitListener: Listener

  val txConfidenceEventListener = new TransactionConfidenceEventListener {

    override def onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction): Unit = {
      Context.propagate(btcContext)
      self ! TransactionUpdated(tx, tx.getValue(wallet),
        tx.getConfidence.getConfidenceType,
        tx.getConfidence.getDepthInBlocks)
    }
  }

  val downloadProgressTracker = new DownloadProgressTracker {

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

  override def postStop(): Unit = {
    super.postStop()
    stopWallet()
  }

  def startWallet(dpt: DownloadProgressTracker, autoSave: Boolean = true) = {
    Context.propagate(btcContext)
    // setup wallet app kit
    kit.setAutoSave(true)
    kit.setBlockingStartup(false)
    kit.setUserAgent(Config.config, Config.version)
    kit.setDownloadListener(dpt)
    kit.addListener(kitListener, dispatcher)
    kit.setAutoSave(autoSave)
    if (netParams == RegTestParams.get) kit.connectToLocalHost()

    // start wallet app kit
    kit.startAsync()
  }

  def stopWallet(): Unit = {
    Context.propagate(btcContext)
    kit.stopAsync()
  }

  def sendToListeners(event: Any, listeners: Seq[ActorRef]) =
    listeners foreach { l =>
      log.debug("send event: {} to listener: {}", event, l)
      l ! event
    }

  def broadcastOpenTx(w: Wallet, ot: OpenTx): OpenTx = {
    Context.propagate(btcContext)
    val signed = ot.sign(w)
    broadcastSignedTx(w, signed)
    log.info(s"OpenTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
      s"size ${signed.tx.getMessageSize} bytes")
    signed
  }

  def broadcastFundTx(w: Wallet, ft: FundTx): FundTx = {
    Context.propagate(btcContext)
    val signed = ft.sign(w)
    broadcastSignedTx(w, signed)
    log.info(s"FundTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
      s"size ${signed.tx.getMessageSize} bytes")
    signed
  }

  def broadcastPayoutTx(w: Wallet, pt: PayoutTx, pk: PubECKey): PayoutTx = {
    Context.propagate(btcContext)
    val signed = pt.sign(pk)(w)
    broadcastSignedTx(w, signed)
    log.info(s"PayoutTx broadcast, ${signed.inputs.length} inputs, ${signed.outputs.length} outputs, " +
      s"size ${signed.tx.getMessageSize} bytes")
    signed
  }

  def broadcastSignedTx(w: Wallet, signed: Tx): Unit = {
    assert(signed.fullySigned)
    w.commitTx(signed.tx)
    kit.peerGroup.broadcastTransaction(signed.tx)
    // TODO do this from the TradeProcess level
    // escrowKit.peerGroup.broadcastTransaction(signed.copy().tx)
  }
}

