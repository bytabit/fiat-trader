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

import java.nio.charset.Charset

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer

abstract class AbstractSprayJsonSerializer[T <: AnyRef](val system: ExtendedActorSystem) extends Serializer {

  def hashId(sid: String): Int = sid.hashCode.abs

  val UTF8: Charset = Charset.forName("UTF-8")

  def includeManifest = true

  def bytesToString(bytes: Array[Byte]): String = new String(bytes, UTF8)

  def stringToBytes(str: String): Array[Byte] = str.getBytes(UTF8)
}
