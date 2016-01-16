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
import org.bytabit.ft.util.EventJsonFormat

trait NotaryServerJsonProtocol extends NotaryClientFSMJsonProtocol {

  implicit def notaryEventPostedJsonFormat = jsonFormat1(NotaryEventPosted)

  implicit def tradeEventPostedJsonFormat = jsonFormat1(TradeEventPosted)

  implicit def notaryServerManagerEventJsonFormat = new EventJsonFormat[NotaryServerManager.Event](
    Map(simpleName(classOf[NotaryEventPosted]) -> notaryEventPostedJsonFormat,
      simpleName(classOf[TradeEventPosted]) -> tradeEventPostedJsonFormat)
  )
}
