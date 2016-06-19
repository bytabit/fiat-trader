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
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.bytabit.ft.fxui.util.ActorController;
import org.bytabit.ft.util.Config;

import java.util.ResourceBundle;

public class MainUI implements ActorController {


    @FXML
    private ResourceBundle resources;

    private ActorSystem sys;

    public MainUI(ActorSystem system) {
        sys = system;
    }

    @FXML
    private Tab traderTradesTab;

    @FXML
    private Tab arbitratorTradesTab;

    @FXML
    private Tab walletTab;

    @FXML
    private Tab eventClientServersTab;

    @FXML
    private Tab eventClientProfileTab;

    @FXML
    private Tab arbitratorContractsTab;

    @FXML
    private TabPane tabPane;

    @FXML
    void initialize() {

        // remove arbitrator server tab if arbitrator is not enabled
        arbitratorTradesTab.setDisable(!Config.arbitratorEnabled());
        arbitratorContractsTab.setDisable(!Config.arbitratorEnabled());

        // remove trader trades tab if arbitrator is enabled
        traderTradesTab.setDisable(Config.arbitratorEnabled());

        tabPane.getTabs().removeIf(Tab::isDisable);
    }

    @Override
    public ActorSystem system() {
        return sys;
    }

    @Override
    public LoggingAdapter log() {
        return sys.log();
    }
}
