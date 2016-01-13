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
import javax.crypto.Cipher._

import org.bitcoinj.core.ECKey
import org.spongycastle.crypto.agreement.ECDHBasicAgreement
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.engines.{AESEngine, IESEngine}
import org.spongycastle.crypto.generators.KDF2BytesGenerator
import org.spongycastle.crypto.macs.HMac
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.spongycastle.jcajce.provider.asymmetric.ec.{BCECPrivateKey, BCECPublicKey, IESCipher}
import org.spongycastle.jce.provider.BouncyCastleProvider
import org.spongycastle.jce.spec.{ECParameterSpec, ECPrivateKeySpec, ECPublicKeySpec, IESParameterSpec}

case class ECIESCipher(ecKey: ECKey) {

  final val MAC_KEY_SIZE_IN_BITS = 256
  final val AES_KEY_SIZE_IN_BITS = 256

  final val algorithm = "EC"

  final val ranGen = new SecureRandom()

  final val ecParamSpec = new ECParameterSpec(ECKey.CURVE.getCurve, ECKey.CURVE.getG, ECKey.CURVE.getN)

  final val parameterSpec = new IESParameterSpec(null, null, MAC_KEY_SIZE_IN_BITS, AES_KEY_SIZE_IN_BITS)

  def iesEngine = new IESEngine(new ECDHBasicAgreement(),
    new KDF2BytesGenerator(new SHA256Digest()),
    new HMac(new SHA256Digest()),
    new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine())))

  def iesCipher = new IESCipher(iesEngine)

  val encryptor = iesCipher
  val remotePubKeyPoint = ecKey.getPubKeyPoint
  val pubKeySpec = new ECPublicKeySpec(remotePubKeyPoint, ecParamSpec)
  val pubKey = new BCECPublicKey(algorithm, pubKeySpec, BouncyCastleProvider.CONFIGURATION)
  encryptor.engineInit(ENCRYPT_MODE, pubKey, parameterSpec, ranGen)

  def encrypt(plain: Array[Byte]): Array[Byte] = {
    encryptor.engineDoFinal(plain, 0, plain.length)
  }

  val decryptor = iesCipher
  val privKeySpec = new ECPrivateKeySpec(ecKey.getPrivKey, ecParamSpec)
  val privKey = new BCECPrivateKey(algorithm, privKeySpec, BouncyCastleProvider.CONFIGURATION)
  decryptor.engineInit(DECRYPT_MODE, privKey, parameterSpec, ranGen)

  def decrypt(cipher: Array[Byte]): Array[Byte] = {
    decryptor.engineDoFinal(cipher, 0, cipher.length)
  }

}
