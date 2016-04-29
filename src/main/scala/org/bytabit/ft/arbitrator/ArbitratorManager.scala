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

import java.net.URL

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.arbitrator.ArbitratorManager._
import org.bytabit.ft.trade.model._
import org.bytabit.ft.util._
import org.bytabit.ft.wallet.model._
import org.joda.money.CurrencyUnit
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect._
import scala.util.{Failure, Success}

object ArbitratorManager {

  // actor setup

  def props(arbitrator: Arbitrator) = Props(new ArbitratorManager(arbitrator))

  def name(arbitrator: Arbitrator) = s"${ArbitratorManager.getClass.getSimpleName}-${arbitrator.escrowPubKey.toString}"

  // commands

  sealed trait Command {
    val url: URL
  }

  case class Start(url: URL) extends Command

  final case class AddContractTemplate(url: URL, currencyUnit: CurrencyUnit, deliveryMethod: FiatDeliveryMethod) extends Command {
    assert(Monies.isFiat(currencyUnit))
  }

  final case class RemoveContractTemplate(url: URL, id: Sha256Hash) extends Command

  // events

  sealed trait Event {
    val url: URL
  }

  sealed trait PostedEvent extends Event with Posted

  final case class ArbitratorCreated(url: URL, arbitrator: Arbitrator,
                                     posted: Option[DateTime] = None) extends PostedEvent

  final case class ContractAdded(url: URL, contract: Contract,
                                 posted: Option[DateTime] = None) extends PostedEvent

  final case class ContractRemoved(url: URL, id: Sha256Hash,
                                   posted: Option[DateTime] = None) extends PostedEvent

  // states

  sealed trait State extends FSMState

  case object ADDED extends State {
    override val identifier: String = "ADDED"
  }

  case object CREATED extends State {
    override val identifier: String = "CREATED"
  }

  // data

  case class Data(arbitrator: Arbitrator, contracts: Map[Sha256Hash, Contract] = Map()) {

    def withContract(contract: Contract) =
      this.copy(contracts = contracts + (contract.id -> contract))

    def withoutContract(id: Sha256Hash) =
      this.copy(contracts = contracts - id)
  }

}

class ArbitratorManager(arbitrator: Arbitrator) extends PersistentFSM[ArbitratorManager.State, ArbitratorManager.Data, ArbitratorManager.Event] with ArbitratorJsonProtocol {

  import spray.json._

  // implicits

  implicit val system = context.system

  implicit def executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  // persistence

  override def persistenceId: String = s"${ArbitratorManager.name(arbitrator)}-persister"

  override def domainEventClassTag: ClassTag[ArbitratorManager.Event] = classTag[ArbitratorManager.Event]

  // apply events to arbitrator manager data

  def applyEvent(event: ArbitratorManager.Event, data: ArbitratorManager.Data): ArbitratorManager.Data =

    (event, data) match {

      case (ContractAdded(_, c, Some(_)), data: Data) =>
        data.withContract(c)

      case (ContractRemoved(_, i, Some(_)), data: Data) =>
        data.withoutContract(i)

      // error

      case _ =>
        log.error(s"No transition for event: $event\nwith arbitrator manager data: ${data.getClass.getSimpleName}")
        data
    }

  startWith(ADDED, Data(arbitrator))

  when(ADDED, stateTimeout = 30 second) {

    case Event(Start | StateTimeout, Data(a, cm)) =>
      postArbitratorEvent(a.url, ArbitratorManager.ArbitratorCreated(a.url, a), self)
      stay()

    case Event(ac: ArbitratorManager.ArbitratorCreated, Data(a, cm)) if ac.posted.isDefined =>
      goto(CREATED) andThen { ud =>
        context.parent ! ac
      }
  }

  when(CREATED) {

    case Event(Start, Data(a, cm)) =>

      // notify parent of created arbitrator
      context.parent ! ArbitratorManager.ArbitratorCreated(a.url, a)

      // notify parent of arbitrator contracts
      cm.values.foreach(c => context.parent ! ContractAdded(a.url, c))
      stay()

    case Event(AddContractTemplate(_, cu, dm), Data(a, cm)) =>
      val c = Contract(a, cu, dm)
      if (!cm.contains(c.id)) postArbitratorEvent(a.url, ContractAdded(a.url, c), self)
      else log.error(s"Can't add duplicate contract: ${c.id}")
      stay()

    case Event(ca: ContractAdded, Data(a, cm)) if ca.posted.isDefined =>
      stay() applying ca andThen { ud =>
        context.parent ! ca
      }

    case Event(RemoveContractTemplate(_, id), Data(a, cm)) =>
      postArbitratorEvent(a.url, ContractRemoved(a.url, id), self)
      stay()

    case Event(cr: ContractRemoved, Data(a, cm)) if cr.posted.isDefined =>
      stay() applying cr andThen { ud =>
        context.parent ! cr
      }
  }

  // http flow

  def connectionFlow(url: URL) = Http().outgoingConnection(host = url.getHost, port = url.getPort)

  // http request and handler

  def postArbitratorEvent(url: URL, postedEvent: ArbitratorManager.PostedEvent, self: ActorRef): Unit = {

    val tradeUri = s"/arbitrator"

    Marshal(postedEvent.toJson).to[RequestEntity].onSuccess {

      case reqEntity =>

        val req = Source.single(HttpRequest(uri = tradeUri, method = HttpMethods.POST,
          entity = reqEntity.withContentType(ContentTypes.`application/json`)))
          .via(connectionFlow(url))

        req.runWith(Sink.head).onComplete {

          case Success(HttpResponse(StatusCodes.OK, headers, respEntity, protocol)) =>
            log.debug(s"Response from ${url.toString}$tradeUri OK, $respEntity")
            Unmarshal(respEntity).to[ArbitratorManager.PostedEvent].onSuccess {
              case pe: ArbitratorManager.PostedEvent if pe.posted.isDefined =>
                self ! pe
              case _ =>
                log.error("No posted event in response.")
            }

          case Success(HttpResponse(sc, h, e, p)) =>
            log.error(s"Response from ${url.toString}$tradeUri ${sc.toString()}")

          case Failure(failure) =>
            log.debug(s"No Response from ${url.toString}: $failure")
        }
    }
  }
}
