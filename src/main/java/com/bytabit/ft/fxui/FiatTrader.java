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

package com.bytabit.ft.fxui;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.bytabit.ft.fxui.util.ActorControllerFactory;
import com.bytabit.ft.notary.NotaryClientManager;
import com.bytabit.ft.notary.NotaryServerManager;
import com.bytabit.ft.util.Config;
import com.bytabit.ft.wallet.WalletManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.io.IOException;

public class FiatTrader extends Application {

    @Override
    public final void start(Stage stage) throws IOException {

        // TODO Create config directory (if it doesn't exist), do this here?
        // File configDir = new File(Config.);

        // Create Actor System
        ActorSystem system = ActorSystem.create(Config.config());
        ActorRef walletMgrRef = WalletManager.actorOf(system);
        NotaryServerManager.actorOf(walletMgrRef, system);
        //ActorRef tradeMgrRef = TradeManager.actorOf(walletMgrRef, system);
        NotaryClientManager.actorOf(walletMgrRef, system);

        // Load  UI
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bytabit/ft/fxui/MainUI.fxml"));
        loader.setControllerFactory(new ActorControllerFactory(system));

        Parent mainUI = loader.load();
        Scene scene = new Scene(mainUI, 1111, 350);
        String title = "Fiat Trader (" + Config.walletNet() + ")";
        if (Config.config().length() > 0) title += " | " + Config.config();

        stage.setTitle(title);
        stage.setScene(scene);
        // TODO add custom icon and with hover text
        // stage.getIcons().add(new Image(this.getClass().getResourceAsStream("bytabit-icon.png")));

        // Set UI Close Handler
        stage.setOnCloseRequest(e -> {
            // Shutdown Actors
            system.terminate();
            try {
                Await.result(system.whenTerminated(), Duration.create(1, "second"));
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });

        // Show UI
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
