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

import java.net.URL
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.joda.money.{CurrencyUnit, Money}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import spray.json._

trait UtilJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  import spray.json._

  def simpleName(clazz: Class[_]): String = clazz.getSimpleName

  implicit object MoneyJsonFormat extends JsonFormat[Money] {

    def read(value: JsValue) = value match {
      case JsString(m) => Money.parse(m)
      case _ => deserializationError("Money expected")
    }

    def write(m: Money) = JsString(m.toString)
  }

  implicit object URLJsonFormat extends JsonFormat[URL] {

    def read(value: JsValue) = value match {
      case JsString(u) => new URL(u)
      case _ => deserializationError("URL expected")
    }

    def write(u: URL) = JsString(u.toString)
  }

  val dateTimeFormatter = ISODateTimeFormat.dateTime.withZoneUTC

  implicit object DateTimeJsonFormat extends JsonFormat[DateTime] {

    def read(value: JsValue) = value match {
      case JsString(dateTime) => dateTimeFormatter.parseDateTime(dateTime)
      case _ => deserializationError("DateTime expected")
    }

    def write(dateTime: DateTime) = JsString(dateTimeFormatter.print(dateTime))
  }

  // CurrencyUnit json protocol
  implicit object CurrencyUnitJsonFormat extends JsonFormat[CurrencyUnit] {

    def read(value: JsValue) = value match {
      case JsString(cu) => CurrencyUnit.getInstance(cu)
      case _ => deserializationError("CurrencyUnit code expected")
    }

    def write(cu: CurrencyUnit) = JsString(cu.getCode)
  }

  // PaymentMethod json protocol
  implicit object PaymentMethodJsonFormat extends JsonFormat[PaymentMethod] {

    def read(value: JsValue) = value match {
      case JsString(fdm) => PaymentMethod.getInstance(fdm).getOrElse(deserializationError("PaymentMethod name expected"))
      case _ => deserializationError("PaymentMethod name expected")
    }

    def write(fdm: PaymentMethod) = JsString(fdm.name)
  }

  // UUID json protocol
  implicit object UUIDJsonFormat extends JsonFormat[UUID] {

    def read(value: JsValue) = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ => deserializationError("UUID expected")
    }

    def write(uuid: UUID) = JsString(uuid.toString)
  }

  // found here: https://groups.google.com/forum/#!topic/spray-user/RkIwRIXzDDc
  def jsonEnum[T <: Enumeration](enu: T) = new JsonFormat[T#Value] {
    def write(obj: T#Value) = JsString(obj.toString)

    def read(json: JsValue) = json match {
      case JsString(txt) => enu.withName(txt)
      case something => throw new DeserializationException(s"Expected a value from enum $enu instead of $something")
    }
  }
}
