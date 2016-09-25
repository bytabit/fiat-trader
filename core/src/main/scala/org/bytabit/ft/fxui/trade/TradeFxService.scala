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

package org.bytabit.ft.fxui.trade

import java.net.URL
import java.util.UUID
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ObservableList
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control._
import javafx.scene.layout.GridPane
import javafx.util.Callback

import akka.actor.ActorSystem
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.arbitrator.ArbitratorManager.{ArbitratorCreated, ContractAdded, ContractRemoved}
import org.bytabit.ft.client._
import org.bytabit.ft.fxui.util.TradeDataFxService
import org.bytabit.ft.trade.BtcBuyProcess.{AddBtcBuyOffer, CancelBtcBuyOffer, SendFiat}
import org.bytabit.ft.trade.BtcSellProcess.{ReceiveFiat, TakeBtcBuyOffer}
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.util.{BTCMoney, _}
import org.bytabit.ft.wallet.WalletManager.InsufficientBtc
import org.bytabit.ft.wallet.{TradeWalletManager, WalletManager}
import org.joda.money.{CurrencyUnit, Money}

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object TradeFxService {
  def apply(system: ActorSystem) = new TradeFxService(system)
}

class TradeFxService(actorSystem: ActorSystem) extends TradeDataFxService {

  override val system = actorSystem

  val clientMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val clientMgrRef = clientMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // TODO FT-99: disable btc buy and sell buttons if current trade is uncommitted
  val tradeUncommitted: SimpleBooleanProperty = new SimpleBooleanProperty(false)

  // Private Data
  private var contracts: Seq[Contract] = Seq()
  private var btcBuyCurrencyUnitSelected: Option[CurrencyUnit] = None
  private var btcBuyContractSelected: Option[Contract] = None

  override def start() {
    if (!Config.arbitratorEnabled) {
      super.start()
      system.eventStream.subscribe(inbox.getRef(), classOf[ArbitratorManager.Event])
      system.eventStream.subscribe(inbox.getRef(), classOf[TradeProcess.Event])
      system.eventStream.subscribe(inbox.getRef(), classOf[WalletManager.InsufficientBtc])
    }
  }

  override def handler = {

    // Handle client events

    case e: EventClient.ServerOnline =>
    //log.info(s"ServerOnline at URL: ${u}")

    case e: EventClient.ServerOffline =>
    //log.info(s"ServerOnline at URL: ${u}")

    // Handle Arbitrator Events

    case ArbitratorCreated(u, a, _) =>
    //log.info(s"ArbitratorCreated at URL: ${u}")

    case ContractAdded(u, c, _) =>
      contracts = contracts :+ c
      updateCurrencyUnits(contracts, btcBuyCurrencyUnits)
      updatePaymentMethods(contracts, btcBuyPaymentMethods, btcBuyCurrencyUnitSelected)

    case ContractRemoved(url, id, _) =>
      contracts = contracts.filterNot(_.id == id)
      updateCurrencyUnits(contracts, btcBuyCurrencyUnits)
      updateCurrencyUnits(contracts, btcBuyCurrencyUnits)
      updatePaymentMethods(contracts, btcBuyPaymentMethods, btcBuyCurrencyUnitSelected)

    // Handle Trade Events

    // common path

    case LocalBtcBuyerCreatedOffer(id, btcBuyOffer, p) =>
      createOffer(BTCBUYER, btcBuyOffer)
      updateUncommitted()

    case BtcBuyerCreatedOffer(id, btcBuyOffer, p) =>
      createOffer(BTCSELLER, btcBuyOffer)
      updateUncommitted()

    case bto: BtcSellerTookOffer =>
      takeOffer(bto)
      updateUncommitted()

    case sso: BtcBuyerSignedOffer =>
      signOffer(sso)
      updateUncommitted()

    case boe: BtcSellerOpenedEscrow =>
      openEscrow(boe)
      updateUncommitted()

    case bfe: BtcSellerFundedEscrow =>
      fundEscrow(bfe)
      updateUncommitted()

    case InsufficientBtc(cbo: TradeWalletManager.CreateBtcBuyOffer, r, a) =>
      dialogError("Insufficient BTC", s"Insufficient wallet balance to create buy offer.\n\nTrade requires: $r\nAvailable balance: $a")

    case InsufficientBtc(tbo: TradeWalletManager.TakeBtcBuyOffer, r, a) =>
      dialogError("Insufficient BTC", s"Insufficient wallet balance to create buy offer.\n\nTrade requires: $r\nAvailable balance: $a")

    // happy path

    case fs: BtcBuyerFiatSent =>
      fiatSent(fs)
      updateUncommitted()

    case fr: FiatReceived =>
      fiatReceived(fr)
      updateUncommitted()

    case BtcSellerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)
      updateUncommitted()

