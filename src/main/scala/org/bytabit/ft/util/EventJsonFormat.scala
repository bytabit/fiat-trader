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

package org.bytabit.ft.util

import spray.json._

class EventJsonFormat[E](eventJsonFormatMap: Map[String, RootJsonFormat[_ <: E]]) extends RootJsonFormat[E] {

  override def read(json: JsValue): E = {
    json.asJsObject.getFields("event", "data") match {
      case Seq(JsString(event), data) =>
        eventJsonFormatMap.get(event) match {
          case Some(format) =>
            format.read(data)
          case _ =>
            throw new DeserializationException(s"No Event json format found for: $event")
        }
      case _ =>
        throw new DeserializationException("Event class name and event data expected")
    }
  }

  override def write(data: E): JsValue = {
    val event = data.getClass.getSimpleName
    val eventJson: JsValue = eventJsonFormatMap.get(event) match {
      case Some(format) =>
        format.asInstanceOf[RootJsonFormat[E]].write(data)
      case _ =>
        throw new DeserializationException(s"No Event json format found for: $event")
    }
    JsObject(
      "event" -> JsString(event),
      "data" -> eventJson
    )
  }
}
