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
import javafx.beans.property.SimpleStringProperty
import javafx.collections.{FXCollections, ObservableList}

import akka.actor.ActorSystem
import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.arbitrator.ArbitratorManager._
import org.bytabit.ft.client.{ClientManager, EventClient}
import org.bytabit.ft.fxui.model.ContractUIModel
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.trade.TradeProcess
import org.bytabit.ft.util.ListenerUpdater.AddListener
import org.bytabit.ft.util.{CurrencyUnits, ListenerUpdater, PaymentMethod}
import org.joda.money.CurrencyUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object ArbitratorManagerFxService {
  def apply(system: ActorSystem) = new ArbitratorManagerFxService(system)
}

class ArbitratorManagerFxService(actorSystem: ActorSystem) extends ActorFxService {

  override val system = actorSystem

  val clientMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val clientMgrRef = clientMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // Private Data
  private var addCurrencyUnitSelected: Option[CurrencyUnit] = None

  // UI Data

  val arbitratorId: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val bondPercent: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val arbitratorFee: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val arbitratorUrl: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val contractTemplates: ObservableList[ContractUIModel] = FXCollections.observableArrayList[ContractUIModel]

  val addCurrencyUnits: ObservableList[CurrencyUnit] = FXCollections.observableArrayList[CurrencyUnit]

  val addPaymentMethods: ObservableList[PaymentMethod] = FXCollections.observableArrayList[PaymentMethod]

  override def start() {
    super.start()
    sendCmd(AddListener(inbox.getRef()))
    addCurrencyUnits.setAll(CurrencyUnits.FIAT)
  }

  def addContractTemplate(fiatCurrencyUnit: CurrencyUnit, paymentMethod: PaymentMethod) = {
    sendCmd(AddContractTemplate(new URL(arbitratorUrl.getValue), fiatCurrencyUnit, paymentMethod))
  }

  def deleteContractTemplate(id: Sha256Hash) = {
    sendCmd(RemoveContractTemplate(new URL(arbitratorUrl.getValue), id))
  }

  @Override
  def handler = {
    case ArbitratorCreated(u, n, p) =>
      arbitratorId.set(n.id.toString)
      bondPercent.set(f"${n.bondPercent * 100}%f")
      arbitratorFee.set(n.btcArbitratorFee.toString)
      arbitratorUrl.set(n.url.toString)

    case ContractAdded(u, c, _) =>
      addUIContract(u, c.id, c.fiatCurrencyUnit, c.paymentMethod)

    case ContractRemoved(_, id, _) =>
      removeUIContract(id)

    case ec: EventClient.Event =>
    //log.info(s"Event client event")

    case te: TradeProcess.Event =>
    //log.info(s"Trade process event")

    case e =>
      log.error(s"Unexpected event: $e")
  }


  def setSelectedCurrencyUnit(selectedCurrencyUnit: CurrencyUnit) = {

    updatePaymentMethods(addPaymentMethods, selectedCurrencyUnit)
  }

  def updatePaymentMethods(paymentMethods: ObservableList[PaymentMethod], currencyUnit: CurrencyUnit) = {
    val addDms = PaymentMethod.forCurrencyUnit(currencyUnit)
    paymentMethods.setAll(addDms)
  }

  def addUIContract(arbitratorURL: URL, id: Sha256Hash, fcu: CurrencyUnit, fdm: PaymentMethod) = {
    val newContractTempUI = ContractUIModel(arbitratorURL, id, fcu, fdm)
    contractTemplates.find(t => t.getId == newContractTempUI.getId) match {
      case Some(ct) => contractTemplates.set(contractTemplates.indexOf(ct), newContractTempUI)
      case None => contractTemplates.add(newContractTempUI)
    }
  }

  def removeUIContract(id: Sha256Hash) = {
    contractTemplates.removeIf(new Predicate[ContractUIModel] {
      override def test(t: ContractUIModel): Boolean = {
        t.getId == id.toString
      }
    })
  }

  def sendCmd(cmd: ClientManager.Command) = sendMsg(clientMgrRef, cmd)

  def sendCmd(cmd: ArbitratorManager.Command) = sendMsg(clientMgrRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = {
    sendMsg(clientMgrRef, cmd)
  }
}