/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.bytabit.ft.client

import org.bytabit.ft.arbitrator.ArbitratorJsonProtocol
import org.bytabit.ft.client.EventClient._
import org.bytabit.ft.server.PostedEvents
import org.bytabit.ft.util.EventJsonFormat
import spray.json._

trait EventClientJsonProtocol extends ArbitratorJsonProtocol {

  implicit def arbitratorAddedJsonFormat = jsonFormat(ArbitratorAdded, "url", "arbitrator", "posted")

  implicit def tradeAddedJsonFormat = jsonFormat(TradeAdded, "url", "role", "tradeId", "offer", "posted")

  implicit def tradeRemovedJsonFormat = jsonFormat(TradeRemoved, "url", "tradeId", "posted")

  implicit def postedTradeEventReceivedJsonFormat = jsonFormat(PostedEventReceived, "url", "posted")

  val eventClientJsonFormatMap: Map[String, RootJsonFormat[_ <: EventClient.Event]] = Map(
    simpleName(classOf[ArbitratorAdded]) -> arbitratorAddedJsonFormat,
    simpleName(classOf[TradeAdded]) -> tradeAddedJsonFormat,
    simpleName(classOf[TradeRemoved]) -> tradeRemovedJsonFormat,
    simpleName(classOf[PostedEventReceived]) -> postedTradeEventReceivedJsonFormat
  )

  implicit def eventClientJsonFormat = new EventJsonFormat[EventClient.Event](eventClientJsonFormatMap)

  implicit def postedEventsJsonFormat = jsonFormat(PostedEvents.apply, "arbitratorEvents", "tradeEvents")
}
