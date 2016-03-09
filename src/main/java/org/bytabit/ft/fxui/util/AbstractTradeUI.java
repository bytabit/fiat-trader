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

package org.bytabit.ft.fxui.util;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.bytabit.ft.fxui.TradeInfoDialog;
import org.bytabit.ft.fxui.model.TradeUIActionTableCell;
import org.bytabit.ft.fxui.model.TradeUIModel;

import java.util.ResourceBundle;

public abstract class AbstractTradeUI implements ActorController {

    protected TradeFxService tradeFxService;

    @FXML
    protected ResourceBundle resources;

    // table

    @FXML
    protected TableView<TradeUIModel> tradeTable;

    @FXML
    protected TableColumn<TradeUIModel, TradeUIActionTableCell.TradeOriginState> actionColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> statusColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> fiatCurrencyColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> fiatAmountColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> btcAmountColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> exchRateColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> deliveryMethodColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> bondPercentColumn;

    @FXML
    protected TableColumn<TradeUIModel, String> notaryFeeColumn;

    final private ActorSystem sys;

    public AbstractTradeUI(ActorSystem system) {
        sys = system;
    }

    @Override
    public LoggingAdapter log() {
        return system().log();
    }

    public ActorSystem system() {
        return sys;
    }

    @FXML
    protected void initialize() {

        // setup trade table

        tradeTable.setRowFactory(tv -> {
            TableRow row = new TableRow<TradeUIModel>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    TradeUIModel rowData = (TradeUIModel) row.getItem();

                    TradeInfoDialog dialog = new TradeInfoDialog(system(), rowData);
                    dialog.showAndWait();
                }
            });
            return row;
        });

        statusColumn.setCellValueFactory(t -> t.getValue().statusProperty());
        fiatCurrencyColumn.setCellValueFactory(t -> t.getValue().fiatCurrencyUnitProperty());
        fiatAmountColumn.setCellValueFactory(t -> t.getValue().fiatAmountProperty());
        btcAmountColumn.setCellValueFactory(t -> t.getValue().btcAmountProperty());
        exchRateColumn.setCellValueFactory(t -> t.getValue().exchangeRateProperty());
        deliveryMethodColumn.setCellValueFactory(t -> t.getValue().deliveryMethodProperty());
        bondPercentColumn.setCellValueFactory(t -> t.getValue().bondPercentProperty());
        notaryFeeColumn.setCellValueFactory(t -> t.getValue().notaryFeeProperty());

    }

}
