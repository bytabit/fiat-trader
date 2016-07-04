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

package org.bytabit.ft.fxui.util

import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.control.{Button, TableCell}

import org.bytabit.ft.fxui.trade.TradeUIActionTableCell.TradeOriginState
import org.bytabit.ft.fxui.trade.TradeUIModel

trait ActionTableCell extends TableCell[TradeUIModel, TradeOriginState] {

  def actionButton(text: String, handler: ActionEvent => Unit): Button = {
    val actionButton: Button = new Button
    actionButton.setText(text)
    actionButton.setOnAction(new EventHandler[ActionEvent] {
      override def handle(event: ActionEvent): Unit = handler(event)
    })
    actionButton
  }
}
