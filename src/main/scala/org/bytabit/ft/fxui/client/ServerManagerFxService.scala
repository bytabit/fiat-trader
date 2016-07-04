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
package org.bytabit.ft.fxui.client

import java.net.URL
import java.util.function.Predicate
import javafx.collections.{FXCollections, ObservableList}

import akka.actor.ActorSystem
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.arbitrator.ArbitratorManager.ArbitratorCreated
import org.bytabit.ft.client.ClientManager._
import org.bytabit.ft.client.EventClient.{ADDED, _}
import org.bytabit.ft.client._
import org.bytabit.ft.fxui.arbitrator.ArbitratorUIModel
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.trade.model.Contract
import org.bytabit.ft.util.{BTCMoney, FiatMoney, Monies}
import org.bytabit.ft.wallet.model.Arbitrator
import org.joda.money.{CurrencyUnit, IllegalCurrencyException, Money}

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object ServerManagerFxService {
  def apply(system: ActorSystem) = new ServerManagerFxService(system)
}

class ServerManagerFxService(actorSystem: ActorSystem) extends ActorFxService {

  override val system = actorSystem

  val clientMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val clientMgrRef = clientMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val arbitrators: ObservableList[ArbitratorUIModel] = FXCollections.observableArrayList[ArbitratorUIModel]
  val addCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val addPaymentMethods: ObservableList[String] = FXCollections.observableArrayList[String]

  // Private Data
  private var contractTemplates: Seq[Contract] = Seq()
  private var addCurrencyUnitSelected: Option[CurrencyUnit] = None

  override def start() {
    super.start()
    system.eventStream.subscribe(inbox.getRef(), classOf[ClientManager.Event])
    system.eventStream.subscribe(inbox.getRef(), classOf[ArbitratorManager.ArbitratorCreated])
    system.eventStream.subscribe(inbox.getRef(), classOf[EventClient.ServerOnline])
    system.eventStream.subscribe(inbox.getRef(), classOf[EventClient.ServerOffline])
    sendCmd(FindServers)
  }

  def addArbitrator(url: URL) =
    sendCmd(AddServer(url))

  def removeArbitrator(url: URL) =
    sendCmd(RemoveServer(url))

  @Override
  def handler = {
    case FoundServers(us) =>
      us.foreach { u =>
        updateArbitratorUIModel(ADDED, u, None)
      }

    case ClientCreated(p) =>
    // do nothing for now

    case ServerAdded(u) =>
      updateArbitratorUIModel(ADDED, u, None)

    case ArbitratorCreated(u, n, _) =>
      updateArbitratorUIModel(ONLINE, u, Some(n))

    case ServerRemoved(u) =>
      removeArbitratorUIModel(u)

    case ServerOnline(u) =>
      updateArbitratorUIModel(ONLINE, u, None)

    case ServerOffline(u) =>
      updateArbitratorUIModel(OFFLINE, u, None)

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  private def updateArbitratorUIModel(state: EventClient.State, url: URL, arbitrator: Option[Arbitrator]) = {
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
    updatePaymentMethods(contractTemplates, addPaymentMethods, addCurrencyUnitSelected)
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

  def updatePaymentMethods(cts: Seq[Contract], adm: ObservableList[String], cuf: Option[CurrencyUnit]) = {
    val existingDms = addPaymentMethods.toList
    val filteredCts = cuf.map(cu => cts.filter(ct => ct.fiatCurrencyUnit.equals(cu))).getOrElse(cts)
    val foundDms = filteredCts.map(ct => ct.paymentMethod.toString).distinct
    val addDms = foundDms.filterNot(existingDms.contains(_))
    val rmDms = existingDms.filterNot(foundDms.contains(_))
    addPaymentMethods.addAll(addDms)
    addPaymentMethods.removeAll(rmDms)
    addPaymentMethods.sort(Ordering.String)
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

  private def sendCmd(cmd: ClientManager.Command) = sendMsg(clientMgrRef, cmd)
}