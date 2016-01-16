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

import org.bytabit.ft.notary.NotaryClientManager.{NotaryAdded, NotaryRemoved}
import org.bytabit.ft.util.{EventJsonFormat, UtilJsonProtocol}

trait NotaryClientManagerJsonProtocol extends UtilJsonProtocol {

  implicit def notaryAddedJsonFormat = jsonFormat(NotaryAdded.apply(_), "url")

  implicit def notaryRemovedJsonFormat = jsonFormat(NotaryRemoved.apply(_), "url")

  implicit def notaryClientManagerEventJsonFormat = new EventJsonFormat[NotaryClientManager.Event](
    Map(simpleName(classOf[NotaryAdded]) -> notaryAddedJsonFormat,
      simpleName(classOf[NotaryRemoved]) -> notaryRemovedJsonFormat)
  )
}
