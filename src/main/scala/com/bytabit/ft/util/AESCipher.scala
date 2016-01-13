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

package com.bytabit.ft.util

import java.security.SecureRandom

import com.bytabit.ft.util.AESCipher._
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.spongycastle.crypto.params.{KeyParameter, ParametersWithIV}

object AESCipher {

  val AES_KEY_LEN = 16
  val AES_IV_LEN = 16

  def newAesKey = AESCipher.genRanData(AES_KEY_LEN)

  val ranGen = new SecureRandom()

  def genRanData(byteLength: Int): Array[Byte] = {
    val data = new Array[Byte](byteLength)
    ranGen.nextBytes(data)
    data
  }
}

case class AESCipher(key: Array[Byte], iv: Array[Byte]) {

  assert(key.length == AES_KEY_LEN)
  assert(iv.length == AES_IV_LEN)

  def aesCipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()))

  val keyParam = new KeyParameter(key)
  val ivAndKey = new ParametersWithIV(keyParam, iv)

  val encryptor = aesCipher
  val decryptor = aesCipher

  encryptor.init(true, ivAndKey)
  decryptor.init(false, ivAndKey)

  private def proccessData(cipher: PaddedBufferedBlockCipher, data: Array[Byte]): Array[Byte] = {
    val minSize = cipher.getOutputSize(data.length)
    var outBuf = new Array[Byte](minSize)
    val length1 = cipher.processBytes(data, 0, data.length, outBuf, 0)
    val length2 = cipher.doFinal(outBuf, length1)
    val actualLength = length1 + length2
    var result = new Array[Byte](actualLength)
    Array.copy(outBuf, 0, result, 0, result.length)
    result
  }

  def encrypt(plainData: Array[Byte]) = {
    proccessData(encryptor, plainData)
  }

  def decrypt(cipherData: Array[Byte]) = {
    proccessData(decryptor, cipherData)
  }
}

/**
private static byte[] cipherData(PaddedBufferedBlockCipher cipher, byte[] data)
        throws Exception
{
    int minSize = cipher.getOutputSize(data.length);
    byte[] outBuf = new byte[minSize];
    int length1 = cipher.processBytes(data, 0, data.length, outBuf, 0);
    int length2 = cipher.doFinal(outBuf, length1);
    int actualLength = length1 + length2;
    byte[] result = new byte[actualLength];
    System.arraycopy(outBuf, 0, result, 0, result.length);
    return result;
}

private static byte[] decrypt(byte[] cipher, byte[] key, byte[] iv) throws Exception
{
    PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(
            new AESEngine()));
    CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
    aes.init(false, ivAndKey);
    return cipherData(aes, cipher);
}

private static byte[] encrypt(byte[] plain, byte[] key, byte[] iv) throws Exception
{
    PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(
            new AESEngine()));
    CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
    aes.init(true, ivAndKey);
    return cipherData(aes, plain);
}
  */
