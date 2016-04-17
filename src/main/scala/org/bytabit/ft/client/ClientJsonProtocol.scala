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

package org.bytabit.ft.client

import org.bytabit.ft.client.ClientFSM._
import org.bytabit.ft.server.PostedEvents
import org.bytabit.ft.trade.TradeJsonProtocol
import org.bytabit.ft.util.EventJsonFormat
import spray.json._

trait ClientJsonProtocol extends TradeJsonProtocol {

  implicit def arbitratorCreatedJsonFormat = jsonFormat3(ArbitratorCreated)

  implicit def contractAddedJsonFormat = jsonFormat3(ContractAdded)

  implicit def contractRemovedJsonFormat = jsonFormat3(ContractRemoved)

  implicit def sellTradeAddedJsonFormat = jsonFormat4(SellTradeAdded)

  implicit def buyTradeAddedJsonFormat = jsonFormat4(BuyTradeAdded)

  implicit def arbitrateTradeAddedJsonFormat = jsonFormat4(ArbitrateTradeAdded)

  implicit def tradeRemovedJsonFormat = jsonFormat3(TradeRemoved)

  implicit def postedTradeEventReceivedJsonFormat = jsonFormat2(PostedTradeEventReceived)

  val arbitratorEventJsonFormatMap: Map[String, RootJsonFormat[_ <: ClientFSM.Event]] = Map(
    simpleName(classOf[ArbitratorCreated]) -> arbitratorCreatedJsonFormat,
    simpleName(classOf[ContractAdded]) -> contractAddedJsonFormat,
    simpleName(classOf[ContractRemoved]) -> contractRemovedJsonFormat,
    simpleName(classOf[SellTradeAdded]) -> sellTradeAddedJsonFormat,
    simpleName(classOf[BuyTradeAdded]) -> buyTradeAddedJsonFormat,
    simpleName(classOf[ArbitrateTradeAdded]) -> arbitrateTradeAddedJsonFormat,
    simpleName(classOf[TradeRemoved]) -> tradeRemovedJsonFormat,
    simpleName(classOf[PostedTradeEventReceived]) -> postedTradeEventReceivedJsonFormat
  )

  implicit def arbitratorEventJsonFormat = new EventJsonFormat[ClientFSM.Event](arbitratorEventJsonFormatMap)

  implicit def arbitratorPostedEventJsonFormat = new RootJsonFormat[ClientFSM.PostedEvent] {

    override def read(json: JsValue): ClientFSM.PostedEvent =
      arbitratorEventJsonFormat.read(json) match {
        case pe: ClientFSM.PostedEvent => pe
        case _ => throw new DeserializationException("ArbitratorClientFSM PostedEvent expected")
      }

    override def write(obj: ClientFSM.PostedEvent): JsValue =
      arbitratorEventJsonFormat.write(obj)
  }

  implicit val postedEventsJsonFormat = jsonFormat(PostedEvents.apply, "arbitratorEvents", "tradeEvents")
}
