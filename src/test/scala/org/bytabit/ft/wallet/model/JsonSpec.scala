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

package org.bytabit.ft.wallet.model

import java.net.URL

import org.bitcoinj.core._
import org.bytabit.ft.util.BTCMoney
import org.bytabit.ft.wallet.WalletJsonProtocol
import org.scalatest._
import spray.json._

class JsonSpec extends FlatSpec with Matchers with WalletJsonProtocol {

  val params = NetworkParameters.fromID(NetworkParameters.ID_TESTNET)
  Context.propagate(new Context(params))

  // notary
  val notaryWallet = new Wallet(params)

  val notaryUrl = new URL("http://bytabit.com/notary")
  val bondPercent = 0.20
  val btcNotaryFee = BTCMoney(0.10)

  val testNotary = Notary(notaryUrl, bondPercent, btcNotaryFee)(notaryWallet)

  it should "serialize initialized to json" in {

    val json: String = testNotary.toJson.toString()
    //System.out.println(json)

    val obj: Notary = json.parseJson.convertTo[Notary]
    //System.out.println(obj)

    json.toString should equal(obj.toJson.toString())
  }

}
