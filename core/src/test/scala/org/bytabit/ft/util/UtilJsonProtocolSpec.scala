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

import org.bytabit.ft.util.CurrencyUnits._
import org.joda.money.IllegalCurrencyException
import spray.json.JsString

class UtilJsonProtocolSpec extends FlatSpec with Matchers with PropertyChecks with UtilJsonProtocol {

  "The CurrencyUnitJsonFormat" should "convert CurrencyUnit objects to and from valid JSON strings" in {

    val json = CurrencyUnitJsonFormat.write(XBT)

    CurrencyUnitJsonFormat.read(json) shouldEqual XBT
  }

  it should "throw IllegalCurrencyException if an illegal currency code is provided" in {
    val json = JsString("FOO")
    a[IllegalCurrencyException] should be thrownBy {
      CurrencyUnitJsonFormat.read(json)
    }
  }
}
