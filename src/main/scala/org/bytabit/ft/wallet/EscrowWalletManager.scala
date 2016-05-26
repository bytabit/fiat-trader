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
import org.bytabit.ft.wallet.EscrowWalletManager.{AddWatchEscrowAddress, BroadcastSignedTx, RemoveWatchEscrowAddress, Start}
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.model._

import scala.collection.JavaConversions._
import scala.util.Try

// ephemeral wallet to listen for trade transactions to/from escrow addresses

object EscrowWalletManager {

  val props = Props(new EscrowWalletManager)
  val name = s"escrowWalletManager"

  def actorOf(system: ActorSystem) = system.actorOf(props, name)

  sealed trait Command

  case object Start extends Command

  case class AddWatchEscrowAddress(escrowAddress: Address) extends Command

  case class RemoveWatchEscrowAddress(escrowAddress: Address) extends Command

  case class BroadcastSignedTx(tx: Tx) extends Command

}

class EscrowWalletManager extends WalletManager {

  val kit: WalletAppKit = new WalletAppKit(btcContext, new File(Config.walletDir), s"${Config.config}-escrow")

  val kitListener = new Listener {

    override def running(): Unit = {
      self ! EscrowWalletRunning
    }
  }

  startWith(STARTING, Data(kit))

  when(STARTING) {

    case Event(Start, Data(k, _, _)) =>
      startWallet(k, downloadProgressTracker, autoSave = false)
      stay()

    case Event(EscrowWalletRunning, Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      w.addTransactionConfidenceEventListener(txConfidenceEventListener)
      al.keys.foreach(ea => w.addWatchedAddress(ea))
      sendToListeners(EscrowWalletRunning, wl.toSeq)
      goto(RUNNING) using Data(k, wl, al)

    case Event(AddWatchEscrowAddress(escrowAddress: Address), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      assert(escrowAddress.isP2SHAddress)
      goto(STARTING) using Data(k, wl, al + (escrowAddress -> context.sender()))

    case Event(RemoveWatchEscrowAddress(escrowAddress: Address), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      assert(escrowAddress.isP2SHAddress)
      if (al.contains(escrowAddress)) {
        goto(STARTING) using Data(k, wl, al - escrowAddress)
      } else {
        stay()
      }

    // handle block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      stay()
  }

  when(RUNNING) {

    case Event(AddWatchEscrowAddress(escrowAddress: Address), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      assert(escrowAddress.isP2SHAddress)
      //addressListeners = addressListeners + (escrowAddress -> context.sender())
      w.addWatchedAddress(escrowAddress)
      //log.info(s"ADDED event listener for address: $escrowAddress listener: ${context.sender()}")
      goto(RUNNING) using Data(k, wl, al + (escrowAddress -> context.sender()))

    case Event(RemoveWatchEscrowAddress(escrowAddress: Address), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      assert(escrowAddress.isP2SHAddress)
      if (al.contains(escrowAddress)) {
        w.removeWatchedAddress(escrowAddress)
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
}
