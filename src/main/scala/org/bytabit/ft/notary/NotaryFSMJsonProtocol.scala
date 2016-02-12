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

import org.bytabit.ft.notary.NotaryFSM._
import org.bytabit.ft.notary.server.PostedEvents
import org.bytabit.ft.trade.TradeFSMJsonProtocol
import org.bytabit.ft.util.EventJsonFormat
import spray.json._

trait NotaryFSMJsonProtocol extends TradeFSMJsonProtocol {

  implicit def notaryCreatedJsonFormat = jsonFormat3(NotaryCreated)

  implicit def contractAddedJsonFormat = jsonFormat3(ContractAdded)

  implicit def contractRemovedJsonFormat = jsonFormat3(ContractRemoved)

  implicit def sellTradeAddedJsonFormat = jsonFormat4(SellTradeAdded)

  implicit def buyTradeAddedJsonFormat = jsonFormat4(BuyTradeAdded)

  implicit def notarizeTradeAddedJsonFormat = jsonFormat4(NotarizeTradeAdded)

  implicit def tradeRemovedJsonFormat = jsonFormat3(TradeRemoved)

  implicit def postedTradeEventReceivedJsonFormat = jsonFormat2(PostedTradeEventReceived)

  val notaryEventJsonFormatMap: Map[String, RootJsonFormat[_ <: NotaryFSM.Event]] = Map(
    simpleName(classOf[NotaryCreated]) -> notaryCreatedJsonFormat,
    simpleName(classOf[ContractAdded]) -> contractAddedJsonFormat,
    simpleName(classOf[ContractRemoved]) -> contractRemovedJsonFormat,
    simpleName(classOf[SellTradeAdded]) -> sellTradeAddedJsonFormat,
    simpleName(classOf[BuyTradeAdded]) -> buyTradeAddedJsonFormat,
    simpleName(classOf[NotarizeTradeAdded]) -> notarizeTradeAddedJsonFormat,
    simpleName(classOf[TradeRemoved]) -> tradeRemovedJsonFormat,
    simpleName(classOf[PostedTradeEventReceived]) -> postedTradeEventReceivedJsonFormat
  )

  implicit def notaryEventJsonFormat = new EventJsonFormat[NotaryFSM.Event](notaryEventJsonFormatMap)

  implicit def notaryPostedEventJsonFormat = new RootJsonFormat[NotaryFSM.PostedEvent] {

    override def read(json: JsValue): NotaryFSM.PostedEvent =
      notaryEventJsonFormat.read(json) match {
        case pe: NotaryFSM.PostedEvent => pe
        case _ => throw new DeserializationException("NotaryClientFSM PostedEvent expected")
      }

    override def write(obj: NotaryFSM.PostedEvent): JsValue =
      notaryEventJsonFormat.write(obj)
  }

  implicit val postedEventsJsonFormat = jsonFormat(PostedEvents.apply, "notaryEvents", "tradeEvents")
}
