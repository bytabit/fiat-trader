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

package org.bytabit.ft.server

import org.bytabit.ft.client.EventClientJsonProtocol
import org.bytabit.ft.server.EventServer.{ArbitratorEventPosted, TradeEventPosted}
import org.bytabit.ft.util.EventJsonFormat

trait EventServerJsonProtocol extends EventClientJsonProtocol {

  implicit def arbitratorEventPostedJsonFormat = jsonFormat1(ArbitratorEventPosted)

  implicit def tradeEventPostedJsonFormat = jsonFormat1(TradeEventPosted)

  implicit def arbitratorServerManagerEventJsonFormat = new EventJsonFormat[EventServer.Event](
    Map(simpleName(classOf[ArbitratorEventPosted]) -> arbitratorEventPostedJsonFormat,
      simpleName(classOf[TradeEventPosted]) -> tradeEventPostedJsonFormat)
  )

}
