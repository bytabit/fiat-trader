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

package org.bytabit.ft.fxui

import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.net.URI
import javafx.beans.property.{SimpleDoubleProperty, SimpleStringProperty}
import javafx.collections.{FXCollections, ObservableList}
import javafx.event.EventHandler
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control._
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.{Clipboard, ClipboardContent, MouseEvent}
import javafx.scene.layout.GridPane
import javafx.util.Callback

import akka.actor.ActorSystem
import net.glxn.qrgen.QRCode
import net.glxn.qrgen.image.ImageType
import org.bitcoinj.core.{Address, NetworkParameters}
import org.bitcoinj.uri.BitcoinURI
import org.bitcoinj.wallet.KeyChain
import org.bytabit.ft.fxui.model.TransactionUIModel
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.util.{BTCMoney, Config, ListenerUpdater}
import org.bytabit.ft.wallet.TradeWalletManager._
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.{EscrowWalletManager, TradeWalletManager}
import org.joda.money.Money

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object WalletFxService {
  val name = "walletFxService"

  def apply(system: ActorSystem) = new WalletFxService(system)
}

class WalletFxService(actorSystem: ActorSystem) extends ActorFxService {

  override val system = actorSystem

  val tradeWalletMgrSel = system.actorSelection(s"/user/${TradeWalletManager.name}")
  lazy val tradeWalletMgrRef = tradeWalletMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  val escrowWalletMgrSel = system.actorSelection(s"/user/${EscrowWalletManager.name}")
  lazy val escrowWalletMgrRef = escrowWalletMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val transactions: ObservableList[TransactionUIModel] = FXCollections.observableArrayList[TransactionUIModel]

  val downloadProgress: SimpleDoubleProperty = new SimpleDoubleProperty(0)

  val walletBalance: SimpleStringProperty = new SimpleStringProperty("Unknown")

  override def start() {
    super.start()
    //sendCmd(AddListener(inbox.getRef()))
    sendCmd(TradeWalletManager.Start)
    sendCmd(EscrowWalletManager.Start)
  }

  def findNewReceiveAddress(): Unit = {
    sendCmd(FindCurrentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS))
  }

  @Override
  def handler = {

    case TradeWalletRunning =>
      sendCmd(FindTransactions)

    case DownloadProgress(pct, blocksSoFar, date) =>
      downloadProgress.set(pct)

    case DownloadDone =>
      downloadProgress.set(100)
      sendCmd(FindBalance)

    case BalanceFound(coinAmt) =>
      walletBalance.set(BTCMoney(coinAmt).toString)

    case TransactionUpdated(tx, coinAmt, ct, cd) =>
      val newTxUI = TransactionUIModel(tx, BTCMoney(coinAmt), ct, cd)
      transactions.find(t => t.getHash == newTxUI.getHash) match {
        case Some(t) => transactions.set(transactions.indexOf(t), newTxUI)
        case None => transactions.add(newTxUI)
      }
      sendCmd(FindBalance)

    case CurrentAddressFound(a) =>
      alertInfoNewReceiveAddress(a)

    case TxBroadcast(_) =>
    // do nothing

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
        // TODO FT-22: warn user bitcoin wallet will be launched
          requestMoney(a)
      }
    })

    alert.showAndWait
  }

  def dialogWithdrawBtc(): Unit = {

    val dialog = new Dialog[(String, Money)]()
    dialog.setTitle("Withdraw")
    dialog.setHeaderText("Enter destination bitcoin address and amount to withdraw.")

    val addrLabel = new Label("Address: ")
    val addrTextField = new TextField()

    val amtLabel = new Label("Amount (XBT): ")
    val amtTextField = new TextField()

    val grid = new GridPane()
    grid.add(addrLabel, 1, 1)
    grid.add(addrTextField, 2, 1)
    grid.add(amtLabel, 1, 2)
    grid.add(amtTextField, 2, 2)
    dialog.getDialogPane.setContent(grid)

    val okButtonType = new ButtonType("OK", ButtonData.OK_DONE)
    val cancelButtonType = new ButtonType("CANCEL", ButtonData.CANCEL_CLOSE)

    dialog.getDialogPane.getButtonTypes.addAll(okButtonType, cancelButtonType)

    dialog.setResultConverter(new Callback[ButtonType, (String, Money)]() {
      override def call(bt: ButtonType): (String, Money) = {
        if (bt == okButtonType) {
          val addr = addrTextField.getText
          // TODO FT-100: validate wallet withdraw destination address format
          val amt = BTCMoney(amtTextField.getText)
          // TODO FT-101: validate wallet withdraw amount
          // TODO FT-102: require password or PIN to withdraw money from wallet
          (addr, amt)
        } else {
          null
        }
      }
    })

    val result = dialog.showAndWait()
    if (result.isPresent) {
      log.info(s"Requested withdraw info: ${result.get}")
      sendCmd(WithdrawXBT(result.get._1, result.get._2))
    }
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

  def sendCmd(cmd: TradeWalletManager.Command) = sendMsg(tradeWalletMgrRef, cmd)

  def sendCmd(cmd: EscrowWalletManager.Command) = sendMsg(escrowWalletMgrRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = sendMsg(tradeWalletMgrRef, cmd)
}