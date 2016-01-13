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

package com.bytabit.ft.fxui

import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.net.URI
import javafx.beans.property.{SimpleDoubleProperty, SimpleStringProperty}
import javafx.collections.{FXCollections, ObservableList}
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.{Clipboard, ClipboardContent, MouseEvent}

import akka.actor.ActorSystem
import com.bytabit.ft.fxui.model.TransactionUIModel
import com.bytabit.ft.fxui.util.ActorFxService
import com.bytabit.ft.util.ListenerUpdater.AddListener
import com.bytabit.ft.util.{BTCMoney, Config, ListenerUpdater}
import com.bytabit.ft.wallet.WalletManager
import com.bytabit.ft.wallet.WalletManager._
import net.glxn.qrgen.QRCode
import net.glxn.qrgen.image.ImageType
import org.bitcoinj.core.{Address, NetworkParameters}
import org.bitcoinj.uri.BitcoinURI
import org.bitcoinj.wallet.KeyChain

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object WalletFxService {
  val name = "walletFxService"

  def apply(system: ActorSystem) = new WalletFxService(system)
}

class WalletFxService(system: ActorSystem) extends ActorFxService(system) {

  val walletMgrSel = system.actorSelection(s"/user/${WalletManager.name}")
  lazy val walletMgrRef = walletMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val transactions: ObservableList[TransactionUIModel] = FXCollections.observableArrayList[TransactionUIModel]

  val downloadProgress: SimpleDoubleProperty = new SimpleDoubleProperty(0)

  val walletBalance: SimpleStringProperty = new SimpleStringProperty("Unknown")

  override def start() {
    super.start()
    sendCmd(AddListener(inbox.getRef()))
    sendCmd(Start)
  }

  def findNewReceiveAddress(): Unit = {
    sendCmd(FindCurrentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS))
  }

  @Override
  def handler = {

    case Started =>
      sendCmd(FindTransactions)

    case DownloadProgress(pct, blocksSoFar, date) =>
      downloadProgress.set(pct)

    case DownloadDone =>
      downloadProgress.set(100)
      sendCmd(FindBalance)

    case BalanceFound(coinAmt) =>
      walletBalance.set(BTCMoney(coinAmt).toString)

    case TransactionUpdated(tx, coinAmt) =>
      val newTxUI = TransactionUIModel(tx, BTCMoney(coinAmt))
      transactions.find(t => t.getHash == newTxUI.getHash) match {
        case Some(t) => transactions.set(transactions.indexOf(t), newTxUI)
        case None => transactions.add(newTxUI)
      }
      sendCmd(FindBalance)

    case CurrentAddressFound(a) =>
      alertInfoNewReceiveAddress(a)

    case _ => log.error("Unexpected message")
  }

  def alertInfoNewReceiveAddress(a: Address): Unit = {
    // popup info
    val alert = new Alert(AlertType.INFORMATION)
    alert.setTitle(null)
    alert.setHeaderText("Deposit XBT Address")
    alert.setGraphic(new ImageView(qrCode(a)))
    alert.setContentText(s"${a.toString}\n\nClick Anywhere to Copy")
    alert.getDialogPane.setOnMouseClicked(new EventHandler[MouseEvent]() {
      override def handle(event: MouseEvent): Unit = {
        copyAddress(a)
        if (Config.walletNet == NetworkParameters.ID_MAINNET)
        // TODO warn user bitcoin wallet will be launched
          requestMoney(a)
      }
    })

    alert.showAndWait
  }

  def depositAddressUri(a: Address): String = BitcoinURI.convertToBitcoinURI(a, null, Config.config, "Deposit")

  def copyAddress(address: Address): Unit = {
    val clipboard = Clipboard.getSystemClipboard
    val content = new ClipboardContent()
    val addressStr = address.toString

    content.putString(addressStr)
    content.putHtml(s"<a href=${depositAddressUri(address)}>$addressStr</a>")
    clipboard.setContent(content)
  }

  def requestMoney(address: Address): Unit = {
    Desktop.getDesktop.browse(URI.create(depositAddressUri(address)))
  }

  def qrCode(address: Address): Image = {
    val imageBytes = QRCode.from(depositAddressUri(address)).withSize(320, 240).to(ImageType.PNG).stream.toByteArray
    new Image(new ByteArrayInputStream(imageBytes))
  }

  def sendCmd(cmd: WalletManager.Command) = sendMsg(walletMgrRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = sendMsg(walletMgrRef, cmd)
}