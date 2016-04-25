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

import org.bytabit.ft.arbitrator.ArbitratorManager._
import org.bytabit.ft.trade.TradeJsonProtocol
import org.bytabit.ft.util.EventJsonFormat
import spray.json._

trait ArbitratorJsonProtocol extends TradeJsonProtocol {

  implicit def arbitratorCreatedJsonFormat = jsonFormat3(ArbitratorCreated)

  implicit def contractAddedJsonFormat = jsonFormat3(ContractAdded)

  implicit def contractRemovedJsonFormat = jsonFormat3(ContractRemoved)

  val arbitratorEventJsonFormatMap: Map[String, RootJsonFormat[_ <: ArbitratorManager.Event]] = Map(
    simpleName(classOf[ArbitratorCreated]) -> arbitratorCreatedJsonFormat,
    simpleName(classOf[ContractAdded]) -> contractAddedJsonFormat,
    simpleName(classOf[ContractRemoved]) -> contractRemovedJsonFormat
  )

  implicit def arbitratorEventJsonFormat = new EventJsonFormat[ArbitratorManager.Event](arbitratorEventJsonFormatMap)

  implicit def arbitratorPostedEventJsonFormat = new RootJsonFormat[ArbitratorManager.PostedEvent] {

    override def read(json: JsValue): ArbitratorManager.PostedEvent =
      arbitratorEventJsonFormat.read(json) match {
        case pe: ArbitratorManager.PostedEvent => pe
        case _ => throw new DeserializationException("ArbitratorManager PostedEvent expected")
      }

    override def write(obj: ArbitratorManager.PostedEvent): JsValue =
      arbitratorEventJsonFormat.write(obj)
  }
}
