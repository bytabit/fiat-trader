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
import org.bytabit.ft.trade.model.{BtcBuyOffer, Contract, SignedTakenOffer, TakenOffer, _}
import org.bytabit.ft.util.EventJsonFormat
import org.bytabit.ft.wallet.WalletJsonProtocol
import spray.json._

trait TradeJsonProtocol extends WalletJsonProtocol {

  implicit def contractJsonFormat = jsonFormat(Contract.apply, "text", "arbitrator", "fiatCurrencyUnit", "paymentMethod")

  implicit def offerJsonFormat = jsonFormat(Offer.apply, "id", "contract", "fiatAmount", "btcAmount")

  implicit def btcBuyOfferJsonFormat = jsonFormat(BtcBuyOffer.apply, "offer", "btcBuyer", "posted")

  implicit def takenOfferJsonFormat = jsonFormat(TakenOffer.apply, "btcBuyOffer", "btcSeller", "btcSellerOpenTxSigs", "btcSellerFundPayoutTxo", "cipherPaymentDetails", "paymentDetailsKey")

  implicit def signedTakenOfferJsonFormat = jsonFormat(SignedTakenOffer.apply, "takenOffer", "btcBuyerOpenTxSigs", "btcBuyerPayoutTxSigs")

  implicit def btcBuyerfiatSendJsonFormat = jsonFormat(BtcBuyerFiatSent.apply, "id", "reference", "posted")

  implicit def certifyPaymentRequestedJsonFormat = jsonFormat(CertifyPaymentRequested.apply, "id", "evidence", "posted")

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
      case JsString(CERT_PAYMENT_REQD.identifier) => CERT_PAYMENT_REQD
      case JsString(FIAT_SENT_CERTD.identifier) => FIAT_SENT_CERTD
      case JsString(FIAT_NOT_SENT_CERTD.identifier) => FIAT_NOT_SENT_CERTD

      case _ => deserializationError("TradeStatus expected")
    }

    def write(state: TradeProcess.State) = JsString(state.identifier)
  }

  // events

  implicit def btcBuyerAddedToOfferJsonFormat: RootJsonFormat[BtcBuyerAddedToOffer] = jsonFormat2(BtcBuyerAddedToOffer)

  implicit def localBtcBuyerCreatedOfferJsonFormat = jsonFormat3(LocalBtcBuyerCreatedOffer)

  implicit def btcBuyerCreatedOfferJsonFormat = jsonFormat3(BtcBuyerCreatedOffer)

  implicit def btcBuyerCanceledOfferJsonFormat = jsonFormat2(BtcBuyerCanceledOffer)

  implicit def btcSellerSetPaymentDetailsKeyJsonFormat = jsonFormat2(BtcSellerSetPaymentDetailsKey)

  implicit def btcSellerTookOfferJsonFormat = jsonFormat6(BtcSellerTookOffer)

  implicit def btcBuyerSignedOfferJsonFormat = jsonFormat5(BtcBuyerSignedOffer)

  implicit def btcSellerOpenedEscrowJsonFormat = jsonFormat3(BtcSellerOpenedEscrow)

  implicit def btcSellerFundedEscrowJsonFormat = jsonFormat4(BtcSellerFundedEscrow)

  implicit def btcBuyerReceivedPayoutJsonFormat = jsonFormat3(BtcBuyerReceivedPayout)

  implicit def btcSellerReceivedPayoutJsonFormat = jsonFormat3(BtcSellerReceivedPayout)

  implicit def btcBuyerFundedJsonFormat = jsonFormat3(BtcBuyerFunded)

  implicit def btcSellerRefundedJsonFormat = jsonFormat3(BtcSellerRefunded)

  val tradeEventJsonFormatMap: Map[String, RootJsonFormat[_ <: TradeProcess.Event]] = Map(
    simpleName(classOf[LocalBtcBuyerCreatedOffer]) -> localBtcBuyerCreatedOfferJsonFormat,
    simpleName(classOf[BtcBuyerCreatedOffer]) -> btcBuyerCreatedOfferJsonFormat,
    simpleName(classOf[BtcBuyerCanceledOffer]) -> btcBuyerCanceledOfferJsonFormat,
    simpleName(classOf[BtcSellerTookOffer]) -> btcSellerTookOfferJsonFormat,
    simpleName(classOf[BtcSellerSetPaymentDetailsKey]) -> btcSellerSetPaymentDetailsKeyJsonFormat,
    simpleName(classOf[BtcBuyerAddedToOffer]) -> btcBuyerAddedToOfferJsonFormat,
    simpleName(classOf[BtcBuyerSignedOffer]) -> btcBuyerSignedOfferJsonFormat,
    simpleName(classOf[BtcSellerOpenedEscrow]) -> btcSellerOpenedEscrowJsonFormat,
    simpleName(classOf[BtcSellerFundedEscrow]) -> btcSellerFundedEscrowJsonFormat,
    simpleName(classOf[BtcBuyerFiatSent]) -> btcBuyerfiatSendJsonFormat,
    simpleName(classOf[CertifyPaymentRequested]) -> certifyPaymentRequestedJsonFormat,
    simpleName(classOf[FiatSentCertified]) -> fiatSentCertifiedJsonFormat,
    simpleName(classOf[FiatNotSentCertified]) -> fiatNotSentCertifiedJsonFormat,
    simpleName(classOf[BtcSellerReceivedPayout]) -> btcSellerReceivedPayoutJsonFormat,
    simpleName(classOf[BtcBuyerReceivedPayout]) -> btcBuyerReceivedPayoutJsonFormat,
    simpleName(classOf[BtcSellerRefunded]) -> btcSellerRefundedJsonFormat,
    simpleName(classOf[BtcBuyerFunded]) -> btcBuyerFundedJsonFormat
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
      case JsString(BTCBUYER.identifier) => BTCBUYER
      case JsString(BTCSELLER.identifier) => BTCSELLER

      case _ => deserializationError("TradeStatus expected")
    }

    def write(role: Role) = JsString(role.identifier)
  }

}
