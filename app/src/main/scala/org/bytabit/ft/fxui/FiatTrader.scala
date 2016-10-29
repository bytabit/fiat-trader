/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.bytabit.ft.fxui

import javafx.scene.Scene
import javafx.scene.control.CheckBox

import akka.actor.ActorSystem
import com.gluonhq.charm.glisten.application.MobileApplication
import com.gluonhq.charm.glisten.mvc.View
import com.gluonhq.charm.glisten.visual.Swatch
import org.bytabit.ft.client.ClientManager
import org.bytabit.ft.fxui.util.GluonConfig
import org.bytabit.ft.util.{Config, JavaLogging}

import scala.compat.java8.FunctionConverters._
import scala.util.Success

object FiatTrader {

  import MobileApplication._

  val TRADE_VIEW: String = HOME_VIEW
  val WALLET_VIEW: String = "Wallet View"
  val MENU_LAYER: String = "Side Menu"
}

class FiatTrader extends MobileApplication with JavaLogging {

  import FiatTrader._

  val config: Config = new GluonConfig
  val actorSystem = ActorSystem.create(config.configName)

  override def init() {

    // create data directories if they don't exist
    if (config.snapshotStoreDir.isDefined && config.createDir(config.snapshotStoreDir.get).isFailure) {
      log.error("Unable to create snapshot directory.")
    }
    if (config.journalDir.isDefined && config.createDir(config.journalDir.get).isFailure) {
      log.error("Unable to create journal directory.")
    }
    if (config.walletDir.isDefined && config.createDir(config.walletDir.get).isFailure) {
      log.error("Unable to create wallet directory.")
    }

    val tradeViewFactory: () => View = () => {
      //      val tradeView = new TradeView()
      //      tradeView.getView.asInstanceOf[View]

      ClientManager.actorOf(config, actorSystem)

      new View(new CheckBox("I like Glisten"))
    }

    addViewFactory(TRADE_VIEW, tradeViewFactory.asJava)

  }

  override def postInit(scene: Scene) {

    Swatch.GREEN.assignTo(scene)
    // Load  UI
    //    var title: String = "Fiat Trader (" + config.walletNet + ", v" + config.version
    //    log.info("Config: " + config.walletNet)
    //    if (config.configName.length > 0 && config.configName != "default") title += ", " + config.configName
    //    title += ")"
    //    //        addViewFactory(TRADE_VIEW, () -> new TradeView(TRADE_VIEW, actorSystem).getView());
    //    //        addViewFactory(WALLET_VIEW, () -> new WalletView(WALLET_VIEW).getView());
    //    val drawer: NavigationDrawer = new NavigationDrawer
    //    val header: NavigationDrawer.Header = new NavigationDrawer.Header("Bytabit", "Fiat Trader", new Avatar(21, new Image(classOf[FiatTrader].getResourceAsStream("/logo.png"))))
    //    drawer.setHeader(header)
    //    val primaryItem: NavigationDrawer.Item = new NavigationDrawer.Item("Primary", MaterialDesignIcon.HOME.graphic)
    //    val secondaryItem: NavigationDrawer.Item = new NavigationDrawer.Item("Secondary", MaterialDesignIcon.DASHBOARD.graphic)
    //    drawer.getItems.addAll(primaryItem, secondaryItem)
    //        drawer.selectedItemProperty().addListener((obs, oldItem, newItem) -> {
    //            hideLayer(MENU_LAYER);
    //            switchView(newItem.equals(primaryItem) ? TRADE_VIEW : WALLET_VIEW);
    //        });


  }

  override def stop() {

    import scala.concurrent.ExecutionContext.Implicits.global

    actorSystem.terminate.onComplete {
      case Success(v) =>
        log.info("Akka termination succeeded.")
      case _ =>
        log.error("Akka termination failed.")
    }
  }
}