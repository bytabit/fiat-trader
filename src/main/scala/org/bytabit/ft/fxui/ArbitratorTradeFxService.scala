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

import akka.actor.ActorSystem
import org.bytabit.ft.client._
import org.bytabit.ft.fxui.util.TradeFxService
import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade._
import org.bytabit.ft.trade.model.ARBITRATOR
import org.bytabit.ft.util.ListenerUpdater.AddListener
import org.bytabit.ft.util._

import scala.concurrent.duration.FiniteDuration

object ArbitratorTradeFxService {
  def apply(serverUrl: URL, system: ActorSystem) = new ArbitratorTradeFxService(serverUrl, system)
}

class ArbitratorTradeFxService(serverUrl: URL, actorSystem: ActorSystem) extends TradeFxService {

  override val system = actorSystem

  val arbitratorMgrSel = system.actorSelection(s"/user/${ClientManager.name}")
  lazy val arbitratorMgrRef = arbitratorMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  override def start() {
    if (Config.arbitratorEnabled) {
      super.start()
      sendCmd(AddListener(inbox.getRef()))
    }
  }

  @Override
  def handler = {

    // common path

    case SellerCreatedOffer(id, sellOffer, p) =>
      createOffer(ARBITRATOR, sellOffer)

    case bto: BuyerTookOffer =>
      takeOffer(bto)

    case sso: SellerSignedOffer =>
      signOffer(sso)

    case boe: BuyerOpenedEscrow =>
      openEscrow(boe)

    case bfe: BuyerFundedEscrow =>
      fundEscrow(bfe)

    // happy path

    case fr: FiatReceived =>
      fiatReceived(fr)

    case BuyerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)

    case SellerReceivedPayout(id, txHash, txUpdated) =>
      payoutEscrow(id, txHash, txUpdated)

    // unhappy path

    case cdr: CertifyDeliveryRequested =>
      reqCertDelivery(cdr)

    case fsc: FiatSentCertified =>
      certifyFiatSent(fsc)

    case fnc: FiatNotSentCertified =>
      certifyFiatNotSent(fnc)

    case sf: SellerFunded =>
      fundSeller(sf)

    case rb: BuyerRefunded =>
      refundBuyer(rb)

    // cancel path

    case SellerCanceledOffer(id, p) =>
      cancelTradeUIModel(id)

    // errors

    case e: EventClient.Event =>
      log.debug(s"unhandled ArbitratorFSM event: $e")

    case e: TradeProcess.Event =>
      log.error(s"unhandled TradeFSM event: $e")

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

  def sendCmd(cmd: ListenerUpdater.Command) = {
    sendMsg(arbitratorMgrRef, cmd)
  }
}