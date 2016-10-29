/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.bytabit.ft.fxui.views

import java.lang.Boolean
import javafx.animation.Transition
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.event.{ActionEvent, EventHandler}
import javafx.fxml.FXML
import javafx.scene.control.MenuItem

import com.gluonhq.charm.glisten.animation.FadeInLeftBigTransition
import com.gluonhq.charm.glisten.application.MobileApplication
import com.gluonhq.charm.glisten.layout.layer.FloatingActionButton
import com.gluonhq.charm.glisten.mvc.View
import com.gluonhq.charm.glisten.visual.{MaterialDesignIcon, Swatch}
import org.bytabit.ft.fxui.FiatTrader

//import org.bytabit.ft.util.JavaLogging

import scala.compat.java8.FunctionConverters._

class TradePresenter {
  //extends JavaLogging {

  @FXML
  var tradeView: View = null

  @FXML
  def initialize() {
    val fab = new FloatingActionButton()
    fab.setOnAction(new EventHandler[ActionEvent]() {
      override def handle(e: ActionEvent): Unit = {
        MobileApplication.getInstance().switchView(FiatTrader.WALLET_VIEW)
      }
    })

    tradeView.getLayers.add(fab)

    val viewToTransition: View => Transition = v => new FadeInLeftBigTransition(v)
    tradeView.setShowTransitionFactory(viewToTransition.asJava)

    tradeView.showingProperty().addListener(new ChangeListener[Boolean] {
      override def changed(ob: ObservableValue[_ <: Boolean], ov: Boolean, nv: Boolean): Unit = {
        if (nv) {
          val appBar = MobileApplication.getInstance().getAppBar
          appBar.setNavIcon(MaterialDesignIcon.MENU.button(new EventHandler[ActionEvent] {
            override def handle(event: ActionEvent): Unit = {
              //log.info("menu")
            }
          }))
          appBar.setTitleText("The Trade View")
          appBar.getActionItems.add(MaterialDesignIcon.SEARCH.button())
          appBar.getMenuItems.addAll(new MenuItem("Settings"))

          Swatch.ORANGE.assignTo(tradeView.getScene)
        }
      }
    })
  }

  @FXML
  def onClick() {
    //log.info("click")
  }
}