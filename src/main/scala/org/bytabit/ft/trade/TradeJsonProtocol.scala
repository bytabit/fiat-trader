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

import org.bytabit.ft.trade.TradeProcess._
import org.bytabit.ft.trade.model.{Contract, SellOffer, SignedTakenOffer, TakenOffer, _}
import org.bytabit.ft.util.EventJsonFormat
import org.bytabit.ft.wallet.WalletJsonProtocol
import spray.json._

trait TradeJsonProtocol extends WalletJsonProtocol {

  implicit def contractJsonFormat = jsonFormat(Contract.apply, "text", "arbitrator", "fiatCurrencyUnit", "fiatDeliveryMethod")

  implicit def offerJsonFormat = jsonFormat(Offer.apply, "id", "contract", "fiatAmount", "btcAmount")

  implicit def sellOfferJsonFormat = jsonFormat(SellOffer.apply, "offer", "seller")

  implicit def takenOfferJsonFormat = jsonFormat(TakenOffer.apply, "sellOffer", "buyer", "buyerOpenTxSigs", "buyerFundPayoutTxo", "cipherFiatDeliveryDetails", "fiatDeliveryDetailsKey")

  implicit def signedTakenOfferJsonFormat = jsonFormat(SignedTakenOffer.apply, "takenOffer", "sellerOpenTxSigs", "sellerPayoutTxSigs")

  implicit def certifyDeliveryRequestedJsonFormat = jsonFormat(CertifyDeliveryRequested.apply, "id", "evidence", "posted")

  implicit def fiatSentCertifiedJsonFormat = jsonFormat(FiatSentCertified.apply, "id", "payoutSigs", "posted")

  implicit def fiatNotSentCertifiedJsonFormat = jsonFormat(FiatNotSentCertified.apply, "id", "payoutSigs", "posted")

  implicit object tradeStateJsonFormat extends JsonFormat[TradeProcess.State] {

    def read(value: JsValue) = value match {
      case JsString(CREATED.identifier) => CREATED
      case JsString(CANCELED.identifier) => CANCELED
      case JsString(TAKEN.identifier) => TAKEN
      case JsString(SIGNED.identifier) => SIGNED
      case JsString(OPENED.identifier) => OPENED
      case JsString(FUNDED.identifier) => FUNDED
      case JsString(FIAT_RCVD.identifier) => FIAT_RCVD
      case JsString(FIAT_SENT.identifier) => FIAT_SENT
      case JsString(TRADED.identifier) => TRADED
      case JsString(CERT_DELIVERY_REQD.identifier) => CERT_DELIVERY_REQD
      case JsString(FIAT_SENT_CERTD.identifier) => FIAT_SENT_CERTD
      case JsString(FIAT_NOT_SENT_CERTD.identifier) => FIAT_NOT_SENT_CERTD

      case _ => deserializationError("TradeStatus expected")
    }

    def write(state: TradeProcess.State) = JsString(state.identifier)
  }

  // events

  implicit def sellerAddedToOfferJsonFormat: RootJsonFormat[SellerAddedToOffer] = jsonFormat2(SellerAddedToOffer)

  implicit def localSellerCreatedOfferJsonFormat = jsonFormat3(LocalSellerCreatedOffer)

  implicit def sellerCreatedOfferJsonFormat = jsonFormat3(SellerCreatedOffer)

  implicit def sellerCanceledOfferJsonFormat = jsonFormat2(SellerCanceledOffer)

  implicit def buyerSetFiatDeliveryDetailsKeyJsonFormat = jsonFormat2(BuyerSetFiatDeliveryDetailsKey)

  implicit def buyerTookOfferJsonFormat = jsonFormat6(BuyerTookOffer)

  implicit def sellerSignedOfferJsonFormat = jsonFormat5(SellerSignedOffer)

  implicit def buyerOpenedEscrowJsonFormat = jsonFormat3(BuyerOpenedEscrow)

  implicit def buyerFundedEscrowJsonFormat = jsonFormat4(BuyerFundedEscrow)

  implicit def sellerReceivedPayoutJsonFormat = jsonFormat3(SellerReceivedPayout)

  implicit def buyerReceivedPayoutJsonFormat = jsonFormat3(BuyerReceivedPayout)

  implicit def sellerFundedJsonFormat = jsonFormat3(SellerFunded)

  implicit def buyerRefundedJsonFormat = jsonFormat3(BuyerRefunded)

  val tradeEventJsonFormatMap: Map[String, RootJsonFormat[_ <: TradeProcess.Event]] = Map(
    simpleName(classOf[LocalSellerCreatedOffer]) -> localSellerCreatedOfferJsonFormat,
    simpleName(classOf[SellerCreatedOffer]) -> sellerCreatedOfferJsonFormat,
    simpleName(classOf[SellerCanceledOffer]) -> sellerCanceledOfferJsonFormat,
    simpleName(classOf[BuyerTookOffer]) -> buyerTookOfferJsonFormat,
    simpleName(classOf[BuyerSetFiatDeliveryDetailsKey]) -> buyerSetFiatDeliveryDetailsKeyJsonFormat,
    simpleName(classOf[SellerAddedToOffer]) -> sellerAddedToOfferJsonFormat,
    simpleName(classOf[SellerSignedOffer]) -> sellerSignedOfferJsonFormat,
    simpleName(classOf[BuyerOpenedEscrow]) -> buyerOpenedEscrowJsonFormat,
    simpleName(classOf[BuyerFundedEscrow]) -> buyerFundedEscrowJsonFormat,
    simpleName(classOf[CertifyDeliveryRequested]) -> certifyDeliveryRequestedJsonFormat,
    simpleName(classOf[FiatSentCertified]) -> fiatSentCertifiedJsonFormat,
    simpleName(classOf[FiatNotSentCertified]) -> fiatNotSentCertifiedJsonFormat,
    simpleName(classOf[BuyerReceivedPayout]) -> buyerReceivedPayoutJsonFormat,
    simpleName(classOf[SellerReceivedPayout]) -> sellerReceivedPayoutJsonFormat,
    simpleName(classOf[BuyerRefunded]) -> buyerRefundedJsonFormat,
    simpleName(classOf[SellerFunded]) -> sellerFundedJsonFormat
  )

  implicit def tradeEventJsonFormat = new EventJsonFormat[TradeProcess.Event](tradeEventJsonFormatMap)

  implicit def tradePostedEventJsonFormat = new RootJsonFormat[TradeProcess.PostedEvent] {

    override def read(json: JsValue): TradeProcess.PostedEvent =
      tradeEventJsonFormat.read(json) match {
        case pe: TradeProcess.PostedEvent => pe
        case _ => throw new DeserializationException("TradeFSM PostedEvent expected")
      }

    override def write(obj: TradeProcess.PostedEvent): JsValue =
      tradeEventJsonFormat.write(obj)
  }

  implicit object roleJsonFormat extends JsonFormat[Role] {

    def read(value: JsValue) = value match {
      case JsString(ARBITRATOR.identifier) => ARBITRATOR
      case JsString(SELLER.identifier) => SELLER
      case JsString(BUYER.identifier) => BUYER

      case _ => deserializationError("TradeStatus expected")
    }

    def write(role: Role) = JsString(role.identifier)
  }

}
