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

import akka.actor.Props
import com.google.common.util.concurrent.Service.Listener
import org.bitcoinj.core._
import org.bitcoinj.kits.WalletAppKit
import org.bytabit.ft.util.Config
import org.bytabit.ft.wallet.EscrowWalletManager.{AddWatchAddress, BroadcastSignedTx, RemoveWatchAddress}
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.model._
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.util.Try

// ephemeral wallet to listen for trade transactions to/from escrow addresses

object EscrowWalletManager {

  def props(config: Config) = Props(new EscrowWalletManager(config: Config))

  val name = s"escrowWalletManager"

  sealed trait Command

  case class AddWatchAddress(escrowAddress: Address, creationTime: DateTime) extends Command

  case class RemoveWatchAddress(escrowAddress: Address) extends Command

  case class BroadcastSignedTx(tx: Tx) extends Command

}

class EscrowWalletManager(appConfig: Config) extends WalletManager {

  override val config = appConfig
  val directory = config.walletDir
  val filePrefix = s"${config.configName}-escrow"

  def kit: WalletAppKit = new WalletAppKit(btcContext, directory, filePrefix)

  def kitListener = new Listener {

    override def running(): Unit = {
      self ! EscrowWalletRunning
    }
  }

  startWith(STARTING, Data(startWallet(kit, downloadProgressTracker)))

  when(STARTING) {

    case Event(EscrowWalletRunning, Data(k)) =>
      Context.propagate(btcContext)
      k.wallet.addTransactionConfidenceEventListener(txConfidenceEventListener)
      context.system.eventStream.publish(EscrowWalletRunning)
      goto(RUNNING) using Data(k)

    // handle block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      stay()
  }

  when(RUNNING) {

    case Event(AddWatchAddress(address, creationTime), Data(k)) =>
      context.system.eventStream.subscribe(context.sender(), classOf[EscrowTransactionUpdated])
      Context.propagate(btcContext)
      if (k.wallet.addWatchedAddress(address, creationTime.getMillis / 1000)) {
        // stop wallet, delete chainFile and restart wallet
        k.stopAsync().awaitTerminated()
        val chainFile: File = new File(directory, filePrefix + ".spvchain")
        val success = chainFile.delete()
        val newKit = startWallet(kit, downloadProgressTracker)
        goto(STARTING) using Data(newKit)
      }
      else stay()

    case Event(RemoveWatchAddress(escrowAddress: Address), Data(k)) =>
      context.system.eventStream.unsubscribe(context.sender(), classOf[EscrowTransactionUpdated])
      Context.propagate(btcContext)
      assert(escrowAddress.isP2SHAddress)
      k.wallet.removeWatchedAddress(escrowAddress)
      goto(RUNNING) using Data(k)

    case Event(BroadcastSignedTx(tx: Tx), Data(k)) =>
      broadcastSignedTx(k, tx)
      stay()

    case Event(TransactionUpdated(tx, amt, ct, bd), Data(w)) =>
      Context.propagate(btcContext)
      // find P2SH addresses in inputs and outputs
      val p2shAddresses: List[Address] = (tx.getInputs.toList.map(i => p2shAddress(i.getConnectedOutput))
        ++ tx.getOutputs.toList.map(o => p2shAddress(o))).flatten
      context.system.eventStream.publish(EscrowTransactionUpdated(p2shAddresses, tx, tx.getConfidence.getConfidenceType))
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
}
