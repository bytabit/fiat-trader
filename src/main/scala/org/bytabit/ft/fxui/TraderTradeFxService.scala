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
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ObservableList

import akka.actor.ActorSystem
import org.bytabit.ft.client.ClientFSM.{ContractAdded, ContractRemoved}
import org.bytabit.ft.client._
import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, SELLER}
import org.bytabit.ft.fxui.util.TradeFxService
import org.bytabit.ft.trade.BuyProcess.{ReceiveFiat, TakeSellOffer}
import org.bytabit.ft.trade.SellProcess.{AddSellOffer, CancelSellOffer, SendFiat}
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade._
import org.bytabit.ft.trade.model.{Contract, Offer}
import org.bytabit.ft.util.ListenerUpdater.AddListener
import org.bytabit.ft.util._
import org.joda.money.{CurrencyUnit, Money}

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

object TraderTradeFxService {
  def apply(system: ActorSystem) = new TraderTradeFxService(system)
}

class TraderTradeFxService(actorSystem: ActorSystem) extends TradeFxService {

  override val system = actorSystem

  val arbitratorMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val arbitratorMgrRef = arbitratorMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  // TODO FT-99: disable buy buttons if current trade is uncommitted
  val tradeUncommitted: SimpleBooleanProperty = new SimpleBooleanProperty(false)

  // Private Data
  private var contracts: Seq[Contract] = Seq()
  private var sellCurrencyUnitSelected: Option[CurrencyUnit] = None
  private var sellContractSelected: Option[Contract] = None

  override def start() {
    if (!Config.arbitratorEnabled) {
      super.start()
      sendCmd(AddListener(inbox.getRef()))
    }
  }

  @Override
  def handler = {

    // Handle Arbitrator Events

    case ContractAdded(u, c, _) =>
      contracts = contracts :+ c
      updateCurrencyUnits(contracts, sellCurrencyUnits)
      updateDeliveryMethods(contracts, sellDeliveryMethods, sellCurrencyUnitSelected)

    case ContractRemoved(url, id, _) =>
      contracts = contracts.filterNot(_.id == id)
      updateCurrencyUnits(contracts, sellCurrencyUnits)
      updateCurrencyUnits(contracts, sellCurrencyUnits)
      updateDeliveryMethods(contracts, sellDeliveryMethods, sellCurrencyUnitSelected)

    case e: ClientFSM.Event =>
      log.debug(s"Unhandled ArbitratorFSM event: $e")

    // Handle Trade Events

    // common path

    case LocalSellerCreatedOffer(id, sellOffer, p) =>
      createOffer(SELLER, sellOffer)
      updateUncommitted()

    case SellerCreatedOffer(id, sellOffer, p) =>
      createOffer(BUYER, sellOffer)
      updateUncommitted()

    case bto: BuyerTookOffer =>
      takeOffer(bto)
      updateUncommitted()

    case sso: SellerSignedOffer =>
      signOffer(sso)
      updateUncommitted()

    case boe: BuyerOpenedEscrow =>
      openEscrow(boe)
      updateUncommitted()

    case bfe: BuyerFundedEscrow =>
      fundEscrow(bfe)
      updateUncommitted()

    // happy path

    case fs: FiatSent =>
      fiatSent(fs)
      updateUncommitted()

    case fr: FiatReceived =>
      fiatReceived(fr)
      updateUncommitted()

    case BuyerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)
      updateUncommitted()

