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

package org.bytabit.ft.fxui.arbitrator

import java.net.URL
import java.util.function.Predicate
import javafx.collections.{FXCollections, ObservableList}

import akka.actor.ActorSystem
import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.arbitrator.ArbitratorManager._
import org.bytabit.ft.client.{ClientManager, EventClient}
import org.bytabit.ft.fxui.util.ActorFxService
import org.bytabit.ft.trade.TradeProcess
import org.bytabit.ft.util.{CurrencyUnits, PaymentMethod}
import org.bytabit.ft.wallet.model.Arbitrator
import org.joda.money.CurrencyUnit

import scala.collection.JavaConversions._
import scala.collection.mutable
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

  //val contractTemplates: ObservableList[ContractUIModel] = FXCollections.observableArrayList[ContractUIModel]
  val contractTemplates = mutable.HashMap[URL, ObservableList[ContractUIModel]]()

  val addCurrencyUnits: ObservableList[CurrencyUnit] = FXCollections.observableArrayList[CurrencyUnit]

  val addPaymentMethods: ObservableList[PaymentMethod] = FXCollections.observableArrayList[PaymentMethod]

  override def start() {
    super.start()
    system.eventStream.subscribe(inbox.getRef(), classOf[ArbitratorManager.Event])
    addCurrencyUnits.setAll(CurrencyUnits.FIAT)
  }

  def addContractTemplate(arbitrator: Arbitrator, fiatCurrencyUnit: CurrencyUnit, paymentMethod: PaymentMethod) = {
    sendCmd(AddContractTemplate(arbitrator.url, fiatCurrencyUnit, paymentMethod))
  }

  def deleteContractTemplate(arbitrator: Arbitrator, id: Sha256Hash) = {
    sendCmd(RemoveContractTemplate(arbitrator.url, id))
  }

  @Override
  def handler = {
    case ArbitratorCreated(u, a, p) =>
      contractTemplates.put(u, FXCollections.observableArrayList[ContractUIModel]())

    case ContractAdded(u, c, _) =>
      addUIContract(u, c.id, c.fiatCurrencyUnit, c.paymentMethod)

    case ContractRemoved(u, id, _) =>
      removeUIContract(u, id)

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
    contractTemplates.get(arbitratorURL).map { ol =>
      ol.find(t => t.getId == newContractTempUI.getId) match {
        case Some(ct) => ol.set(ol.indexOf(ct), newContractTempUI)
        case None => ol.add(newContractTempUI)
      }
    }
  }

  def removeUIContract(arbitratorURL: URL, id: Sha256Hash) = {
    contractTemplates.get(arbitratorURL).map { ol =>
      ol.removeIf(new Predicate[ContractUIModel] {
        override def test(t: ContractUIModel): Boolean = {
          t.arbitratorUrl == arbitratorURL && t.getId == id.toString
        }
      })
    }
  }

  def sendCmd(cmd: ClientManager.Command) = sendMsg(clientMgrRef, cmd)

  def sendCmd(cmd: ArbitratorManager.Command) = sendMsg(clientMgrRef, cmd)
}