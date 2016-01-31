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

package org.bytabit.ft.trade

import org.bytabit.ft.trade.TradeFSM._
import org.bytabit.ft.trade.model.{Contract, SellOffer, SignedTakenOffer, TakenOffer, _}
import org.bytabit.ft.util.EventJsonFormat
import org.bytabit.ft.wallet.WalletJsonProtocol
import spray.json._

trait TradeFSMJsonProtocol extends WalletJsonProtocol {

  implicit def contractJsonFormat = jsonFormat(Contract.apply, "text", "notary", "fiatCurrencyUnit", "fiatDeliveryMethod")

  implicit def offerJsonFormat = jsonFormat(Offer.apply, "contract", "fiatAmount", "btcAmount", "btcBond")

  implicit def sellOfferJsonFormat = jsonFormat(SellOffer.apply, "offer", "seller")

  implicit def takenOfferJsonFormat = jsonFormat(TakenOffer.apply, "sellOffer", "buyer", "buyerOpenTxSigs", "buyerFundPayoutTxo")

  implicit def signedTakenOfferJsonFormat = jsonFormat(SignedTakenOffer.apply, "takenOffer", "sellerOpenTxSigs", "sellerPayoutTxSigs")

  implicit object tradeStateJsonFormat extends JsonFormat[TradeFSM.State] {

    def read(value: JsValue) = value match {
      case JsString(CREATED.identifier) => CREATED
      case JsString(CANCELED.identifier) => CANCELED
      case JsString(TAKEN.identifier) => TAKEN
      case JsString(SIGNED.identifier) => SIGNED
      case JsString(OPENED.identifier) => OPENED
      case JsString(FUNDED.identifier) => FUNDED
      case JsString(TRADED.identifier) => TRADED

      case _ => deserializationError("TradeStatus expected")
    }

    def write(state: TradeFSM.State) = JsString(state.identifier)
  }

  // events

  implicit def sellerAddedToOfferJsonFormat: RootJsonFormat[SellerAddedToOffer] = jsonFormat2(SellerAddedToOffer)

  implicit def localSellerCreatedOfferJsonFormat = jsonFormat3(LocalSellerCreatedOffer)

  implicit def sellerCreatedOfferJsonFormat = jsonFormat3(SellerCreatedOffer)

  implicit def sellerCanceledOfferJsonFormat = jsonFormat2(SellerCanceledOffer)

  implicit def buyerTookOfferJsonFormat = jsonFormat5(BuyerTookOffer)

  implicit def sellerSignedOfferJsonFormat = jsonFormat5(SellerSignedOffer)

  implicit def buyerOpenedEscrowJsonFormat = jsonFormat2(BuyerOpenedEscrow)

  implicit def buyerFundedEscrowJsonFormat = jsonFormat1(BuyerFundedEscrow)

  implicit def buyerReceivedPayoutJsonFormat = jsonFormat1(BuyerReceivedPayout)

  val tradeEventJsonFormatMap: Map[String, RootJsonFormat[_ <: TradeFSM.Event]] = Map(
    simpleName(classOf[LocalSellerCreatedOffer]) -> localSellerCreatedOfferJsonFormat,
    simpleName(classOf[SellerCreatedOffer]) -> sellerCreatedOfferJsonFormat,
    simpleName(classOf[SellerCanceledOffer]) -> sellerCanceledOfferJsonFormat,
    simpleName(classOf[BuyerTookOffer]) -> buyerTookOfferJsonFormat,
    simpleName(classOf[SellerAddedToOffer]) -> sellerAddedToOfferJsonFormat,
    simpleName(classOf[SellerSignedOffer]) -> sellerSignedOfferJsonFormat,
    simpleName(classOf[BuyerOpenedEscrow]) -> buyerOpenedEscrowJsonFormat,
    simpleName(classOf[BuyerFundedEscrow]) -> buyerFundedEscrowJsonFormat,
    simpleName(classOf[BuyerReceivedPayout]) -> buyerReceivedPayoutJsonFormat
  )

  implicit def tradeEventJsonFormat = new EventJsonFormat[TradeFSM.Event](tradeEventJsonFormatMap)

  implicit def tradePostedEventJsonFormat = new RootJsonFormat[TradeFSM.PostedEvent] {

    override def read(json: JsValue): TradeFSM.PostedEvent =
      tradeEventJsonFormat.read(json) match {
        case pe: TradeFSM.PostedEvent => pe
        case _ => throw new DeserializationException("TradeFSM PostedEvent expected")
      }

    override def write(obj: TradeFSM.PostedEvent): JsValue =
      tradeEventJsonFormat.write(obj)
  }
}
