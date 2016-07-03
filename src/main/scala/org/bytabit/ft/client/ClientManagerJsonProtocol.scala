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

package org.bytabit.ft.client

import org.bytabit.ft.client.ClientManager.{ClientCreated, ServerAdded, ServerRemoved}
import org.bytabit.ft.client.model.ClientProfile
import org.bytabit.ft.util.EventJsonFormat
import org.bytabit.ft.wallet.WalletJsonProtocol

trait ClientManagerJsonProtocol extends WalletJsonProtocol {

  implicit def clientProfile = jsonFormat3(ClientProfile)

  implicit def clientCreatedFormat = jsonFormat(ClientCreated.apply(_), "profile")

  implicit def serverAddedJsonFormat = jsonFormat(ServerAdded.apply(_), "url")

  implicit def serverRemovedJsonFormat = jsonFormat(ServerRemoved.apply(_), "url")

  implicit def clientManagerEventJsonFormat = new EventJsonFormat[ClientManager.Event](
    Map(simpleName(classOf[ClientCreated]) -> clientCreatedFormat,
      simpleName(classOf[ServerAdded]) -> serverAddedJsonFormat,
      simpleName(classOf[ServerRemoved]) -> serverRemovedJsonFormat)
  )
}