    case BtcBuyerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)
      updateUncommitted()

    // unhappy path

    case cdr: CertifyPaymentRequested =>
      reqCertPayment(cdr)
      updateUncommitted()

    case fsc: FiatSentCertified =>
      certifyFiatSent(fsc)
      updateUncommitted()

    case fnc: FiatNotSentCertified =>
      certifyFiatNotSent(fnc)
      updateUncommitted()

    case sf: BtcBuyerFunded =>
      fundBtcBuyer(sf)
      updateUncommitted()

    case rb: BtcSellerRefunded =>
      refundBtcSeller(rb)
      updateUncommitted()

    // cancel path

    case BtcBuyerCanceledOffer(id, p) =>
      removeTradeUIModel(id)
      updateUncommitted()

    // errors

    case e: EventClient.Event =>
      log.error(s"Unhandled EventClient event: $e")

    case e: ArbitratorManager.Event =>
      log.error(s"Unhandled ArbitratorManager event: $e")

    case e: TradeProcess.Event =>
      log.error(s"Unhandled TradeProcess event: $e")

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  def setSelectedAddCurrencyUnit(sacu: CurrencyUnit) = {
    btcBuyCurrencyUnitSelected = Some(sacu)
    updatePaymentMethods(contracts, btcBuyPaymentMethods, btcBuyCurrencyUnitSelected)
  }

  def setSelectedContract(dm: PaymentMethod) = {
    // TODO FT-21: allow user to rank arbitrators in arbitrator client screen so UI can auto pick top ranked one
    // for now pick lowest fee contract template that matches currency and payment method
    btcBuyContractSelected = for {
      fcu <- btcBuyCurrencyUnitSelected
      c <- contracts.filter(t => t.fiatCurrencyUnit == fcu && t.paymentMethod == dm)
        .sortWith((x, y) => x.arbitrator.btcArbitratorFee.isGreaterThan(y.arbitrator.btcArbitratorFee)).headOption
    } yield c

    updateBtcBuyContract(btcBuyContractSelected)
  }

  def updateCurrencyUnits(cts: Seq[Contract], acu: ObservableList[CurrencyUnit]) = {
    val existingCus = btcBuyCurrencyUnits.toList
    val foundCus = cts.map(ct => ct.fiatCurrencyUnit).distinct
    val addCus = foundCus.filterNot(existingCus.contains(_))
    val rmCus = existingCus.filterNot(foundCus.contains(_))
    acu.addAll(addCus)
    acu.removeAll(rmCus)
    //acu.sort(Ordering.String)
  }

  def updatePaymentMethods(cts: Seq[Contract], apm: ObservableList[PaymentMethod], cuf: Option[CurrencyUnit]) = {
    val existingPms = btcBuyPaymentMethods.toList
    val filteredCts = cuf.map(cu => cts.filter(ct => ct.fiatCurrencyUnit.equals(cu))).getOrElse(cts)
    val foundDms = filteredCts.map(ct => ct.paymentMethod).distinct
    val addDms = foundDms.filterNot(existingPms.contains(_))
    val rmDms = existingPms.filterNot(foundDms.contains)
    btcBuyPaymentMethods.addAll(addDms)
    btcBuyPaymentMethods.removeAll(rmDms)
  }

  def updateBtcBuyContract(contract: Option[Contract]) = {
    contract.foreach { c =>
      btcBuyBondPercent.set(f"${c.arbitrator.bondPercent * 100}%f")
      btcBuyArbitratorFee.set(c.arbitrator.btcArbitratorFee.toString)
    }
  }

  def calculateAddBtcAmt(fiatAmt: String, exchRate: String): String = {
    try {
      btcBuyCurrencyUnitSelected.map { cu =>
        val fa: Money = FiatMoney(cu, fiatAmt)
        val er: BigDecimal = BigDecimal(1.0) / BigDecimal(exchRate)
        fa.convertedTo(CurrencyUnits.XBT, er.bigDecimal, Monies.roundingMode).getAmount.toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  def calculateAddFiatAmt(btcAmt: String, exchRate: String): String = {
    try {
      btcBuyCurrencyUnitSelected.map { cu =>
        val ba: Money = BTCMoney(btcAmt)
        val er: BigDecimal = BigDecimal(1.0) / BigDecimal(exchRate)
        ba.convertedTo(cu, er.bigDecimal, Monies.roundingMode).getAmount.toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  def calculateAddFiatAmt(fiatAmt: String): String = {
    try {
      btcBuyCurrencyUnitSelected.map { cu =>
        FiatMoney(cu, fiatAmt).toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  def calculateAddBtcAmt(btcAmt: String): String = {
    try {
      btcBuyCurrencyUnitSelected.map { cu =>
        BTCMoney(btcAmt).toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  def createBtcBuyOffer(fcu: CurrencyUnit, fiatAmount: Money, btcAmount: Money, fdm: PaymentMethod) = {

    btcBuyContractSelected.foreach { c =>
      val o = Offer(UUID.randomUUID(), c, fiatAmount, btcAmount)
      sendCmd(AddBtcBuyOffer(o.url, o.id, o))
    }
  }

  def cancelBtcBuyOffer(url: URL, tradeId: UUID): Unit = {
    sendCmd(CancelBtcBuyOffer(url, tradeId))
  }

  def takeBtcBuyOffer(url: URL, tradeId: UUID): Unit = {
    sendCmd(TakeBtcBuyOffer(url, tradeId))
  }

  // TODO FT-91: collect evidence
  def btcBuyerReqCertPayment(url: URL, tradeId: UUID): Unit = {
    sendCmd(BtcBuyProcess.RequestCertifyPayment(url, tradeId))
  }

  // TODO FT-91: collect evidence
  def btcSellerReqCertPayment(url: URL, tradeId: UUID): Unit = {
    sendCmd(BtcSellProcess.RequestCertifyPayment(url, tradeId))
  }

  def updateUncommitted() = {
    tradeUncommitted.set(trades.exists(_.uncommitted))
  }

  def sendCmd(cmd: BtcBuyProcess.Command) = sendMsg(clientMgrRef, cmd)

  def sendCmd(cmd: BtcSellProcess.Command) = sendMsg(clientMgrRef, cmd)

  def dialogError(title: String, reason: String): Unit = {
    val alert = new Alert(AlertType.ERROR)
    alert.setTitle(title)
    alert.setHeaderText(title)
    alert.setContentText(reason)
    alert.showAndWait()
  }

  def dialogSendFiatError(url: URL, tradeId: UUID, tradeData: TradeData): Unit = {
    // TODO dialog to tell user why can't send fiat, not funded yet
    //sendCmd(SendFiat(url, tradeId))
  }

  def sendFiatDialog(url: URL, tradeId: UUID, trade: TradeData): Unit = {

    trade match {

      case ft: FundedTrade =>
        val dialog = new Dialog[String]()
        dialog.setTitle("Send Fiat")

        dialog.setHeaderText(s"Send ${ft.contract.fiatCurrencyUnit.toString}")

        val fiatSentRefLabel = new Label(s"Send ${ft.fiatAmount} via ${ft.contract.paymentMethod.name} to ${ft.paymentDetails}")
        val fiatSentRefTextField = new TextField()
        fiatSentRefTextField.setPrefWidth(500)
        fiatSentRefTextField.setMaxWidth(500)
        fiatSentRefTextField.setPromptText(ft.contract.paymentMethod.requiredReference)

        val grid = new GridPane()
        grid.add(fiatSentRefLabel, 0, 0)
        grid.add(fiatSentRefTextField, 0, 1)
        dialog.getDialogPane.setContent(grid)

        val okButtonType = new ButtonType("OK", ButtonData.OK_DONE)
        val cancelButtonType = new ButtonType("CANCEL", ButtonData.CANCEL_CLOSE)

        dialog.getDialogPane.getButtonTypes.addAll(okButtonType, cancelButtonType)

        dialog.setResultConverter(new Callback[ButtonType, String]() {
          override def call(bt: ButtonType): String = {
            if (bt == okButtonType) {
              fiatSentRefTextField.getText
            } else {
              null
            }
          }
        })

        val result = dialog.showAndWait()
        if (result.isPresent) {
          log.info(s"Fiat sent reference: ${result.get}")
          sendCmd(SendFiat(url, tradeId, Some(result.get)))
        }
      case _ =>
      // TODO show error
    }
  }

  def receiveFiatDialog(url: URL, tradeId: UUID, trade: TradeData): Unit = {

    trade match {

      case ft: FundedTrade =>
        val dialog = new Alert(AlertType.CONFIRMATION)
        dialog.setTitle("Receive Fiat")

        dialog.setHeaderText(s"Receive ${ft.contract.fiatCurrencyUnit.toString}")
        dialog.setContentText(s"Receive ${ft.fiatAmount} via ${ft.contract.paymentMethod.name} to ${ft.paymentDetails} with reference: ${ft.fiatSentReference.getOrElse("NONE")}")

        val result = dialog.showAndWait()
        if (result.get().equals(ButtonType.OK)) {
          sendCmd(ReceiveFiat(url, tradeId))
        }
      case _ =>
      // TODO show error
    }
  }
}