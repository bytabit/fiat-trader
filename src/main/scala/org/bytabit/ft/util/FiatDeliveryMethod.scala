package org.bytabit.ft.util

import org.joda.money.CurrencyUnit

object FiatDeliveryMethod {

  val swish: FiatDeliveryMethod =
    FiatDeliveryMethod("Swish", Seq(CurrencyUnits.SEK), Seq("Swish Phone Number"))

  val skrill: FiatDeliveryMethod =
    FiatDeliveryMethod("Skrill", Seq(CurrencyUnits.USD, CurrencyUnits.EUR), Seq("Skrill Email Address"))

  val all = Seq(swish, skrill)

  def forCurrencyUnit(cu: CurrencyUnit) = all.filter(_.currencyUnits.contains(cu))

  def getInstance(name:String):Option[FiatDeliveryMethod] = all.find(fdm => fdm.name == name)
}

case class FiatDeliveryMethod(name: String, currencyUnits: Seq[CurrencyUnit],
                              requiredDetails: Seq[String]) {

  currencyUnits.foreach(cu => assert(Monies.isFiat(cu)))
}
