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
import org.bytabit.ft.trade.TradeFSMJsonProtocol
import spray.json._

trait NotaryClientFSMJsonProtocol extends TradeFSMJsonProtocol {

  implicit val notaryCreatedJsonFormat = jsonFormat3(NotaryCreated)
  implicit val contractAddedJsonFormat = jsonFormat3(ContractAdded)
  implicit val contractRemovedJsonFormat = jsonFormat3(ContractRemoved)

  implicit val sellTradeAddedJsonFormat = jsonFormat4(SellTradeAdded)
  implicit val buyTradeAddedJsonFormat = jsonFormat4(BuyTradeAdded)

  implicit val tradeRemovedJsonFormat = jsonFormat3(TradeRemoved)
  implicit val postedTradeEventReceivedJsonFormat = jsonFormat2(PostedTradeEventReceived)

  implicit val notaryEventJsonFormat = new RootJsonFormat[NotaryClientFSM.Event] {

    def read(value: JsValue): NotaryClientFSM.Event = value.asJsObject.getFields("clazz", "event") match {
      case Seq(JsString(clazz), event) => clazz match {
        case "NotaryCreated" => notaryCreatedJsonFormat.read(event)
        case "ContractAdded" => contractAddedJsonFormat.read(event)
        case "ContractRemoved" => contractRemovedJsonFormat.read(event)
        case "SellTradeAdded" => sellTradeAddedJsonFormat.read(event)
        case "BuyTradeAdded" => buyTradeAddedJsonFormat.read(event)
        case "TradeRemoved" => tradeRemovedJsonFormat.read(event)
        case "PostedTradeEventReceived" => postedTradeEventReceivedJsonFormat.read(event)
        case _ => throw new DeserializationException("NotaryClientFSM Event expected")
      }
      case e => throw new DeserializationException("NotaryClientFSM Event expected")
    }

    def write(evt: NotaryClientFSM.Event) = {
      val clazz = JsString(evt.getClass.getSimpleName)
      val eventJson: JsValue = evt match {
        case ac: NotaryCreated => notaryCreatedJsonFormat.write(ac)
        case cta: ContractAdded => contractAddedJsonFormat.write(cta)
        case ctr: ContractRemoved => contractRemovedJsonFormat.write(ctr)
        case ta: SellTradeAdded => sellTradeAddedJsonFormat.write(ta)
        case ta: BuyTradeAdded => buyTradeAddedJsonFormat.write(ta)
        case tr: TradeRemoved => tradeRemovedJsonFormat.write(tr)
        case pte: PostedTradeEventReceived => postedTradeEventReceivedJsonFormat.write(pte)
        case _ =>
          throw new SerializationException("NotaryClientFSM Event expected")
      }
      JsObject(
        "clazz" -> clazz,
        "event" -> eventJson
      )
    }
  }

  implicit val notaryPostedEventJsonFormat = new RootJsonFormat[NotaryClientFSM.PostedEvent] {

    override def read(json: JsValue): PostedEvent =
      notaryEventJsonFormat.read(json) match {
        case pe: PostedEvent => pe
        case _ => throw new DeserializationException("NotaryClientFSM PostedEvent expected")
      }

    override def write(obj: PostedEvent): JsValue =
      notaryEventJsonFormat.write(obj)
  }

  implicit val postedEventsJsonFormat = jsonFormat(PostedEvents.apply, "notaryEvents", "tradeEvents")
}
