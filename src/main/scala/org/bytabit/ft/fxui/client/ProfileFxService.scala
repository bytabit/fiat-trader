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

import javafx.beans.property.SimpleStringProperty
import javafx.collections.{FXCollections, ObservableList}

import akka.actor.ActorSystem
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.arbitrator.ArbitratorManager.{ContractAdded, ContractRemoved}
import org.bytabit.ft.client.ClientManager._
import org.bytabit.ft.client._
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.trade.model.Contract
import org.bytabit.ft.util.PaymentMethod
import org.joda.money.CurrencyUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object ProfileFxService {
  def apply(system: ActorSystem) = new ProfileFxService(system)
}

class ProfileFxService(actorSystem: ActorSystem) extends ActorFxService {

  override val system = actorSystem

  val clientMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val clientMgrRef = clientMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  //  val arbitrators: ObservableList[ArbitratorUIModel] = FXCollections.observableArrayList[ArbitratorUIModel]
  val profileId: SimpleStringProperty = new SimpleStringProperty("")
  val addDetailsCurrencyUnits: ObservableList[CurrencyUnit] = FXCollections.observableArrayList[CurrencyUnit]
  val addDetailsPaymentMethods: ObservableList[PaymentMethod] = FXCollections.observableArrayList[PaymentMethod]

  // Private Data
  private var contracts: Seq[Contract] = Seq()
  private var addDetailsCurrencyUnitSelected: Option[CurrencyUnit] = None
  private var addDetailsPaymentMethodSelected: Option[PaymentMethod] = None
  private var addPaymentDetails: Option[String] = None

  override def start() {
    super.start()
    system.eventStream.subscribe(inbox.getRef(), classOf[ClientManager.Event])
    system.eventStream.subscribe(inbox.getRef(), classOf[ArbitratorManager.ContractAdded])
    system.eventStream.subscribe(inbox.getRef(), classOf[ArbitratorManager.ContractRemoved])
    sendCmd(FindClientProfile)
  }

  //  def addPaymentDetail(paymentDetail: PaymentDetail) =
  //    sendCmd(AddPaymentDetail(paymentDetail))
  //
  //  def removePaymentDetail(paymentDetail: PaymentDetail) =
  //    sendCmd(RemovePaymentDetail(paymentDetail))

  @Override
  def handler = {
    case FoundClientProfile(p) =>
      profileId.set(p.id.toString)

    case ContractAdded(u, c, _) =>
      contracts = contracts :+ c
      updateCurrencyUnits(contracts, addDetailsCurrencyUnits)
      updatePaymentMethods(contracts, addDetailsPaymentMethods, addDetailsCurrencyUnitSelected)

    case ContractRemoved(url, id, _) =>
      contracts = contracts.filterNot(_.id == id)
      updateCurrencyUnits(contracts, addDetailsCurrencyUnits)
      updateCurrencyUnits(contracts, addDetailsCurrencyUnits)
      updatePaymentMethods(contracts, addDetailsPaymentMethods, addDetailsCurrencyUnitSelected)

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }


  def setSelectedAddCurrencyUnit(sacu: CurrencyUnit) = {
    addDetailsCurrencyUnitSelected = Some(sacu)
    updatePaymentMethods(contracts, addDetailsPaymentMethods, addDetailsCurrencyUnitSelected)
  }

  def setSelectedAddPaymentMethod(sapm: PaymentMethod) = {
    addDetailsPaymentMethodSelected = Some(sapm)
    updateCurrencyUnits(contracts, addDetailsCurrencyUnits)
  }

  def updateCurrencyUnits(cts: Seq[Contract], acu: ObservableList[CurrencyUnit]) = {
    val existingCus = addDetailsCurrencyUnits.toList
    val foundCus = cts.map(ct => ct.fiatCurrencyUnit).distinct
    val addCus = foundCus.filterNot(existingCus.contains(_))
    val rmCus = existingCus.filterNot(foundCus.contains(_))
    acu.addAll(addCus)
    acu.removeAll(rmCus)
  }

  def updatePaymentMethods(cts: Seq[Contract], apm: ObservableList[PaymentMethod], cuf: Option[CurrencyUnit]) = {
    val existingPms = addDetailsPaymentMethods.toList
    val filteredCts = cuf.map(cu => cts.filter(ct => ct.fiatCurrencyUnit.equals(cu))).getOrElse(cts)
    val foundDms = filteredCts.map(ct => ct.paymentMethod).distinct
    val addDms = foundDms.filterNot(existingPms.contains(_))
    val rmDms = existingPms.filterNot(foundDms.contains)
    addDetailsPaymentMethods.addAll(addDms)
    addDetailsPaymentMethods.removeAll(rmDms)
  }

  private def sendCmd(cmd: ClientManager.Command) = sendMsg(clientMgrRef, cmd)
}