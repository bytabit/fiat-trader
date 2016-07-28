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

import com.google.common.primitives.UnsignedBytes
import org.bitcoinj.core.{ECKey, NetworkParameters, Utils}

object PubECKey {

  def apply(eckey: ECKey): PubECKey = PubECKey(eckey.getPubKey)

  def apply(ecKey: String): PubECKey = PubECKey(Utils.HEX.decode(ecKey))

  // true if a eckey pub bytes lexiconographically less than b eckey pub pub
  def lt(a: PubECKey, b: PubECKey) = UnsignedBytes.lexicographicalComparator.compare(a.bytes, b.bytes) < 0

  def sort(seq: Seq[PubECKey]): Seq[PubECKey] = seq.sortWith(lt)
}

case class PubECKey(bytes: Array[Byte]) extends Serializable {

  def eckey = ECKey.fromPublicOnly(bytes)

  def toAddress(params: NetworkParameters) = eckey.toAddress(params)

  override def toString = Utils.HEX.encode(bytes)
}