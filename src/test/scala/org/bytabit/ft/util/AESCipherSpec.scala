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

import org.scalatest._
import org.scalatest.prop.PropertyChecks

class AESCipherSpec extends FlatSpec with Matchers with PropertyChecks {

  it should "encrypt and decrypt a message" in {

    val key = AESCipher.genRanData(AESCipher.AES_KEY_LEN)
    val iv = AESCipher.genRanData(AESCipher.AES_IV_LEN)

    val aesCipher = AESCipher(key, iv)

    val testText = "This is a test message."
    val plain: Array[Byte] = testText.map(c => c.toByte).toArray

    val cipher = aesCipher.encrypt(plain)
    val decrypted = aesCipher.decrypt(cipher)

    plain should equal(decrypted)

    val decryptedTestText: String = new String(decrypted.map(b => b.toChar))
    testText should equal(decryptedTestText)
  }

  it should "encrypt to a differnet cipher text given a different IV" in {

    val testText = "This is a test message."
    val plain: Array[Byte] = testText.map(c => c.toByte).toArray
    val key = AESCipher.genRanData(AESCipher.AES_KEY_LEN)
    val iv1 = AESCipher.genRanData(AESCipher.AES_IV_LEN)
    val iv2 = AESCipher.genRanData(AESCipher.AES_IV_LEN)

    val aesCipher1 = AESCipher(key, iv1)
    val aesCipher2 = AESCipher(key, iv2)

    val cipher1 = aesCipher1.encrypt(plain)
    val cipher2 = aesCipher2.encrypt(plain)

    val decrypted1 = aesCipher1.decrypt(cipher1)
    val decrypted2 = aesCipher2.decrypt(cipher2)

    plain should equal(decrypted1)
    plain should equal(decrypted2)

    cipher1 shouldNot equal(cipher2)
  }

  it should "encrypt message should be the same length as the plain message plus iv" in {

    val key = AESCipher.genRanData(AESCipher.AES_KEY_LEN)
    val iv = AESCipher.genRanData(AESCipher.AES_IV_LEN)

    val aesCipher = AESCipher(key, iv)

    val plain = AESCipher.genRanData(AESCipher.AES_KEY_LEN)

    val cipher = aesCipher.encrypt(plain)
    val decrypted = aesCipher.decrypt(cipher)

    plain.length + iv.length should equal(cipher.length)
    plain should equal(decrypted)
  }
}
