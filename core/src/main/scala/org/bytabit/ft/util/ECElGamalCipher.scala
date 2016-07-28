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

import java.math.BigInteger
import java.security.SecureRandom

import org.bitcoinj.core.ECKey
import org.spongycastle.crypto.ec.{ECElGamalDecryptor, ECElGamalEncryptor, ECPair}
import org.spongycastle.crypto.params.{ECPrivateKeyParameters, ECPublicKeyParameters, ParametersWithRandom}
import org.spongycastle.math.ec.ECPoint

case class ECElGamalCipher() {

  final val ranGen = new SecureRandom()
  final val randNumLength: Int = ECKey.CURVE.getN.bitLength - 1

  def newEcPoint: ECPoint = newEcPoint(new BigInteger(randNumLength, ranGen))

  def newEcPoint(randNum: BigInteger): ECPoint = {
    assert(randNum.bitLength() <= randNumLength)
    ECKey.CURVE.getG.multiply(randNum)
  }

  def ecPointToBytes(ecPoint: ECPoint) = ecPoint.getEncoded

  def encrypt(pubKey: ECKey, ecPoint: ECPoint): ECPair = {
    val pubKeyParams = new ECPublicKeyParameters(pubKey.getPubKeyPoint, ECKey.CURVE)
    val pRandom = new ParametersWithRandom(pubKeyParams, ranGen)
    val encryptCipher = new ECElGamalEncryptor()
    encryptCipher.init(pRandom)
    encryptCipher.encrypt(ecPoint)
  }

  def decrypt(privKey: ECKey, ecPair: ECPair): ECPoint = {
    val privKeyParams = new ECPrivateKeyParameters(privKey.getPrivKey, ECKey.CURVE)
    val decryptor = new ECElGamalDecryptor()
    decryptor.init(privKeyParams)
    decryptor.decrypt(ecPair)
  }

}
