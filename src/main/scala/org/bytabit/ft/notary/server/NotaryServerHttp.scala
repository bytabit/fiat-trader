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

package org.bytabit.ft.notary.server

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.bytabit.ft.notary.NotaryClientFSMJsonProtocol
import org.bytabit.ft.trade.TradeFSM
import org.joda.time.DateTime

import scala.concurrent.Future

trait NotaryServerHttp extends NotaryClientFSMJsonProtocol {

  implicit val system: ActorSystem

  implicit val materializer: ActorMaterializer

  val bindingFuture: Future[Http.ServerBinding]

  val log: LoggingAdapter

  def getPostedEvents(since: Option[DateTime]): PostedEvents

  def postTradeEvent(tradeEvent: TradeFSM.PostedEvent): Future[TradeFSM.PostedEvent]

  def binding(localAddress: String, localPort: Int) = Http().bindAndHandle(route, localAddress, localPort)

  def dateParam(dateParam: Option[String]): Option[DateTime] = dateParam match {
    case Some(dateTimeStr) => try {
      Some(DateTime.parse(dateTimeStr))
    } catch {
      case e: IllegalArgumentException =>
        None
    }
    case None =>
      None
  }

  val route = {
    pathPrefix("notary") {
      pathEnd {
        get {
          parameter("since".?) { sinceParam =>
            val since = dateParam(sinceParam)
            complete {
              val pae = getPostedEvents(since)
              if (pae.notaryEvents.nonEmpty || pae.tradeEvents.nonEmpty) pae
              else HttpResponse(StatusCodes.NoContent)
            }
          }
        }
      }
    } ~
      path("trade") {
        post {
          entity(as[TradeFSM.PostedEvent]) { te =>
            complete {
              postTradeEvent(te)
            }
          }
        }
      }
  }
}
