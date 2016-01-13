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

import org.bytabit.ft.notary.NotaryServerManager.{NotaryEventPosted, TradeEventPosted}
import spray.json._

trait NotaryServerJsonProtocol extends NotaryClientFSMJsonProtocol {

  implicit val notaryEventPostedJsonFormat = jsonFormat1(NotaryEventPosted)
  implicit val tradeEventPostedJsonFormat = jsonFormat1(TradeEventPosted)

  implicit val notaryServerManagerEventJsonFormat = new RootJsonFormat[NotaryServerManager.Event] {

    // TODO use macro reflection to create maps of sub-class type, type name to JsonFormatter?

    def read(value: JsValue): NotaryServerManager.Event = value.asJsObject.getFields("clazz", "event") match {
      case Seq(JsString(clazz), event) => clazz match {
        case "NotaryEventPosted" => notaryEventPostedJsonFormat.read(event)
        case "TradeEventPosted" => tradeEventPostedJsonFormat.read(event)

        case _ => throw new DeserializationException("NotaryServerManager PostedEvent expected")
      }
      case e => throw new DeserializationException("NotaryServerManager PostedEvent expected")
    }

    def write(evt: NotaryServerManager.Event) = {
      val clazz = JsString(evt.getClass.getSimpleName)
      val eventJson: JsValue = evt match {
        case aep: NotaryEventPosted => notaryEventPostedJsonFormat.write(aep)
        case tep: TradeEventPosted => tradeEventPostedJsonFormat.write(tep)

        case _ =>
          throw new SerializationException("NotaryServerManager Event expected")
      }
      JsObject(
        "clazz" -> clazz,
        "event" -> eventJson
      )
    }
  }

}
