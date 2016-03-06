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
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.bytabit.ft.fxui.model.TradeUIActionTableCell;
import org.bytabit.ft.fxui.model.TradeUIModel;

import java.util.ResourceBundle;

public abstract class AbstractTradeUI extends ActorController {

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

    public AbstractTradeUI(ActorSystem system) {

        super(system);
    }

    @FXML
    protected void initialize() {

        // setup trade table

        tradeTable.setRowFactory(tv -> {
            TableRow row = new TableRow<TradeUIModel>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    TradeUIModel rowData = (TradeUIModel)row.getItem();

                    // TODO show dialog with all trade data
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("Trade ID: "+rowData.getId());
                    //alert.setContentText(rowData.toString());
                    VBox vbox = new VBox();
                    GridPane infoGrid = new GridPane();
                    infoGrid.setHgap(10);
                    infoGrid.setVgap(10);
                    infoGrid.setPadding(new Insets(20, 150, 10, 10));

                    infoGrid.add(new Label("Fiat / XBT Exch Rate:"), 0, 0);
                    infoGrid.add(new Label(rowData.exchangeRateProperty().getValue()), 1, 0);

                    infoGrid.add(new Label("Bond Percent:"), 0, 1);
                    infoGrid.add(new Label(rowData.bondPercentProperty().getValue()), 1, 1);

                    alert.getDialogPane().setContent(infoGrid);

                    alert.showAndWait();
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
