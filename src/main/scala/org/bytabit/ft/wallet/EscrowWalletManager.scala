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

import akka.actor.{ActorSystem, Props}
import com.google.common.util.concurrent.Service.Listener
import org.bitcoinj.core._
import org.bitcoinj.kits.WalletAppKit
import org.bytabit.ft.util.Config
import org.bytabit.ft.wallet.EscrowWalletManager.{AddWatchAddress, BroadcastSignedTx, RemoveWatchAddress, Start}
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.model._
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.util.Try

// ephemeral wallet to listen for trade transactions to/from escrow addresses

object EscrowWalletManager {

  val props = Props(new EscrowWalletManager)
  val name = s"escrowWalletManager"

  def actorOf(system: ActorSystem) = system.actorOf(props, name)

  sealed trait Command

  case object Start extends Command

  case class AddWatchAddress(escrowAddress: Address, creationTime: DateTime) extends Command

  case class RemoveWatchAddress(escrowAddress: Address) extends Command

  case class BroadcastSignedTx(tx: Tx) extends Command

}

class EscrowWalletManager extends WalletManager {

  val directory = new File(Config.walletDir)
  val filePrefix = s"${Config.config}-escrow"

  def kit: WalletAppKit = new WalletAppKit(btcContext, directory, filePrefix)

  def kitListener = new Listener {

    override def running(): Unit = {
      self ! EscrowWalletRunning
    }
  }

  startWith(STARTING, Data(kit))

  when(STARTING) {

    case Event(Start, Data(k, _, _)) =>
      startWallet(k, downloadProgressTracker)
      stay()

    case Event(EscrowWalletRunning, Data(k, wl, al)) =>
      Context.propagate(btcContext)
      //      k.wallet.reset()
      //      k.peerGroup().setFastCatchupTimeSecs(0)
      k.wallet.addTransactionConfidenceEventListener(txConfidenceEventListener)
      // al.keys.foreach(ea => k.wallet.addWatchedAddress(ea))
      // TODO replay tx arbitrator may have missed
      // k.wallet.clearTransactions(0)
      sendToListeners(EscrowWalletRunning, wl.toSeq)
      goto(RUNNING) using Data(k, wl, al)

    case Event(AddWatchAddress(address, creationTime), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      if (k.wallet.addWatchedAddress(address, creationTime.getMillis / 1000)) {
        // stop wallet, delete chainFile and restart wallet
        k.stopAsync().awaitTerminated()
        val chainFile: File = new File(directory, filePrefix + ".spvchain")
        val success = chainFile.delete()
        val newKit = startWallet(kit, downloadProgressTracker)
        log.info(s"ADDED event listener for address: $address listener: $sender")
        goto(STARTING) using Data(newKit, wl, al + (address -> sender))
      }
      else stay()

    case Event(RemoveWatchAddress(address: Address), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      if (al.contains(address)) {
        goto(STARTING) using Data(k, wl, al - address)
      }
      else stay()

    // handle block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      stay()
  }

  when(RUNNING) {

    case Event(AddWatchAddress(address, creationTime), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      if (k.wallet.addWatchedAddress(address, creationTime.getMillis / 1000)) {
        // stop wallet, delete chainFile and restart wallet
        k.stopAsync().awaitTerminated()
        val chainFile: File = new File(directory, filePrefix + ".spvchain")
        val success = chainFile.delete()
        val newKit = startWallet(kit, downloadProgressTracker)
        //log.info(s"ADDED event listener for address: $address listener: $sender")
        goto(STARTING) using Data(newKit, wl, al + (address -> sender))
      }
      else stay()

    case Event(RemoveWatchAddress(escrowAddress: Address), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      assert(escrowAddress.isP2SHAddress)
      if (al.contains(escrowAddress)) {
        k.wallet.removeWatchedAddress(escrowAddress)
        goto(RUNNING) using Data(k, wl, al - escrowAddress)
        //log.info(s"REMOVED event listener for address: $escrowAddress listener: $ar")
      } else {
        stay()
      }

    case Event(BroadcastSignedTx(tx: Tx), Data(k, wl, al)) =>
      broadcastSignedTx(k, tx)
      stay()

    case Event(TransactionUpdated(tx, amt, ct, bd), Data(w, wl, al)) =>
      Context.propagate(btcContext)
      // find P2SH addresses in inputs and outputs
      val foundAddrs: List[Address] = (tx.getInputs.toList.map(i => p2shAddress(i.getConnectedOutput))
        ++ tx.getOutputs.toList.map(o => p2shAddress(o))).flatten

      // send TX to actor ref listening for that P2SH address
      foundAddrs.foreach { a =>
        al.get(a) match {
          case Some(ar) =>
            ar ! EscrowTransactionUpdated(tx, tx.getConfidence.getConfidenceType)
          //log.info(s"EscrowTransactionUpdated for $a sent to $ar")
          case _ =>
          // do nothing
        }
      }
      stay()

    // handle block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      stay()

  }

  def p2shAddress(output: TransactionOutput): Option[Address] = Try(output.getAddressFromP2SH(netParams)).toOption

  def addWatchedAddress(k: WalletAppKit, a: Address, ct: DateTime): Boolean = {
    if (!k.wallet.isAddressWatched(a)) {
      k.wallet.addWatchedAddress(a, ct.getMillis / 1000)
    }
    else false
  }

  //  def replayWallet(k: WalletAppKit, replayDateTime: Option[DateTime], useFastCatchup: Boolean, clearMemPool: Boolean): Unit = {
  //
  //    // Stop the peer group if it is running
  //    stopPeerGroup(k)
  //
  //    // Reset the mem pool - this will ensure transactions will be re-downloaded
  //    if (clearMemPool) {
  //      val memPool: TxConfidenceTable = Context.get.getConfidenceTable
  //      memPool.
  //    }
  //
  //  }
  //
  //  def stopPeerGroup(k: WalletAppKit): Unit = {
  //    if (k.peerGroup != null) {
  //      log.debug("Stopping peerGroup service...")
  //      k.peerGroup.removeWallet(k.wallet())
  //      log.debug("Service peerGroup stopped")
  //    }
  //    else {
  //      log.debug("Peer group was not present")
  //    }
  //  }
}
