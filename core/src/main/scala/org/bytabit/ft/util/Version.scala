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

object Version {
  def apply(s: String): Version = {
    val split = s.split('.')
    val major = if (split.length > 0) split(0).toInt else 0
    val minor = if (split.length > 1) split(1).toInt else 0

    val patchHash = if (split.length > 2) Some(split(2).split('-')) else None
    val patch = if (patchHash.isDefined && patchHash.get.length > 0)
      patchHash.get(0).toInt
    else 0

    new Version(major, minor, patch)
  }

  def apply(config:Config): Version = apply(config.version)
}

@SerialVersionUID(100L)
case class Version(major: Int = 0, minor: Int = 0, patch: Int = 0) extends Serializable {

  override def toString = s"$major.$minor.$patch"

}
