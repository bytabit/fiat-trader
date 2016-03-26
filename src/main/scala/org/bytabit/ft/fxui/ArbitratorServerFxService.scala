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
import org.bytabit.ft.arbitrator.ArbitratorFSM.{ArbitratorCreated, ContractAdded, ContractRemoved}
import org.bytabit.ft.arbitrator.server.ArbitratorServerManager
import org.bytabit.ft.arbitrator.server.ArbitratorServerManager.{AddContractTemplate, RemoveContractTemplate, Start}
import org.bytabit.ft.fxui.model.ContractUIModel
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.util.{CurrencyUnits, ListenerUpdater}
import org.joda.money.CurrencyUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object ArbitratorServerFxService {
  def apply(system: ActorSystem) = new ArbitratorServerFxService(system)
}

class ArbitratorServerFxService(actorSystem: ActorSystem) extends ActorFxService {

  override val system = actorSystem

  val arbitratorServerMgrSel = system.actorSelection(s"/user/${ArbitratorServerManager.name}")
  lazy val arbitratorServerMgrRef = arbitratorServerMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val arbitratorId: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val bondPercent: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val arbitratorFee: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val arbitratorUrl: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val contractTemplates: ObservableList[ContractUIModel] = FXCollections.observableArrayList[ContractUIModel]

  val addCurrencyUnits: ObservableList[String] = FXCollections.observableArrayList[String]

  override def start() {
    super.start()

    addCurrencyUnits.setAll(CurrencyUnits.FIAT.map(_.toString))

    //sendCmd(AddListener(inbox.getRef()))
    sendCmd(Start)
  }

  def addContractTemplate(fiatCurrencyUnit: CurrencyUnit, fiatDeliveryMethod: String) = {
    sendCmd(AddContractTemplate(fiatCurrencyUnit, fiatDeliveryMethod))
  }

  def deleteContractTemplate(id: Sha256Hash) = {
    sendCmd(RemoveContractTemplate(id))
  }

  @Override
  def handler = {
    case ArbitratorCreated(u, n, p) =>
      arbitratorId.set(n.id.toString)
      bondPercent.set(f"${n.bondPercent * 100}%f")
      arbitratorFee.set(n.btcArbitratorFee.toString)
      arbitratorUrl.set(n.url.toString)

    case ContractAdded(u, c, _) =>
      addUIContract(u, c.id, c.fiatCurrencyUnit, c.fiatDeliveryMethod)

    case ContractRemoved(_, id, _) =>
      removeUIContract(id)

    case e =>
      log.error(s"Unexpected event: $e")
  }

  def addUIContract(arbitratorURL: URL, id: Sha256Hash, fcu: CurrencyUnit, fdm: String) = {
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

  def sendCmd(cmd: ArbitratorServerManager.Command) = sendMsg(arbitratorServerMgrRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = sendMsg(arbitratorServerMgrRef, cmd)
}