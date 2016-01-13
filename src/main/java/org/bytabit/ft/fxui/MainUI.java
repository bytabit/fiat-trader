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
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.bytabit.ft.fxui.util.ActorController;
import org.bytabit.ft.util.Config;

import java.util.ResourceBundle;

public class MainUI extends ActorController {


    @FXML
    private ResourceBundle resources;

    public MainUI(ActorSystem system) {
        super(system);
    }

    @FXML
    private Tab notaryServerTab;

    @FXML
    private TabPane tabPane;

    @FXML
    void initialize() {

        // remove notary server tab if notary is not enabled
        notaryServerTab.setDisable(!Config.serverEnabled());
        tabPane.getTabs().removeIf(Tab::isDisable);
    }

    // TODO add code for log and other main screen feature
}
