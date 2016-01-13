package org.bytabit.ft.trade.model

trait Template {

  val text: String
  val keyValues: Map[String, Option[String]]

  override lazy val toString: String = replaceAll(text, keyValues)

  def replaceAll(text: String, keyValues: Map[String, Option[String]]): String = {

    keyValues.foldLeft(text) { (t, kv) =>
      val regex = "\\$" + kv._1
      kv._2 match {
        case Some(s: String) => t.replaceAll(regex, s)
        case None => t.replaceAll(regex, "<NONE>")
      }
    }
  }
}
