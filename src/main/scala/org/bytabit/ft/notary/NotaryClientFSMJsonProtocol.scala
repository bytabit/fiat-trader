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

package org.bytabit.ft.notary

import org.bytabit.ft.notary.NotaryClientFSM._
import org.bytabit.ft.notary.server.PostedEvents
import org.bytabit.ft.trade.TradeFSMJsonProtocol
import org.bytabit.ft.util.EventJsonFormat
import spray.json._

trait NotaryClientFSMJsonProtocol extends TradeFSMJsonProtocol {

  implicit def notaryCreatedJsonFormat = jsonFormat3(NotaryCreated)

  implicit def contractAddedJsonFormat = jsonFormat3(ContractAdded)

  implicit def contractRemovedJsonFormat = jsonFormat3(ContractRemoved)

  implicit def sellTradeAddedJsonFormat = jsonFormat4(SellTradeAdded)

  implicit def buyTradeAddedJsonFormat = jsonFormat4(BuyTradeAdded)

  implicit def tradeRemovedJsonFormat = jsonFormat3(TradeRemoved)

  implicit def postedTradeEventReceivedJsonFormat = jsonFormat2(PostedTradeEventReceived)

  val notaryClientEventJsonFormatMap: Map[String, RootJsonFormat[_ <: NotaryClientFSM.Event]] = Map(
    simpleName(classOf[NotaryCreated]) -> notaryCreatedJsonFormat,
    simpleName(classOf[ContractAdded]) -> contractAddedJsonFormat,
    simpleName(classOf[ContractRemoved]) -> contractRemovedJsonFormat,
    simpleName(classOf[SellTradeAdded]) -> sellTradeAddedJsonFormat,
    simpleName(classOf[BuyTradeAdded]) -> buyTradeAddedJsonFormat,
    simpleName(classOf[TradeRemoved]) -> tradeRemovedJsonFormat,
    simpleName(classOf[PostedTradeEventReceived]) -> postedTradeEventReceivedJsonFormat
  )

  implicit def notaryEventJsonFormat = new EventJsonFormat[NotaryClientFSM.Event](notaryClientEventJsonFormatMap)

  implicit def notaryPostedEventJsonFormat = new RootJsonFormat[NotaryClientFSM.PostedEvent] {

    override def read(json: JsValue): NotaryClientFSM.PostedEvent =
      notaryEventJsonFormat.read(json) match {
        case pe: NotaryClientFSM.PostedEvent => pe
        case _ => throw new DeserializationException("NotaryClientFSM PostedEvent expected")
      }

    override def write(obj: NotaryClientFSM.PostedEvent): JsValue =
      notaryEventJsonFormat.write(obj)
  }

  implicit val postedEventsJsonFormat = jsonFormat(PostedEvents.apply, "notaryEvents", "tradeEvents")
}