    case SellerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)
      updateUncommitted()

    // unhappy path

    case cdr: CertifyDeliveryRequested =>
      reqCertDelivery(cdr)
      updateUncommitted()

    case fsc: FiatSentCertified =>
      certifyFiatSent(fsc)
      updateUncommitted()

    case fnc: FiatNotSentCertified =>
      certifyFiatNotSent(fnc)
      updateUncommitted()

    case sf: SellerFunded =>
      fundSeller(sf)
      updateUncommitted()

    case rb: BuyerRefunded =>
      refundBuyer(rb)
      updateUncommitted()

    // cancel path

    case SellerCanceledOffer(id, p) =>
      cancelTradeUIModel(id)
      updateUncommitted()

    // errors

    case e: TradeFSM.Event =>
      log.error(s"Unhandled TradeFSM event: $e")

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  def setSelectedAddCurrencyUnit(sacu: CurrencyUnit) = {
    sellCurrencyUnitSelected = Some(sacu)
    updateDeliveryMethods(contracts, sellDeliveryMethods, sellCurrencyUnitSelected)
  }

  def setSelectedContract(dm: FiatDeliveryMethod) = {
    // TODO FT-21: allow user to rank arbitrators in arbitrator client screen so UI can auto pick top ranked one
    // for now pick lowest fee contract template that matches currency and delivery method
    sellContractSelected = for {
      fcu <- sellCurrencyUnitSelected
      c <- contracts.filter(t => t.fiatCurrencyUnit == fcu && t.fiatDeliveryMethod == dm)
        .sortWith((x, y) => x.arbitrator.btcArbitratorFee.isGreaterThan(y.arbitrator.btcArbitratorFee)).headOption
    } yield c

    updateSellContract(sellContractSelected)
  }

  def updateCurrencyUnits(cts: Seq[Contract], acu: ObservableList[CurrencyUnit]) = {
    val existingCus = sellCurrencyUnits.toList
    val foundCus = cts.map(ct => ct.fiatCurrencyUnit).distinct
    val addCus = foundCus.filterNot(existingCus.contains(_))
    val rmCus = existingCus.filterNot(foundCus.contains(_))
    acu.addAll(addCus)
    acu.removeAll(rmCus)
    //acu.sort(Ordering.String)
  }

  def updateDeliveryMethods(cts: Seq[Contract], adm: ObservableList[FiatDeliveryMethod], cuf: Option[CurrencyUnit]) = {
    val existingDms = sellDeliveryMethods.toList
    val filteredCts = cuf.map(cu => cts.filter(ct => ct.fiatCurrencyUnit.equals(cu))).getOrElse(cts)
    val foundDms = filteredCts.map(ct => ct.fiatDeliveryMethod).distinct
    val addDms = foundDms.filterNot(existingDms.contains(_))
    val rmDms = existingDms.filterNot(foundDms.contains(_))
    sellDeliveryMethods.addAll(addDms)
    sellDeliveryMethods.removeAll(rmDms)
    //sellDeliveryMethods.sort(Ordering)
  }

  def updateSellContract(contract: Option[Contract]) = {
    contract.foreach { c =>
      sellBondPercent.set(f"${c.arbitrator.bondPercent * 100}%f")
      sellArbitratorFee.set(c.arbitrator.btcArbitratorFee.toString)
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

  def createSellOffer(fcu: CurrencyUnit, fiatAmount: Money, btcAmount: Money, fdm: FiatDeliveryMethod) = {

    sellContractSelected.foreach { c =>
      val o = Offer(UUID.randomUUID(), c, fiatAmount, btcAmount)
      sendCmd(AddSellOffer(o))
    }
  }

  def cancelSellOffer(url: URL, tradeId: UUID): Unit = {
    sendCmd(CancelSellOffer(url, tradeId))
  }

  def takeSellOffer(url: URL, tradeId: UUID): Unit = {
    // TODO FT-10: get delivery details from delivery details preferences
    sendCmd(TakeSellOffer(url, tradeId, "Swish: +467334557"))
  }

  def receiveFiat(url: URL, tradeId: UUID): Unit = {
    sendCmd(ReceiveFiat(url, tradeId))
  }

  def sendFiat(url: URL, tradeId: UUID): Unit = {
    sendCmd(SendFiat(url, tradeId))
  }

  // TODO FT-91: collect evidence
  def sellerReqCertDelivery(url: URL, tradeId: UUID): Unit = {
    sendCmd(SellProcess.RequestCertifyDelivery(url, tradeId))
  }

  // TODO FT-91: collect evidence
  def buyerReqCertDelivery(url: URL, tradeId: UUID): Unit = {
    sendCmd(BuyProcess.RequestCertifyDelivery(url, tradeId))
  }

  def updateUncommitted() = {
    tradeUncommitted.set(trades.exists(_.uncommitted))
  }

  def sendCmd(cmd: SellProcess.Command) = sendMsg(arbitratorMgrRef, cmd)

  def sendCmd(cmd: BuyProcess.Command) = sendMsg(arbitratorMgrRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = {
    sendMsg(arbitratorMgrRef, cmd)
  }
}