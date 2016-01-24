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

import java.net.URL
import java.util.UUID
import java.util.function.Predicate
import javafx.beans.property.{SimpleBooleanProperty, SimpleStringProperty}
import javafx.collections.{FXCollections, ObservableList}

import akka.actor.ActorSystem
import org.bytabit.ft.fxui.model.TradeUIModel
import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, Role, SELLER}
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.notary.NotaryClientFSM.{ContractAdded, ContractRemoved}
import org.bytabit.ft.notary._
import org.bytabit.ft.trade.BuyFSM.{ReceiveFiat, TakeSellOffer}
import org.bytabit.ft.trade.SellFSM.{AddSellOffer, CancelSellOffer}
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade._
import org.bytabit.ft.trade.model.{Contract, Offer, SellOffer}
import org.bytabit.ft.util.ListenerUpdater.AddListener
import org.bytabit.ft.util._
import org.joda.money.{CurrencyUnit, IllegalCurrencyException, Money}
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object TradeFxService {
  def apply(system: ActorSystem) = new TradeFxService(system)
}

class TradeFxService(system: ActorSystem) extends ActorFxService(system) {

  val notaryMgrSel = system.actorSelection(s"/user/${NotaryClientManager.name}")
  lazy val notaryMgrRef = notaryMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val trades: ObservableList[TradeUIModel] = FXCollections.observableArrayList[TradeUIModel]
  val sellCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellDeliveryMethods: ObservableList[String] = FXCollections.observableArrayList[String]
  val sellBondPercent = new SimpleStringProperty()
  val sellNotaryFee = new SimpleStringProperty()

  val tradeActive: SimpleBooleanProperty = new SimpleBooleanProperty(false)

  // Private Data
  private var contracts: Seq[Contract] = Seq()
  private var sellCurrencyUnitSelected: Option[CurrencyUnit] = None
  private var sellContractSelected: Option[Contract] = None

  override def start() {
    super.start()
    sendCmd(AddListener(inbox.getRef()))
  }

