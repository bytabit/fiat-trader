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

package com.bytabit.ft.trade

import com.bytabit.ft.trade.TradeFSM._
import com.bytabit.ft.trade.model._
import com.bytabit.ft.wallet.WalletJsonProtocol
import spray.json._

trait TradeFSMJsonProtocol extends WalletJsonProtocol {

  implicit val contractJsonFormat = jsonFormat(Contract.apply, "text", "notary", "fiatCurrencyUnit", "fiatDeliveryMethod")

  implicit val offerJsonFormat = jsonFormat(Offer.apply, "contract", "fiatAmount", "btcAmount", "btcBond")

  implicit val sellOfferJsonFormat = jsonFormat(SellOffer.apply, "offer", "seller")

  implicit val takenOfferJsonFormat = jsonFormat(TakenOffer.apply, "sellOffer", "buyer", "buyerOpenTxSigs", "buyerFundPayoutTxo")

  implicit val signedTakenOfferJsonFormat = jsonFormat(SignedTakenOffer.apply, "takenOffer", "sellerOpenTxSigs", "sellerPayoutTxSigs")

  implicit object tradeStateJsonFormat extends JsonFormat[TradeFSM.State] {

    def read(value: JsValue) = value match {
      case JsString(PUBLISHED.identifier) => PUBLISHED
      case JsString(CANCELED.identifier) => CANCELED
      case JsString(TAKEN.identifier) => TAKEN
      case JsString(SIGNED.identifier) => SIGNED
      case JsString(OPENED.identifier) => OPENED
      case JsString(FUNDED.identifier) => FUNDED
      case JsString(BOUGHT.identifier) => BOUGHT

      case _ => deserializationError("TradeStatus expected")
    }

    def write(state: TradeFSM.State) = JsString(state.identifier)
  }

  // events

  implicit val sellerAddedToOfferJsonFormat = jsonFormat2(SellerAddedToOffer)

  implicit val localSellerCreatedOfferJsonFormat = jsonFormat3(LocalSellerCreatedOffer)

  implicit val sellerCreatedOfferJsonFormat = jsonFormat3(SellerCreatedOffer)

  implicit val sellerCanceledOfferJsonFormat = jsonFormat2(SellerCanceledOffer)

  implicit val buyerTookOfferJsonFormat = jsonFormat5(BuyerTookOffer)

  implicit val sellerSignedOfferJsonFormat = jsonFormat4(SellerSignedOffer)

  implicit val buyerOpenedEscrowJsonFormat = jsonFormat2(BuyerOpenedEscrow)

  implicit val buyerFundedEscrowJsonFormat = jsonFormat1(BuyerFundedEscrow)

  implicit val buyerSettledEscrowJsonFormat = jsonFormat1(BuyerReceivedPayout)

  implicit val tradeEventJsonFormat = new RootJsonFormat[TradeFSM.Event] {

    def read(value: JsValue): TradeFSM.Event = value.asJsObject.getFields("clazz", "event") match {
      case Seq(JsString(clazz), event) => clazz match {
        case "LocalSellerCreatedOffer" => localSellerCreatedOfferJsonFormat.read(event)
        case "SellerCreatedOffer" => sellerCreatedOfferJsonFormat.read(event)
        case "SellerCanceledOffer" => sellerCanceledOfferJsonFormat.read(event)
        case "BuyerTookOffer" => buyerTookOfferJsonFormat.read(event)
        case "SellerAddedToOffer" => sellerAddedToOfferJsonFormat.read(event)
        case "SellerSignedOffer" => sellerSignedOfferJsonFormat.read(event)
        case "BuyerOpenedEscrow" => buyerOpenedEscrowJsonFormat.read(event)
        case "BuyerFundedEscrow" => buyerFundedEscrowJsonFormat.read(event)
        case "BuyerSettledEscrow" => buyerSettledEscrowJsonFormat.read(event)

        case _ => throw new DeserializationException("TradeFSM Event expected")
      }
      case e => throw new DeserializationException("TradeFSM Event expected")
    }

    def write(evt: TradeFSM.Event) = {
      val clazz = JsString(evt.getClass.getSimpleName)
      val eventJson: JsValue = evt match {
        case lsoc: LocalSellerCreatedOffer => localSellerCreatedOfferJsonFormat.write(lsoc)
        case soc: SellerCreatedOffer => sellerCreatedOfferJsonFormat.write(soc)
        case soc: SellerCanceledOffer => sellerCanceledOfferJsonFormat.write(soc)
        case soa: BuyerTookOffer => buyerTookOfferJsonFormat.write(soa)
        case sa: SellerAddedToOffer => sellerAddedToOfferJsonFormat.write(sa)
        case sa: SellerSignedOffer => sellerSignedOfferJsonFormat.write(sa)
        case boe: BuyerOpenedEscrow => buyerOpenedEscrowJsonFormat.write(boe)
        case bse: BuyerFundedEscrow => buyerFundedEscrowJsonFormat.write(bse)
        case bse: BuyerReceivedPayout => buyerSettledEscrowJsonFormat.write(bse)

        case _ =>
          throw new SerializationException("TradeFSM Event expected")
      }
      JsObject(
        "clazz" -> clazz,
        "event" -> eventJson
      )
    }
  }

  implicit val tradePostedEventJsonFormat = new RootJsonFormat[TradeFSM.PostedEvent] {

    override def read(json: JsValue): PostedEvent =
      tradeEventJsonFormat.read(json) match {
        case pe: PostedEvent => pe
        case _ => throw new DeserializationException("TradeFSM PostedEvent expected")
      }

    override def write(obj: TradeFSM.PostedEvent): JsValue =
      tradeEventJsonFormat.write(obj)
  }
}
