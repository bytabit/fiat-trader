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

  val notaryClientSel = system.actorSelection(s"/user/${NotaryFSM.name(serverUrl)}")
  lazy val notaryClientRef = notaryClientSel.resolveOne(FiniteDuration(5, "seconds"))

  override def start() {
    super.start()
    sendCmd(AddListener(inbox.getRef()))
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

    case FiatReceived(id) =>
      updateStateTradeUIModel(FIAT_RCVD, id)

    case BuyerReceivedPayout(id) =>
      updateStateTradeUIModel(TRADED, id)

    case SellerReceivedPayout(id) =>
      updateStateTradeUIModel(TRADED, id)

    case SellerCanceledOffer(id, p) =>
      cancelTradeUIModel(id)

    case e: TradeFSM.Event =>
      log.error(s"unhandled tradeFSM event: $e")

    // TODO cases to notarize trades

    case u =>
      log.error(s"Unexpected message: ${u.toString}")
  }

  // TODO functions to notarize trades

  def sendCmd(cmd: NotaryClientFSM.Command) = sendMsg(notaryClientRef, cmd)

  def sendCmd(cmd: ListenerUpdater.Command) = {
    sendMsg(notaryClientRef, cmd)
  }
}