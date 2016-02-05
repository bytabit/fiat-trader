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
import org.bytabit.ft.fxui.model.TradeUIModel.NOTARY
import org.bytabit.ft.fxui.util.TradeFxService
import org.bytabit.ft.notary._
import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade._
import org.bytabit.ft.util.ListenerUpdater.AddListener
import org.bytabit.ft.util._

import scala.concurrent.duration.FiniteDuration

object NotaryTradeFxService {
  def apply(serverUrl: URL, system: ActorSystem) = new NotaryTradeFxService(serverUrl, system)
}

class NotaryTradeFxService(serverUrl: URL, actorSystem: ActorSystem) extends TradeFxService {

  override val system = actorSystem

  val notaryMgrSel = system.actorSelection(s"/user/${NotaryClientManager.name}")
  lazy val notaryMgrRef = notaryMgrSel.resolveOne(FiniteDuration(5, "seconds"))

  override def start() {
    if (Config.serverEnabled) {
      super.start()
      sendCmd(AddListener(inbox.getRef()))
    }
  }

  @Override
  def handler = {

    case SellerCreatedOffer(id, offer, p) =>
      addOrUpdateTradeUIModel(NOTARY, CREATED, offer, p)

    case BuyerTookOffer(id, _, _, _, _) =>
      updateStateTradeUIModel(TAKEN, id)

    case SellerSignedOffer(id, _, _, _, _) =>
      updateStateTradeUIModel(SIGNED, id)

    case BuyerOpenedEscrow(id, _) =>
      updateStateTradeUIModel(OPENED, id)

    case BuyerFundedEscrow(id) =>
      updateStateTradeUIModel(FUNDED, id)

    case CertifyDeliveryRequested(id, _, _) =>
      updateStateTradeUIModel(CERT_DELIVERY_REQD, id)

    case FiatSentCertified(id, _, _) =>
      updateStateTradeUIModel(FIAT_SENT_CERTD, id)

    case FiatNotSentCertified(id, _, _) =>
      updateStateTradeUIModel(FIAT_NOT_SENT_CERTD, id)

    case FiatReceived(id) =>
      updateStateTradeUIModel(FIAT_RCVD, id)

    case BuyerReceivedPayout(id) =>
      updateStateTradeUIModel(TRADED, id)

    case SellerReceivedPayout(id) =>
      updateStateTradeUIModel(TRADED, id)

    case SellerCanceledOffer(id, p) =>
      cancelTradeUIModel(id)

    case SellerFunded(id) =>
      updateStateTradeUIModel(SELLER_FUNDED, id)

    case BuyerRefunded(id) =>
      updateStateTradeUIModel(BUYER_REFUNDED, id)

    case e: NotaryFSM.Event =>
      log.debug(s"unhandled NotaryFSM event: $e")

    case e: TradeFSM.Event =>
      log.error(s"unhandled TradeFSM event: $e")

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  def certifyFiatSent(url:URL, tradeId:UUID): Unit = {
    sendCmd(NotarizeFSM.CertifyFiatSent(url,tradeId))
  }

  def certifyFiatNotSent(url:URL, tradeId:UUID): Unit = {
    sendCmd(NotarizeFSM.CertifyFiatNotSent(url,tradeId))
  }

  def sendCmd(cmd: NotarizeFSM.Command) = sendMsg(notaryMgrRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = {
    sendMsg(notaryMgrRef, cmd)
  }
}