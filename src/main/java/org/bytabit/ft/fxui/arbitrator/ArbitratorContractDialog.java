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

import akka.event.LoggingAdapter;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.bitcoinj.core.Sha256Hash;
import org.bytabit.ft.fxui.FiatTrader;
import org.bytabit.ft.util.Config;
import org.bytabit.ft.util.PaymentMethod;
import org.bytabit.ft.wallet.model.Arbitrator;
import org.joda.money.CurrencyUnit;

import java.io.IOException;
import java.util.ResourceBundle;

public class ArbitratorContractDialog extends Alert {

    private LoggingAdapter log;

    private Arbitrator arbitrator;

    private ArbitratorManagerFxService arbitratorManagerFxService;

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
    private ChoiceBox<CurrencyUnit> addFiatCurrencyChoiceBox;

    @FXML
    private ChoiceBox<PaymentMethod> addPaymentMethodChoiceBox;

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
    private TableColumn<ContractUIModel, String> paymentMethodColumn;

    @FXML
    private GridPane gridPane;

    public ArbitratorContractDialog(ArbitratorManagerFxService arbitratorManagerFxService, Arbitrator a) {
        super(AlertType.INFORMATION);
        arbitrator = a;
        log = arbitratorManagerFxService.system().log();
        this.arbitratorManagerFxService = arbitratorManagerFxService;

        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setLocation(FiatTrader.class.getResource("/org/bytabit/ft/fxui/arbitrator/ArbitratorContractDialog.fxml"));
        fxmlLoader.setController(this);

        Boolean isArbitratorServer = Config.arbitratorEnabled() && Config.publicUrl().equals(arbitrator.url());

        try {
            getDialogPane().setContent(fxmlLoader.load());

            actionColumn.setVisible(isArbitratorServer);
            gridPane.setVisible(isArbitratorServer);
        } catch (IOException e) {
            log.error("Couldn't load arbitrator contract dialog.");
            e.printStackTrace();
        }
    }

    // handlers

    @FXML
    void initialize() {

        setTitle(arbitrator.url().toString());
        setHeaderText("Arbitrator Contracts");

        // setup arbitrator id listener
        idLabel.setText(arbitrator.id().toString());

        // setup arbitrator bond percent listener
        bondLabel.setText(String.format("%f", arbitrator.bondPercent() * 100));

        // setup arbitrator fee listener
        feeLabel.setText(arbitrator.btcArbitratorFee().toString());

        // setup arbitrator url listener
        urlLabel.setText(arbitrator.url().toString());

        // setup contract template table
        actionColumn.setCellValueFactory(ct -> ct.getValue().idProperty());

        actionColumn.setCellFactory(column -> newTableCell());

        idColumn.setCellValueFactory(ct -> ct.getValue().idProperty());
        currencyUnitColumn.setCellValueFactory(ct -> ct.getValue().fiatCurrencyUnitProperty());
        paymentMethodColumn.setCellValueFactory(ct -> ct.getValue().paymentMethodProperty());
        //feeColumn.setCellValueFactory(ct -> ct.getValue().arbitratorFeeProperty());

        contractTemplateTable.setItems(arbitratorManagerFxService.contractTemplates().get(arbitrator.url()).get());

        addFiatCurrencyChoiceBox.setConverter(new StringConverter<CurrencyUnit>() {
            @Override
            public String toString(CurrencyUnit cu) {
                return cu.getCode();
            }

            @Override
            public CurrencyUnit fromString(String code) {
                return CurrencyUnit.of(code);
            }
        });

        addFiatCurrencyChoiceBox.setItems(arbitratorManagerFxService.addCurrencyUnits());

        addPaymentMethodChoiceBox.setConverter(new StringConverter<PaymentMethod>() {
            @Override
            public String toString(PaymentMethod dm) {
                return dm.name();
            }

            @Override
            public PaymentMethod fromString(String name) {
                // TODO need a better way to handle this
                return PaymentMethod.getInstance(name).getOrElse(null);
            }
        });

        addPaymentMethodChoiceBox.setItems(arbitratorManagerFxService.addPaymentMethods());

        addFiatCurrencyChoiceBox.setOnAction((event) -> {
            CurrencyUnit selectedCurrencyUnit = addFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
            arbitratorManagerFxService.setSelectedCurrencyUnit(selectedCurrencyUnit);
            if (arbitratorManagerFxService.addPaymentMethods().size() == 1)
                addPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });
    }

    @FXML
    void handleAddContractTemplate() {

        CurrencyUnit fiatCurrencyUnit = addFiatCurrencyChoiceBox.getValue();
        PaymentMethod paymentMethod = addPaymentMethodChoiceBox.getValue();

        arbitratorManagerFxService.addContractTemplate(arbitrator, fiatCurrencyUnit, paymentMethod);
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
                    deleteButton.setOnAction(evt -> arbitratorManagerFxService.deleteContractTemplate(arbitrator, Sha256Hash.wrap(item)));
                    vbox.getChildren().addAll(deleteButton);
                    setGraphic(vbox);
                }
            }
        };
    }
}
