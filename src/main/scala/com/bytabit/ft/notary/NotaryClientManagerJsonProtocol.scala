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

package com.bytabit.ft.notary

import com.bytabit.ft.notary.NotaryClientManager.{NotaryAdded, NotaryRemoved}
import com.bytabit.ft.util.UtilJsonProtocol
import spray.json._

trait NotaryClientManagerJsonProtocol extends UtilJsonProtocol {

  implicit val notaryAddedJsonFormat = jsonFormat(NotaryAdded.apply(_), "url")
  implicit val notaryRemovedJsonFormat = jsonFormat(NotaryRemoved.apply(_), "url")

  implicit val notaryClientManagerEventJsonFormat = new RootJsonFormat[NotaryClientManager.Event] {

    def read(value: JsValue) = value.asJsObject.getFields("clazz", "event") match {
      case Seq(JsString(clazz), event) => clazz match {
        case "NotaryAdded" => notaryAddedJsonFormat.read(event)
        case "NotaryRemoved" => notaryRemovedJsonFormat.read(event)

        case _ => throw new DeserializationException("NotaryClientManager Event expected")
      }
      case e => throw new DeserializationException("NotaryClientManager Event expected")
    }

    def write(evt: NotaryClientManager.Event) = {
      val clazz = JsString(evt.getClass.getSimpleName)
      val eventJson: JsValue = evt match {
        case aa: NotaryAdded => notaryAddedJsonFormat.write(aa)
        case aa: NotaryRemoved => notaryRemovedJsonFormat.write(aa)

        case _ =>
          throw new SerializationException("NotaryClientManager Event expected")
      }
      JsObject(
        "clazz" -> clazz,
        "event" -> eventJson
      )
    }
  }
}
