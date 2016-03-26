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
import akka.event.LoggingAdapter;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.bitcoinj.core.Sha256Hash;
import org.bytabit.ft.fxui.ArbitratorServerFxService;
import org.bytabit.ft.fxui.model.ContractUIModel;
import org.bytabit.ft.fxui.util.ActorController;
import org.bytabit.ft.util.Config;
import org.joda.money.CurrencyUnit;

import java.util.ResourceBundle;

public class ArbitratorServerUI implements ActorController {

    private ArbitratorServerFxService arbitratorServerFxService;

    @FXML
    private ResourceBundle resources;

    // controls

    @FXML
    private Label idLabel;

    @FXML
    private Label bondLabel;

    @FXML
    private Label feeLabel;

    @FXML
    private Label urlLabel;

    @FXML
    private Button addContractTemplateButton;

    @FXML
    private ChoiceBox<String> addFiatCurrencyChoiceBox;

    @FXML
    private TextField fiatDeliveryMethodTextField;

    @FXML
    private TextField btcArbitratorFeeTextField;

    // table

    @FXML
    private TableView<ContractUIModel> contractTemplateTable;

    @FXML
    private TableColumn<ContractUIModel, String> actionColumn;

    @FXML
    private TableColumn<ContractUIModel, String> idColumn;

    @FXML
    private TableColumn<ContractUIModel, String> currencyUnitColumn;

    @FXML
    private TableColumn<ContractUIModel, String> deliveryMethodColumn;

    final private ActorSystem sys;

    public ArbitratorServerUI(ActorSystem system) {
        sys = system;
        arbitratorServerFxService = new ArbitratorServerFxService(system);
    }

    // handlers

    @FXML
    void initialize() {

        if (Config.serverEnabled()) {

            // setup arbitrator id listener
            arbitratorServerFxService.arbitratorId().addListener((observable, oldValue, newValue) -> {
                idLabel.setText(newValue);
            });

            // setup arbitrator bond percent listener
            arbitratorServerFxService.bondPercent().addListener((observable, oldValue, newValue) -> {
                bondLabel.setText(newValue);
            });

            // setup arbitrator fee listener
            arbitratorServerFxService.arbitratorFee().addListener((observable, oldValue, newValue) -> {
                feeLabel.setText(newValue);
            });

            // setup arbitrator url listener
            arbitratorServerFxService.arbitratorUrl().addListener((observable, oldValue, newValue) -> {
                urlLabel.setText(newValue);
            });

            // setup contract template table
            actionColumn.setCellValueFactory(ct -> ct.getValue().idProperty());

            actionColumn.setCellFactory(column -> newTableCell());

            idColumn.setCellValueFactory(ct -> ct.getValue().idProperty());
            currencyUnitColumn.setCellValueFactory(ct -> ct.getValue().fiatCurrencyUnitProperty());
            deliveryMethodColumn.setCellValueFactory(ct -> ct.getValue().deliveryMethodProperty());
            //feeColumn.setCellValueFactory(ct -> ct.getValue().arbitratorFeeProperty());

            contractTemplateTable.setItems(arbitratorServerFxService.contractTemplates());
            addFiatCurrencyChoiceBox.setItems(arbitratorServerFxService.addCurrencyUnits());

            arbitratorServerFxService.start();
        }
    }

    @FXML
    void handleAddContractTemplate() {

        CurrencyUnit fiatCurrencyUnit = CurrencyUnit.of(addFiatCurrencyChoiceBox.getValue());
        String fiatDeliveryMethod = fiatDeliveryMethodTextField.getText();

        arbitratorServerFxService.addContractTemplate(fiatCurrencyUnit, fiatDeliveryMethod);
    }

    private TableCell<ContractUIModel, String> newTableCell() {

        return new TableCell<ContractUIModel, String>() {

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                    setGraphic(null);
                } else {
                    VBox vbox = new VBox();
                    vbox.alignmentProperty().setValue(Pos.CENTER);
                    Button deleteButton = new Button();
                    deleteButton.setText("DELETE");
                    deleteButton.setOnAction(evt -> arbitratorServerFxService.deleteContractTemplate(Sha256Hash.wrap(item)));
                    vbox.getChildren().addAll(deleteButton);
                    setGraphic(vbox);
                }
            }
        };
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
