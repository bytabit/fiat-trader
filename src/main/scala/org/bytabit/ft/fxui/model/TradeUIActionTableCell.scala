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

package org.bytabit.ft.fxui.model

import java.net.URL
import java.util.UUID
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.layout.VBox

import org.bytabit.ft.fxui.TraderTradeFxService
import org.bytabit.ft.fxui.model.TradeUIActionTableCell.TradeOriginState
import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, Role, SELLER}
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.TradeFSM.{CREATED, FIAT_SENT, FUNDED}

import scala.collection.JavaConversions._

object TradeUIActionTableCell {

  case class TradeOriginState(url: URL, id: UUID, role: Role, state: TradeFSM.State)

}

class TradeUIActionTableCell(tradefxService: TraderTradeFxService) extends ActionTableCell {

  protected override def updateItem(item: TradeOriginState, empty: Boolean) {
    super.updateItem(item, empty)

    // button container

    val vbox: VBox = new VBox
    vbox.alignmentProperty.setValue(Pos.CENTER)

    // possible buttons

    val cancelButton = actionButton("CANCEL", event => {
      tradefxService.cancelSellOffer(item.url, item.id)
    })

    val buyButton = actionButton("BUY", event => {
      tradefxService.takeSellOffer(item.url, item.id)
    })

    val fiatReceivedButton = actionButton("FIAT RCVD", event => {
      tradefxService.receiveFiat(item.url, item.id)
    })

    val fiatSentButton = actionButton("FIAT SENT", event => {
      tradefxService.sendFiat(item.url, item.id)
    })

    // TODO FT-98: only enable buttons after timeout to deliver fiat
    val sellerReqCertDeliveryButton = actionButton("REQ CERT", event => {
      tradefxService.sellerReqCertDelivery(item.url, item.id)
    })

    val buyerReqCertDeliveryButton = actionButton("REQ CERT", event => {
      tradefxService.buyerReqCertDelivery(item.url, item.id)
    })

    // valid action buttons for item

    val buttons: Seq[Button] = (item, empty) match {
      case (TradeOriginState(u, i, SELLER, CREATED), false) =>
        Seq(cancelButton)
      case (TradeOriginState(u, i, BUYER, CREATED), false) =>
        Seq(buyButton)
      case (TradeOriginState(u, i, BUYER, FUNDED), false) =>
        Seq(fiatReceivedButton, buyerReqCertDeliveryButton)
      case (TradeOriginState(u, i, SELLER, FUNDED), false) =>
        Seq(fiatSentButton)
      case (TradeOriginState(u, i, SELLER, FIAT_SENT), false) =>
        Seq(sellerReqCertDeliveryButton)
      case _ =>
        setText(null)
        setStyle("")
        setGraphic(null)
        Seq()
    }

    vbox.getChildren.setAll(buttons)
    setGraphic(vbox)
  }
}
