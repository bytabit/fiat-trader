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

package org.bytabit.ft.fxui.arbitrator;

import akka.actor.ActorSystem;
import javafx.fxml.FXML;
import org.bytabit.ft.fxui.util.AbstractTradeUI;
import org.bytabit.ft.util.Config;

public class ArbitrateUI extends AbstractTradeUI {

    private ArbitrateFxService tradeFxService;

    public ArbitrateUI(ActorSystem system) {

        super(system);
        tradeFxService = new ArbitrateFxService(Config.publicUrl(), system);
        tradeFxService.start();
    }

    @FXML
    protected void initialize() {

        super.initialize();

        // setup trade table

        actionColumn.setCellValueFactory(t -> t.getValue().actionProperty());
        actionColumn.setCellFactory(column -> new ArbitratorUIActionTableCell(tradeFxService));

        tradeTable.setItems(tradeFxService.trades());
    }
}
