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

package org.bytabit.ft.arbitrator

import org.bytabit.ft.arbitrator.ArbitratorFSM._
import org.bytabit.ft.arbitrator.server.PostedEvents
import org.bytabit.ft.trade.TradeFSMJsonProtocol
import org.bytabit.ft.util.EventJsonFormat
import spray.json._

trait ArbitratorFSMJsonProtocol extends TradeFSMJsonProtocol {

  implicit def arbitratorCreatedJsonFormat = jsonFormat3(ArbitratorCreated)

  implicit def contractAddedJsonFormat = jsonFormat3(ContractAdded)

  implicit def contractRemovedJsonFormat = jsonFormat3(ContractRemoved)

  implicit def sellTradeAddedJsonFormat = jsonFormat4(SellTradeAdded)

  implicit def buyTradeAddedJsonFormat = jsonFormat4(BuyTradeAdded)

  implicit def arbitrateTradeAddedJsonFormat = jsonFormat4(ArbitrateTradeAdded)

  implicit def tradeRemovedJsonFormat = jsonFormat3(TradeRemoved)

  implicit def postedTradeEventReceivedJsonFormat = jsonFormat2(PostedTradeEventReceived)

  val arbitratorEventJsonFormatMap: Map[String, RootJsonFormat[_ <: ArbitratorFSM.Event]] = Map(
    simpleName(classOf[ArbitratorCreated]) -> arbitratorCreatedJsonFormat,
    simpleName(classOf[ContractAdded]) -> contractAddedJsonFormat,
    simpleName(classOf[ContractRemoved]) -> contractRemovedJsonFormat,
    simpleName(classOf[SellTradeAdded]) -> sellTradeAddedJsonFormat,
    simpleName(classOf[BuyTradeAdded]) -> buyTradeAddedJsonFormat,
    simpleName(classOf[ArbitrateTradeAdded]) -> arbitrateTradeAddedJsonFormat,
    simpleName(classOf[TradeRemoved]) -> tradeRemovedJsonFormat,
    simpleName(classOf[PostedTradeEventReceived]) -> postedTradeEventReceivedJsonFormat
  )

  implicit def arbitratorEventJsonFormat = new EventJsonFormat[ArbitratorFSM.Event](arbitratorEventJsonFormatMap)

  implicit def arbitratorPostedEventJsonFormat = new RootJsonFormat[ArbitratorFSM.PostedEvent] {

    override def read(json: JsValue): ArbitratorFSM.PostedEvent =
      arbitratorEventJsonFormat.read(json) match {
        case pe: ArbitratorFSM.PostedEvent => pe
        case _ => throw new DeserializationException("ArbitratorClientFSM PostedEvent expected")
      }

    override def write(obj: ArbitratorFSM.PostedEvent): JsValue =
      arbitratorEventJsonFormat.write(obj)
  }

  implicit val postedEventsJsonFormat = jsonFormat(PostedEvents.apply, "arbitratorEvents", "tradeEvents")
}
