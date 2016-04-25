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

package org.bytabit.ft.arbitrator

import akka.actor.ExtendedActorSystem
import org.bytabit.ft.util.AbstractSprayJsonSerializer
import spray.json._

class ArbitratorJsonSerializer(override val system: ExtendedActorSystem)
  extends AbstractSprayJsonSerializer[ArbitratorManager.Event](system)
    with ArbitratorJsonProtocol {

  override val identifier = hashId("ArbitratorJsonSerializer")

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case Some(clazz: Class[_]) ⇒
      bytesToString(bytes).parseJson.convertTo[ArbitratorManager.Event]
    case _ ⇒
      throw new IllegalArgumentException("No manifest found")
  }

  def toBinary(obj: AnyRef) = obj match {
    case o: ArbitratorManager.Event =>
      stringToBytes(o.toJson.toString())
    case _ =>
      throw new IllegalArgumentException("Wrong type found")
  }
}
