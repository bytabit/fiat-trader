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

import org.bytabit.ft.fxui.NotaryTradeFxService
import org.bytabit.ft.fxui.model.TradeUIActionTableCell.TradeOriginState
import org.bytabit.ft.fxui.model.TradeUIModel.{NOTARY, Role}
import org.bytabit.ft.trade.TradeFSM
import org.bytabit.ft.trade.TradeFSM.CERT_DELIVERY_REQD

import scala.collection.JavaConversions._

object NotarizeUIActionTableCell {

  case class TradeOriginState(url: URL, id: UUID, role: Role, state: TradeFSM.State)

}

class NotarizeUIActionTableCell(tradefxService: NotaryTradeFxService) extends ActionTableCell {

  protected override def updateItem(item: TradeOriginState, empty: Boolean) {
    super.updateItem(item, empty)

    // button container

    val vbox: VBox = new VBox
    vbox.alignmentProperty.setValue(Pos.CENTER)

    // possible buttons

    val certifySentButton = actionButton("SENT", event => {
      tradefxService.certifyFiatSent(item.url, item.id)
    })

    val certifyNotSentButton = actionButton("NOT SENT", event => {
      tradefxService.certifyFiatNotSent(item.url, item.id)
    })

    // valid action buttons for item

    val buttons: Seq[Button] = (item, empty) match {
      case (TradeOriginState(u, i, NOTARY, CERT_DELIVERY_REQD), false) =>
        Seq(certifySentButton, certifyNotSentButton)

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
