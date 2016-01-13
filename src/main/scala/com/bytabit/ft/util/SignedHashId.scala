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

import org.bitcoinj.core.ECKey.ECDSASignature
import org.bitcoinj.core.{ECKey, Sha256Hash, Wallet}

trait SignedHashId {

  val separator = "::"

  def hashId(idStrings: String*): Sha256Hash = {
    // make sure strings don't cointain separator
    idStrings.foreach(s => assert(!(s contains separator)))

    Sha256Hash.of((idStrings mkString separator).toCharArray.map(_.toByte))
  }

  def hashIdSig(pubKey: ECKey, hashId: Sha256Hash)(implicit w: Wallet): Option[ECDSASignature] = {
    try {
      Option(w.findKeyFromPubKey(pubKey.getPubKey)).map(_.sign(hashId))
    } catch {
      case e: Exception => None
    }
  }

  def verifyHashId(pubKey: ECKey, hashIdSig: ECDSASignature, hashId: Sha256Hash): Boolean = {

    try {
      pubKey.verify(hashId, hashIdSig)
    } catch {
      case e: Exception => false
    }
  }
}
