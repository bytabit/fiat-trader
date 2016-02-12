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
import org.bytabit.ft.fxui.model.ContractUIModel
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.notary.NotaryFSM.{ContractAdded, ContractRemoved, NotaryCreated}
import org.bytabit.ft.notary.server.NotaryServerManager
import org.bytabit.ft.notary.server.NotaryServerManager.{AddContractTemplate, RemoveContractTemplate, Start}
import org.bytabit.ft.util.{CurrencyUnits, ListenerUpdater}
import org.joda.money.CurrencyUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object NotaryServerFxService {
  def apply(system: ActorSystem) = new NotaryServerFxService(system)
}

class NotaryServerFxService(actorSystem: ActorSystem) extends ActorFxService {

  override val system = actorSystem

  val notaryServerMgrSel = system.actorSelection(s"/user/${NotaryServerManager.name}")
  lazy val notaryServerMgrRef = notaryServerMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // UI Data

  val notaryId: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val bondPercent: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val notaryFee: SimpleStringProperty = new SimpleStringProperty("Unknown")

  val notaryUrl: SimpleStringProperty = new SimpleStringProperty("Unknown")

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
    case NotaryCreated(u, n, p) =>
      notaryId.set(n.id.toString)
      bondPercent.set(f"${n.bondPercent * 100}%f")
      notaryFee.set(n.btcNotaryFee.toString)
      notaryUrl.set(n.url.toString)

    case ContractAdded(u, c, _) =>
      addUIContract(u, c.id, c.fiatCurrencyUnit, c.fiatDeliveryMethod)

    case ContractRemoved(_, id, _) =>
      removeUIContract(id)

    case e =>
      log.error(s"Unexpected event: $e")
  }

  def addUIContract(notaryURL: URL, id: Sha256Hash, fcu: CurrencyUnit, fdm: String) = {
    val newContractTempUI = ContractUIModel(notaryURL, id, fcu, fdm)
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

  def sendCmd(cmd: NotaryServerManager.Command) = sendMsg(notaryServerMgrRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = sendMsg(notaryServerMgrRef, cmd)
}