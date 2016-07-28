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
package org.bytabit.ft.fxui;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.Avatar;
import com.gluonhq.charm.glisten.control.NavigationDrawer;
import com.gluonhq.charm.glisten.layout.layer.SidePopupView;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import org.bytabit.ft.client.ClientManager;
import org.bytabit.ft.fxui.views.TradeView;
import org.bytabit.ft.fxui.views.WalletView;
import org.bytabit.ft.server.EventServer;
import org.bytabit.ft.util.Config;

import java.io.File;

public class FiatTrader extends MobileApplication {

    public static final String TRADE_VIEW = HOME_VIEW;
    public static final String WALLET_VIEW = "Wallet View";
    public static final String MENU_LAYER = "Side Menu";

    private ActorSystem system;
    private LoggingAdapter log;

    @Override
    public void init() {

        // Load  UI

        String title = "Fiat Trader (" + Config.walletNet() + ", v" + Config.version();
        if (Config.config().length() > 0 && !Config.config().equals("default")) title += ", " + Config.config();
        title += ")";

        addViewFactory(TRADE_VIEW, () -> new TradeView(TRADE_VIEW).getView());
        addViewFactory(WALLET_VIEW, () -> new WalletView(WALLET_VIEW).getView());

        NavigationDrawer drawer = new NavigationDrawer();

        NavigationDrawer.Header header = new NavigationDrawer.Header("Bytabit",
                "Fiat Trader",
                new Avatar(21, new Image(FiatTrader.class.getResourceAsStream("/logo.png"))));
        drawer.setHeader(header);

        final NavigationDrawer.Item primaryItem = new NavigationDrawer.Item("Primary", MaterialDesignIcon.HOME.graphic());
        final NavigationDrawer.Item secondaryItem = new NavigationDrawer.Item("Secondary", MaterialDesignIcon.DASHBOARD.graphic());
        drawer.getItems().addAll(primaryItem, secondaryItem);

        drawer.selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            hideLayer(MENU_LAYER);
            switchView(newItem.equals(primaryItem) ? TRADE_VIEW : WALLET_VIEW);
        });

        addLayerFactory(MENU_LAYER, () -> new SidePopupView(drawer));
    }

    @Override
    public void postInit(Scene scene) {

        // Create actor system
        system = ActorSystem.create(Config.config());
        log = system.log();

        // create data directories if they don't exist
        if (Config.createDir(Config.snapshotStoreDir()).isFailure()) {
            log.error("Unable to create snapshot directory.");
        }
        if (Config.createDir(Config.journalDir()).isFailure()) {
            log.error("Unable to create journal directory.");
        }
        if (Config.createDir(new File(Config.walletDir())).isFailure()) {
            log.error("Unable to create wallet directory.");
        }

        ClientManager.actorOf(system);

        if (Config.serverEnabled()) {
            EventServer.actorOf(system);
        }
    }

    @Override
    public void stop() {
        system.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
