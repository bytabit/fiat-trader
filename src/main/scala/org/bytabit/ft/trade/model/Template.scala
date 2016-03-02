package org.bytabit.ft.trade.model

import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.util.Monies
import org.bytabit.ft.wallet.model.{Buyer, Notary, Seller}
import org.joda.money.{CurrencyUnit, Money}

trait Template {

  val text: String
  val keyValues: Map[String, Option[String]]

  override lazy val toString: String = replaceAll(text, keyValues)

  def contractKeyValues(id: Sha256Hash, fiatCurrencyUnit: CurrencyUnit, fiatDeliveryMethod: String,
                        notary: Notary, btcNetworkName: String) =
    Map[String, Option[String]](
      "contractId" -> Some(id.toString),
      "fiatCurrencyUnit" -> Some(fiatCurrencyUnit.toString),
      "fiatDeliveryMethod" -> Some(fiatDeliveryMethod),

      "notaryId" -> Some(notary.id.toString),
      "btcNetworkName" -> Some(btcNetworkName),
      "notaryFeeAddress" -> Some(notary.feesAddr.toString),
      "notaryURL" -> Some(notary.url.toString),
      "bondPercent" -> Some(notary.bondPercent.toString),
      "btcNotaryFee" -> Some(notary.btcNotaryFee.toString)
    )

  def amountKeyValues(fiatAmount: Money, btcAmount: Money, bondPercent: Double) =
    Map[String, Option[String]](
      "fiatAmount" -> Some(fiatAmount.toString),
      "btcAmount" -> Some(btcAmount.toString),
      "btcBondPercent" -> Some(bondPercent.toString),
      "btcBondAmount" -> Some(btcAmount.multipliedBy(bondPercent, Monies.roundingMode).toString)
    )

  def sellerKeyValues(seller: Seller) =
    Map[String, Option[String]](
      "sellerId" -> Some(seller.id.toString),
      "sellerPayoutAddress" -> Some(seller.payoutAddr.toString)
    )

  def buyerKeyValues(buyer: Buyer) =
    Map[String, Option[String]](
      "buyerId" -> Some(buyer.id.toString),
      "buyerPayoutAddress" -> Some(buyer.payoutAddr.toString)
    )

  def fiatDeliveryDetailsKeyValues(fiatDeliveryDetails: Option[String]) =
    Map[String, Option[String]](
      "buyerFiatDeliveryDetails" -> fiatDeliveryDetails
    )

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