  @Override
  def handler = {

    case ContractAdded(u, c, _) =>
      contracts = contracts :+ c
      updateCurrencyUnits(contracts, sellCurrencyUnits)
      updateDeliveryMethods(contracts, sellDeliveryMethods, sellCurrencyUnitSelected)

    case ContractRemoved(url, id, _) =>
      contracts = contracts.filterNot(_.id == id)
      updateCurrencyUnits(contracts, sellCurrencyUnits)
      updateCurrencyUnits(contracts, sellCurrencyUnits)
      updateDeliveryMethods(contracts, sellDeliveryMethods, sellCurrencyUnitSelected)

    case e: NotaryClientFSM.Event =>
      log.debug(s"unhandled NotaryClientFSM event: $e")

    case LocalSellerCreatedOffer(id, offer, p) =>
      addOrUpdateTradeUIModel(SELLER, CREATED, offer, p)

    case SellerCreatedOffer(id, offer, p) =>
      addOrUpdateTradeUIModel(BUYER, CREATED, offer, p)

    case BuyerTookOffer(id, _, _, _, _) =>
      updateStateTradeUIModel(TAKEN, id)

    case SellerSignedOffer(id, _, _, _, _) =>
      updateStateTradeUIModel(SIGNED, id)

    case BuyerOpenedEscrow(id, _) =>
      updateStateTradeUIModel(OPENED, id)

    case BuyerFundedEscrow(id) =>
      updateStateTradeUIModel(FUNDED, id)

    case FiatReceived(id) =>
      updateStateTradeUIModel(FIAT_RCVD, id)

    case BuyerReceivedPayout(id) =>
      updateStateTradeUIModel(BOUGHT, id)

    case SellerReceivedPayout(id) =>
      updateStateTradeUIModel(SOLD, id)

    case SellerCanceledOffer(id, p) =>
      cancelTradeUIModel(id)

    case e: TradeFSM.Event =>
      log.error(s"unhandled tradeFSM event: $e")

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  def setSelectedAddCurrencyUnit(sacu: String) = {
    sellCurrencyUnitSelected = try {
      Some(CurrencyUnit.getInstance(sacu))
    } catch {
      case ice: IllegalCurrencyException =>
        None
      case npe: NullPointerException =>
        None
    }
    updateDeliveryMethods(contracts, sellDeliveryMethods, sellCurrencyUnitSelected)
  }

  def setSelectedContract(dm: String) = {
    // TODO issue #26, allow user to rank notaries in notary client screen so UI can auto pick top ranked one
    // for now pick lowest fee contract template that matches currency and delivery method
    sellContractSelected = for {
      fcu <- sellCurrencyUnitSelected
      c <- contracts.filter(t => t.fiatCurrencyUnit == fcu && t.fiatDeliveryMethod == dm)
        .sortWith((x, y) => x.notary.btcNotaryFee.isGreaterThan(y.notary.btcNotaryFee)).headOption
    } yield c

    updateSellContract(sellContractSelected)
  }

  def updateCurrencyUnits(cts: Seq[Contract], acu: ObservableList[String]) = {
    val existingCus = sellCurrencyUnits.toList
    val foundCus = cts.map(ct => ct.fiatCurrencyUnit.toString).distinct
    val addCus = foundCus.filterNot(existingCus.contains(_))
    val rmCus = existingCus.filterNot(foundCus.contains(_))
    acu.addAll(addCus)
    acu.removeAll(rmCus)
    acu.sort(Ordering.String)
  }

  def updateDeliveryMethods(cts: Seq[Contract], adm: ObservableList[String], cuf: Option[CurrencyUnit]) = {
    val existingDms = sellDeliveryMethods.toList
    val filteredCts = cuf.map(cu => cts.filter(ct => ct.fiatCurrencyUnit.equals(cu))).getOrElse(cts)
    val foundDms = filteredCts.map(ct => ct.fiatDeliveryMethod.toString).distinct
    val addDms = foundDms.filterNot(existingDms.contains(_))
    val rmDms = existingDms.filterNot(foundDms.contains(_))
    sellDeliveryMethods.addAll(addDms)
    sellDeliveryMethods.removeAll(rmDms)
    sellDeliveryMethods.sort(Ordering.String)
  }

  def updateSellContract(contract: Option[Contract]) = {
    contract.foreach { c =>
      sellBondPercent.set(f"${c.notary.bondPercent * 100}%f")
      sellNotaryFee.set(c.notary.btcNotaryFee.toString)
    }
  }

  def calculateAddBtcAmt(fiatAmt: String, exchRate: String): String = {
    try {
      sellCurrencyUnitSelected.map { cu =>
        val fa: Money = FiatMoney(cu, fiatAmt)
        val er: BigDecimal = BigDecimal(1.0) / BigDecimal(exchRate)
        fa.convertedTo(CurrencyUnits.XBT, er.bigDecimal, Monies.roundingMode).getAmount.toString
      }.getOrElse("")
    } catch {
      case x: Exception => "ERROR"
    }
  }

  def calculateAddFiatAmt(fiatAmt: String): String = {
    try {
      sellCurrencyUnitSelected.map { cu =>
        FiatMoney(cu, fiatAmt).toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  def calculateAddBtcAmt(btcAmt: String): String = {
    try {
      sellCurrencyUnitSelected.map { cu =>
        BTCMoney(btcAmt).toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  def createSellOffer(fcu: CurrencyUnit, fiatAmount: Money, btcAmount: Money, fdm: String) = {

    sellContractSelected.foreach { c =>
      val o = Offer(UUID.randomUUID(), c, fiatAmount, btcAmount)
      sendCmd(AddSellOffer(o))
    }
  }

  def cancelSellOffer(url: URL, tradeId: UUID): Unit = {
    sendCmd(CancelSellOffer(url, tradeId))
  }

  def takeSellOffer(url: URL, tradeId: UUID): Unit = {
    // TODO issue #15, get delivery details from delivery details preferences
    sendCmd(TakeSellOffer(url, tradeId, "Test Delivery Details"))
  }

  def receiveFiat(url: URL, tradeId: UUID): Unit = {
    sendCmd(ReceiveFiat(url, tradeId))
  }

  def updateActive() = {
    tradeActive.set(trades.exists(_.active))
  }

  private def addOrUpdateTradeUIModel(role: Role, state: State, offer: SellOffer,
                                      posted: Option[DateTime] = None): Unit = {

    trades.find(t => t.getId == offer.id) match {
      case Some(t) =>
        val newTradeUI = t.copy(state = state, offer = offer, posted = posted)
        trades.set(trades.indexOf(t), newTradeUI)
      case None =>
        trades.add(TradeUIModel(role, state, offer, posted))
    }
    updateActive()
  }

  private def updateStateTradeUIModel(state: State, id: UUID) {
    trades.find(t => t.getId == id) match {
      case Some(t) =>
        val newTradeUI = t.copy(state = state)
        trades.set(trades.indexOf(t), newTradeUI)
      case None =>
        log.error(s"trade error, id not found: $id")
    }
    updateActive()
  }

  private def cancelTradeUIModel(id: UUID) = {
    trades.removeIf(new Predicate[TradeUIModel] {
      override def test(a: TradeUIModel): Boolean = {
        a.getId == id
      }
    })
    updateActive()
  }

  private def sendCmd(cmd: NotaryClientFSM.Command) = sendMsg(notaryMgrRef, cmd)

  private def sendCmd(cmd: SellFSM.Command) = sendMsg(notaryMgrRef, cmd)

  private def sendCmd(cmd: BuyFSM.Command) = sendMsg(notaryMgrRef, cmd)

  private def sendCmd(cmd: ListenerUpdater.Command) = {
    sendMsg(notaryMgrRef, cmd)
  }
}