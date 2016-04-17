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
import java.util.function.Predicate
import javafx.collections.{FXCollections, ObservableList}

import akka.actor.ActorSystem
import org.bytabit.ft.client.ClientManager.{Start, _}
import org.bytabit.ft.client.ClientFSM._
import org.bytabit.ft.client._
import org.bytabit.ft.fxui.model.ArbitratorUIModel
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.model.Contract
import org.bytabit.ft.util.{BTCMoney, FiatMoney, ListenerUpdater, Monies}
import org.bytabit.ft.wallet.model.Arbitrator
import org.joda.money.{CurrencyUnit, IllegalCurrencyException, Money}

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object ArbitratorClientFxService {
  def apply(system: ActorSystem) = new ArbitratorClientFxService(system)
}

class ArbitratorClientFxService(actorSystem: ActorSystem) extends ActorFxService {

  override val system = actorSystem

  val arbitratorClientMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val arbitratorClientMgrRef = arbitratorClientMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val arbitrators: ObservableList[ArbitratorUIModel] = FXCollections.observableArrayList[ArbitratorUIModel]
  val addCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val addDeliveryMethods: ObservableList[String] = FXCollections.observableArrayList[String]

  // Private Data
  private var contractTemplates: Seq[Contract] = Seq()
  private var addCurrencyUnitSelected: Option[CurrencyUnit] = None

  override def start() {
    super.start()
    sendCmd(Start)
  }

  def addArbitrator(url: URL) =
    sendCmd(AddClient(url))

  def removeArbitrator(url: URL) =
    sendCmd(RemoveClient(url))

  @Override
  def handler = {
    case ClientAdded(u) =>
      updateArbitratorUIModel(ADDED, u, None)

    case ArbitratorCreated(u, n, _) =>
      updateArbitratorUIModel(ONLINE, u, Some(n))

    case ClientRemoved(u) =>
      removeArbitratorUIModel(u)

    case ArbitratorOnline(u) =>
      updateArbitratorUIModel(ONLINE, u, None)

    case ArbitratorOffline(u) =>
      updateArbitratorUIModel(OFFLINE, u, None)

    case e: ClientFSM.Event =>
      log.debug(s"unhandled ArbitratorFSM event: $e")

    case e: TradeFSM.Event =>
      log.debug(s"unhandled tradeFSM event: $e")

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  private def updateArbitratorUIModel(state: ClientFSM.State, url: URL, arbitrator: Option[Arbitrator]) = {
    arbitrators.find(n => n.getUrl == url.toString) match {
      case Some(n) if arbitrator.isDefined =>
        val newArbitratorUI = ArbitratorUIModel(state, url, arbitrator)
        arbitrators.set(arbitrators.indexOf(n), newArbitratorUI)
      case Some(n) if arbitrator.isEmpty =>
        val newArbitratorUI = n.copy(status = state, url = url)
        arbitrators.set(arbitrators.indexOf(n), newArbitratorUI)
      case None =>
        val newArbitratorUI = ArbitratorUIModel(state, url, arbitrator)
        arbitrators.add(newArbitratorUI)
    }
  }

  private def removeArbitratorUIModel(u: URL) = {
    arbitrators.removeIf(new Predicate[ArbitratorUIModel] {
      override def test(a: ArbitratorUIModel): Boolean = {
        a.getUrl == u.toString
      }
    })
  }

  def setSelectedAddCurrencyUnit(sacu: String) = {
    addCurrencyUnitSelected = try {
      Some(CurrencyUnit.getInstance(sacu))
    } catch {
      case ice: IllegalCurrencyException =>
        None
      case npe: NullPointerException =>
        None
    }
    updateDeliveryMethods(contractTemplates, addDeliveryMethods, addCurrencyUnitSelected)
  }

  def updateCurrencyUnits(cts: Seq[Contract], acu: ObservableList[String]) = {
    val existingCus = addCurrencyUnits.toList
    val foundCus = cts.map(ct => ct.fiatCurrencyUnit.toString).distinct
    val addCus = foundCus.filterNot(existingCus.contains(_))
    val rmCus = existingCus.filterNot(foundCus.contains(_))
    acu.addAll(addCus)
    acu.removeAll(rmCus)
    acu.sort(Ordering.String)
  }

  def updateDeliveryMethods(cts: Seq[Contract], adm: ObservableList[String], cuf: Option[CurrencyUnit]) = {
    val existingDms = addDeliveryMethods.toList
    val filteredCts = cuf.map(cu => cts.filter(ct => ct.fiatCurrencyUnit.equals(cu))).getOrElse(cts)
    val foundDms = filteredCts.map(ct => ct.fiatDeliveryMethod.toString).distinct
    val addDms = foundDms.filterNot(existingDms.contains(_))
    val rmDms = existingDms.filterNot(foundDms.contains(_))
    addDeliveryMethods.addAll(addDms)
    addDeliveryMethods.removeAll(rmDms)
    addDeliveryMethods.sort(Ordering.String)
  }

  def calculateAddExchRate(fiatAmt: String, btcAmt: String): String = {
    try {
      addCurrencyUnitSelected.map { cu =>
        val fa: Money = FiatMoney(cu, fiatAmt)
        val ba: Money = BTCMoney(btcAmt)
        fa.dividedBy(ba.getAmount, Monies.roundingMode).getAmount.toString
      }.getOrElse("")
    } catch {
      case x: Exception => "ERROR"
    }
  }

  def calculateAddFiatAmt(fiatAmt: String): String = {
    try {
      addCurrencyUnitSelected.map { cu =>
        FiatMoney(cu, fiatAmt).toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  def calculateAddBtcAmt(btcAmt: String): String = {
    try {
      addCurrencyUnitSelected.map { cu =>
        BTCMoney(btcAmt).toString
      }.getOrElse("")
    } catch {
      case x: Exception => ""
    }
  }

  private def sendCmd(cmd: ClientManager.Command) = sendMsg(arbitratorClientMgrRef, cmd)

  private def sendCmd(cmd: ListenerUpdater.Command) = sendMsg(arbitratorClientMgrRef, cmd)
}