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

import org.bitcoinj.core.ECKey
import org.scalatest._
import org.scalatest.prop.PropertyChecks

class ECIESCipherSpec extends FlatSpec with Matchers with PropertyChecks {

  it should "encrypt and decrypt a message using a bitcoinj ECKey" in {

    val testText1 = "This is the first test message."
    val plain1: Array[Byte] = testText1.map(c => c.toByte).toArray

    val testText2 = "This is another test message."
    val plain2: Array[Byte] = testText2.map(c => c.toByte).toArray

    val ecKey = new ECKey()
    val eciesCipher = ECIESCipher(ecKey)

    val cipher1 = eciesCipher.encrypt(plain1)
    val decrypted1 = eciesCipher.decrypt(cipher1)

    plain1 should equal(decrypted1)

    val cipher2 = eciesCipher.encrypt(plain2)
    val decrypted2 = eciesCipher.decrypt(cipher2)

    plain2 should equal(decrypted2)

    plain1 shouldNot equal(decrypted2)
  }
}
