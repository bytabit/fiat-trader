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
import javafx.event.{ActionEvent, EventHandler}
import javafx.geometry.Pos
import javafx.scene.control.{Button, TableCell}
import javafx.scene.layout.VBox

import org.bytabit.ft.fxui.TradeFxService
import org.bytabit.ft.fxui.model.TradeUIActionTableCell.TradeOriginState
import org.bytabit.ft.fxui.model.TradeUIModel.{BUYER, Origin, SELLER}
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.TradeFSM.{FUNDED, PUBLISHED}

import scala.collection.JavaConversions._

object TradeUIActionTableCell {

  case class TradeOriginState(url: URL, id: UUID, origin: Origin, state: TradeFSM.State)

}

class TradeUIActionTableCell(tradefxService: TradeFxService) extends TableCell[TradeUIModel, TradeOriginState]() {

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

    // valid action buttons for item

    val buttons: Seq[Button] = (item, empty) match {
      case (TradeOriginState(u, i, SELLER, PUBLISHED), false) =>
        Seq(cancelButton)
      case (TradeOriginState(u, i, BUYER, PUBLISHED), false) =>
        Seq(buyButton)
      case (TradeOriginState(u, i, BUYER, FUNDED), false) =>
        Seq(fiatReceivedButton)
      case _ =>
        setText(null)
        setStyle("")
        setGraphic(null)
        Seq()
    }

    vbox.getChildren.setAll(buttons)
    setGraphic(vbox)
  }

  def actionButton(text: String, handler: ActionEvent => Unit): Button = {
    val actionButton: Button = new Button
    actionButton.setText(text)
    actionButton.setOnAction(new EventHandler[ActionEvent] {
      override def handle(event: ActionEvent): Unit = handler(event)
    })
    actionButton
  }
}
