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
import java.util.UUID

import akka.actor.ActorSystem
import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.arbitrator.ArbitratorManager.{ArbitratorCreated, ContractAdded, ContractRemoved}
import org.bytabit.ft.client._
import org.bytabit.ft.fxui.util.TradeDataFxService
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade._
import org.bytabit.ft.trade.model.ARBITRATOR
import org.bytabit.ft.util._

import scala.concurrent.duration.FiniteDuration

object ArbitrateFxService {
  def apply(serverUrl: URL, system: ActorSystem) = new ArbitrateFxService(serverUrl, system)
}

class ArbitrateFxService(serverUrl: URL, actorSystem: ActorSystem) extends TradeDataFxService {

  override val system = actorSystem

  val arbitratorMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val arbitratorMgrRef = arbitratorMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  override def start() {
    if (Config.arbitratorEnabled) {
      super.start()
      system.eventStream.subscribe(inbox.getRef(), classOf[ArbitratorManager.Event])
      system.eventStream.subscribe(inbox.getRef(), classOf[TradeProcess.Event])
    }
  }

  @Override
  def handler = {

    // Handle client events

    case e: EventClient.ServerOnline =>
    //log.info(s"ServerOnline at URL: ${u}")

    case e: EventClient.ServerOffline =>
    //log.info(s"ServerOnline at URL: ${u}")

    // handle arbitrator events

    case ArbitratorCreated(u, a, _) =>
    //log.info(s"ArbitratorCreated at URL: ${u}")

    case ContractAdded(u, c, _) =>
    //log.info(s"ContractAdded at URL: ${u}")

    case ContractRemoved(url, id, _) =>
    //log.info(s"ContractRemoved at URL: ${u}")

    // common path

    case BtcBuyerCreatedOffer(id, btcBuyOffer, p) =>
      createOffer(ARBITRATOR, btcBuyOffer)

    case sto: BtcSellerTookOffer =>
      takeOffer(sto)

    case bso: BtcBuyerSignedOffer =>
      signOffer(bso)

    case soe: BtcSellerOpenedEscrow =>
      openEscrow(soe)

    case sfe: BtcSellerFundedEscrow =>
      fundEscrow(sfe)

    // happy path

    case fr: FiatReceived =>
      fiatReceived(fr)

    case BtcSellerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)

    case BtcBuyerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)

    // unhappy path

    case cpr: CertifyPaymentRequested =>
      reqCertPayment(cpr)

    case fsc: FiatSentCertified =>
      certifyFiatSent(fsc)

    case fnc: FiatNotSentCertified =>
      certifyFiatNotSent(fnc)

    case bf: BtcBuyerFunded =>
      fundBtcBuyer(bf)

    case sr: BtcSellerRefunded =>
      refundBtcSeller(sr)

    // cancel path

    case BtcBuyerCanceledOffer(id, p) =>
      removeTradeUIModel(id)

    // errors

    case e: EventClient.Event =>
      log.error(s"unhandled EventClient event: $e")

    case e: ArbitratorManager.Event =>
      log.error(s"unhandled ArbitratorManager event: $e")

    case e: TradeProcess.Event =>
      log.error(s"unhandled TradeProcess event: $e")

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  def certifyFiatSent(url: URL, tradeId: UUID): Unit = {
    sendCmd(ArbitrateProcess.CertifyFiatSent(url, tradeId))
  }

  def certifyFiatNotSent(url: URL, tradeId: UUID): Unit = {
    sendCmd(ArbitrateProcess.CertifyFiatNotSent(url, tradeId))
  }

  def sendCmd(cmd: ArbitrateProcess.Command) = sendMsg(arbitratorMgrRef, cmd)
}