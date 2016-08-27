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
package org.bytabit.ft.fxui.trade

import java.net.URL
import java.util.UUID
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.layout.VBox

import org.bytabit.ft.fxui.trade.TraderUIActionTableCell.TradeOriginState
import org.bytabit.ft.fxui.util.TradeActionTableCell
import org.bytabit.ft.trade.TradeProcess
import org.bytabit.ft.trade.TradeProcess.{CREATED, FIAT_SENT, FUNDED}
import org.bytabit.ft.trade.model._

import scala.collection.JavaConversions._

object TraderUIActionTableCell {

  case class TradeOriginState(url: URL, id: UUID, role: Role, state: TradeProcess.State, trade: TradeData)

}

class TraderUIActionTableCell(tradefxService: TradeFxService) extends TradeActionTableCell {

  protected override def updateItem(item: TradeOriginState, empty: Boolean) {
    super.updateItem(item, empty)

    // button container

    val vbox: VBox = new VBox
    vbox.alignmentProperty.setValue(Pos.CENTER)

    // possible buttons

    val cancelButton = actionButton("CANCEL", event => {
      tradefxService.cancelBtcBuyOffer(item.url, item.id)
    })

    val btcSellButton = actionButton("SELL", event => {
      tradefxService.takeBtcBuyOffer(item.url, item.id)
    })

    val fiatReceivedButton = actionButton("RCV FIAT", event => {
      tradefxService.receiveFiatDialog(item.url, item.id, item.trade)
    })

    val sendFiatDialog = actionButton("SEND FIAT", event => {
      tradefxService.sendFiatDialog(item.url, item.id, item.trade)
    })

    // TODO FT-98: only enable buttons after timeout to deliver fiat
    val btcBuyerReqCertPaymentButton = actionButton("REQ CERT", event => {
      tradefxService.btcBuyerReqCertPayment(item.url, item.id)
    })

    val btcSellerReqCertPaymentButton = actionButton("REQ CERT", event => {
      tradefxService.btcSellerReqCertPayment(item.url, item.id)
    })

    // valid action buttons for item

    val buttons: Seq[Button] = (item, empty) match {
      case (TradeOriginState(u, i, BTCBUYER, CREATED, _), false) =>
        Seq(cancelButton)
      case (TradeOriginState(u, i, BTCSELLER, CREATED, _), false) =>
        Seq(btcSellButton)
      case (TradeOriginState(u, i, BTCSELLER, FUNDED, _), false) =>
        Seq(fiatReceivedButton, btcSellerReqCertPaymentButton)
      case (TradeOriginState(u, i, BTCBUYER, FUNDED, _), false) =>
        Seq(sendFiatDialog)
      case (TradeOriginState(u, i, BTCBUYER, FIAT_SENT, _), false) =>
        Seq(btcBuyerReqCertPaymentButton)
      case (TradeOriginState(u, i, BTCSELLER, FIAT_SENT, _), false) =>
        Seq(fiatReceivedButton, btcSellerReqCertPaymentButton)
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
