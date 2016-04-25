//package org.bytabit.ft.client
//
//import java.net.URL
//
//import akka.actor.{ActorContext, ActorRef}
//import akka.event.LoggingAdapter
//import akka.http.scaladsl.Http
//import akka.http.scaladsl.marshalling.Marshal
//import akka.http.scaladsl.model.{ContentTypes, _}
//import akka.http.scaladsl.unmarshalling.Unmarshal
//import akka.stream.scaladsl.{Flow, Sink, Source}
//import org.bytabit.ft.arbitrator.ArbitratorManager
//import org.bytabit.ft.client.EventClient.{ReceivePostedArbitratorEvent, ReceivePostedTradeEvent, ServerOffline, ServerOnline}
//import org.bytabit.ft.server.PostedEvents
//import org.bytabit.ft.trade.{TradeProcess, TradeJsonProtocol}
//import org.joda.time.DateTime
//
//import scala.concurrent.Future
//import scala.util.{Failure, Success}
//
//trait EventClientHttpProtocol extends TradeJsonProtocol {
//
//  val log: LoggingAdapter
//
//  import spray.json._
//
//  // http flow
//
//  def connectionFlow(url: URL): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
//    Http().outgoingConnection(host = url.getHost, port = url.getPort)
//
//  // http get posted events requester and handler
//
//  def reqPostedEvents(url: URL, since: Option[DateTime])(implicit context: ActorContext): Unit = {
//
//    val query = since match {
//      case Some(dt) => s"?since=${dt.toString}"
//      case None => ""
//    }
//
//    val eventsUri = s"/events$query"
//
//    val req = Source.single(HttpRequest(uri = eventsUri, method = HttpMethods.GET))
//      .via(connectionFlow(url))
//
//    req.runWith(Sink.head).onComplete {
//
//      case Success(HttpResponse(StatusCodes.OK, headers, entity, protocol)) =>
//        log.debug(s"Response from ${url.toString} $eventsUri OK")
//        Unmarshal(entity).to[PostedEvents].onSuccess {
//          case PostedEvents(aes, tes) =>
//            context.self ! ServerOnline(url)
//            aes.foreach(context.self ! ReceivePostedArbitratorEvent(_))
//            tes.foreach(context.self ! ReceivePostedTradeEvent(_))
//          case _ =>
//            log.error("No arbitrator events in response.")
//        }
//
//      case Success(HttpResponse(StatusCodes.NoContent, headers, entity, protocol)) =>
//        log.debug(s"No new events from ${url.toString}$eventsUri")
//        context.self ! ServerOnline(url)
//
//      case Success(HttpResponse(sc, headers, entity, protocol)) =>
//        log.error(s"Response from ${url.toString}$eventsUri ${sc.toString()}")
//
//      case Failure(failure) =>
//        log.debug(s"No Response from ${url.toString}: $failure")
//        context.self ! ServerOffline(url)
//    }
//  }
//
//  def postTradeEvent(url: URL, postedEvent: TradeProcess.PostedEvent, self: ActorRef): Unit = {
//
//    val tradeUri = s"/trade"
//
//    Marshal(postedEvent.toJson).to[RequestEntity].onSuccess {
//
//      case reqEntity =>
//
//        val req = Source.single(HttpRequest(uri = tradeUri, method = HttpMethods.POST,
//          entity = reqEntity.withContentType(ContentTypes.`application/json`)))
//          .via(connectionFlow(url))
//
//        req.runWith(Sink.head).onComplete {
//
//          case Success(HttpResponse(StatusCodes.OK, headers, respEntity, protocol)) =>
//            log.debug(s"Response from ${url.toString}$tradeUri OK, $respEntity")
//            Unmarshal(respEntity).to[TradeProcess.PostedEvent].onSuccess {
//              case pe: TradeProcess.PostedEvent if pe.posted.isDefined =>
//                self ! pe
//              case _ =>
//                log.error("No posted event in response.")
//            }
//
//          case Success(HttpResponse(sc, h, e, p)) =>
//            log.error(s"Response from ${url.toString}$tradeUri ${sc.toString()}")
//
//          case Failure(failure) =>
//            log.debug(s"No Response from ${url.toString}: $failure")
//        }
//    }
//  }
//
//  def postArbitratorEvent(url: URL, postedEvent: ArbitratorManager.PostedEvent, self: ActorRef): Unit = {
//
//    val tradeUri = s"/arbitrator"
//
//    Marshal(postedEvent.toJson).to[RequestEntity].onSuccess {
//
//      case reqEntity =>
//
//        val req = Source.single(HttpRequest(uri = tradeUri, method = HttpMethods.POST,
//          entity = reqEntity.withContentType(ContentTypes.`application/json`)))
//          .via(connectionFlow(url))
//
//        req.runWith(Sink.head).onComplete {
//
//          case Success(HttpResponse(StatusCodes.OK, headers, respEntity, protocol)) =>
//            log.debug(s"Response from ${url.toString}$tradeUri OK, $respEntity")
//            Unmarshal(respEntity).to[TradeProcess.PostedEvent].onSuccess {
//              case pe: TradeProcess.PostedEvent if pe.posted.isDefined =>
//                self ! pe
//              case _ =>
//                log.error("No posted event in response.")
//            }
//
//          case Success(HttpResponse(sc, h, e, p)) =>
//            log.error(s"Response from ${url.toString}$tradeUri ${sc.toString()}")
//
//          case Failure(failure) =>
//            log.debug(s"No Response from ${url.toString}: $failure")
//        }
//    }
//  }
//}
