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

package org.bytabit.ft.server

import akka.actor.ExtendedActorSystem
import org.bytabit.ft.util.AbstractSprayJsonSerializer
import spray.json._

class ServerManagerJsonSerializer(override val system: ExtendedActorSystem)
  extends AbstractSprayJsonSerializer[ServerManager.Event](system)
    with ServerManagerJsonProtocol {

  override val identifier = hashId(this.getClass.getSimpleName)

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case Some(clazz: Class[_]) ⇒
      bytesToString(bytes).parseJson.convertTo[ServerManager.Event]
    case _ ⇒
      throw new IllegalArgumentException("No manifest found")
  }

  def toBinary(obj: AnyRef) = obj match {
    case o: ServerManager.Event =>
      stringToBytes(o.toJson.toString())
    case _ =>
      throw new IllegalArgumentException("Wrong type found")
  }
}
