package org.bytabit.ft.util

import spray.json._

class EventJsonFormat[E](eventJsonFormatMap: Map[String, RootJsonFormat[_ <: E]]) extends RootJsonFormat[E] {

  override def read(json: JsValue): E = {
    json.asJsObject.getFields("clazz", "event") match {
      case Seq(JsString(clazz), event) =>
        eventJsonFormatMap.get(clazz) match {
          case Some(format) =>
            format.read(event)
          case _ =>
            throw new DeserializationException(s"No Event json format found for: $clazz")
        }
      case _ =>
        throw new DeserializationException("Event class name and event data expected")
    }
  }

  override def write(event: E): JsValue = {
    val clazz = event.getClass.getSimpleName
    val eventJson: JsValue = eventJsonFormatMap.get(clazz) match {
      case Some(format) =>
        format.asInstanceOf[RootJsonFormat[E]].write(event)
      case _ =>
        throw new DeserializationException(s"No Event json format found for: $clazz")
    }
    JsObject(
      "clazz" -> JsString(clazz),
      "event" -> eventJson
    )
  }
}
