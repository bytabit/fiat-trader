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

import java.net.URL
import java.util.function.Predicate
import javafx.collections.{FXCollections, ObservableList}

import akka.actor.ActorSystem
import com.bytabit.ft.fxui.model.NotaryUIModel
import com.bytabit.ft.fxui.util.ActorFxService
import com.bytabit.ft.notary.NotaryClientFSM._
import com.bytabit.ft.notary.NotaryClientManager.{Start, _}
import com.bytabit.ft.notary._
import com.bytabit.ft.trade.TradeFSM
import com.bytabit.ft.trade.model._
import com.bytabit.ft.util._
import com.bytabit.ft.wallet.model.Notary
import org.joda.money.{CurrencyUnit, IllegalCurrencyException, Money}

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object NotaryClientFxService {
  def apply(system: ActorSystem) = new NotaryClientFxService(system)
}

class NotaryClientFxService(system: ActorSystem) extends ActorFxService(system) {

  val notaryClientMgrSel = system.actorSelection(s"/user/${NotaryClientManager.name}")
  lazy val notaryClientMgrRef = notaryClientMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val notaries: ObservableList[NotaryUIModel] = FXCollections.observableArrayList[NotaryUIModel]
  val addCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]
  val addDeliveryMethods: ObservableList[String] = FXCollections.observableArrayList[String]

  // Private Data
  private var contractTemplates: Seq[Contract] = Seq()
  private var addCurrencyUnitSelected: Option[CurrencyUnit] = None

  override def start() {
    super.start()
    sendCmd(Start)
  }

  def addNotary(url: URL) =
    sendCmd(AddNotary(url))

  def removeNotary(url: URL) =
    sendCmd(RemoveNotary(url))

  @Override
  def handler = {
    case NotaryAdded(u) =>
      updateNotaryUIModel(ADDED, u, None)

    case NotaryCreated(u, n, _) =>
      updateNotaryUIModel(ONLINE, u, Some(n))

    case NotaryRemoved(u) =>
      removeNotaryUIModel(u)

    case NotaryOnline(u) =>
      updateNotaryUIModel(ONLINE, u, None)

    case NotaryOffline(u) =>
      updateNotaryUIModel(OFFLINE, u, None)

    case e: NotaryClientFSM.Event =>
      log.debug(s"unhandled NotaryClientFSM event: $e")

    case e: TradeFSM.Event =>
      log.debug(s"unhandled tradeFSM event: $e")

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  private def updateNotaryUIModel(state: NotaryClientFSM.State, url: URL, notary: Option[Notary]) = {
    notaries.find(n => n.getUrl == url.toString) match {
      case Some(n) if notary.isDefined =>
        val newNotaryUI = NotaryUIModel(state, url, notary)
        notaries.set(notaries.indexOf(n), newNotaryUI)
      case Some(n) if notary.isEmpty =>
        val newNotaryUI = n.copy(status = state, url = url)
        notaries.set(notaries.indexOf(n), newNotaryUI)
      case None =>
        val newNotaryUI = NotaryUIModel(state, url, notary)
        notaries.add(newNotaryUI)
    }
  }

  private def removeNotaryUIModel(u: URL) = {
    notaries.removeIf(new Predicate[NotaryUIModel] {
      override def test(a: NotaryUIModel): Boolean = {
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

  private def sendCmd(cmd: NotaryClientManager.Command) = sendMsg(notaryClientMgrRef, cmd)

  private def sendCmd(cmd: ListenerUpdater.Command) = sendMsg(notaryClientMgrRef, cmd)
}