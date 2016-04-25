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

import org.bytabit.ft.arbitrator.ArbitratorManager
import org.bytabit.ft.trade.TradeProcess
import org.bytabit.ft.util.DateTimeOrdering

final case class PostedEvents(arbitratorEvents: Seq[ArbitratorManager.PostedEvent],
                              tradeEvents: Seq[TradeProcess.PostedEvent]) {

  val latestUpdate = (arbitratorEvents.flatMap(_.posted) ++ tradeEvents.flatMap(_.posted))
    .reduceOption(DateTimeOrdering.max)
}