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
import org.bytabit.ft.fxui.FiatTrader;
import org.bytabit.ft.util.Config;
import org.bytabit.ft.util.PaymentMethod;
import org.bytabit.ft.wallet.model.Arbitrator;
import org.joda.money.CurrencyUnit;

import java.util.ResourceBundle;

public class ContractsDialog extends Alert {

    private final LoggingAdapter log;

    private final Arbitrator arbitrator;

    private final ArbitratorManagerFxService arbitratorManagerFxService;

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

    public ContractsDialog(ArbitratorManagerFxService arbitratorManagerFxService, Arbitrator a) {
        super(Alert.AlertType.INFORMATION);
        this.arbitrator = a;
        this.log = arbitratorManagerFxService.system().log();
        this.arbitratorManagerFxService = arbitratorManagerFxService;

        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setLocation(FiatTrader.class.getResource("/org/bytabit/ft/fxui/arbitrator/ContractsDialog.fxml"));
        fxmlLoader.setController(this);

        Boolean isArbitratorServer = Config.arbitratorEnabled() && Config.publicUrl().equals(this.arbitrator.url());

//        try {
//            this.getDialogPane().setContent(fxmlLoader.load());
//
//            this.actionColumn.setVisible(isArbitratorServer);
//            this.gridPane.setVisible(isArbitratorServer);
//        } catch (IOException e) {
//            this.log.error("Couldn't load arbitrator contract dialog.");
//            e.printStackTrace();
//        }
    }

    // handlers

    @FXML
    void initialize() {

        this.setTitle(this.arbitrator.url().toString());
        this.setHeaderText("Arbitrator Contracts");

        // setup arbitrator id listener
        this.idLabel.setText(this.arbitrator.id().toString());

        // setup arbitrator bond percent listener
        this.bondLabel.setText(String.format("%f", this.arbitrator.bondPercent() * 100));

        // setup arbitrator fee listener
        this.feeLabel.setText(this.arbitrator.btcArbitratorFee().toString());

        // setup arbitrator url listener
        this.urlLabel.setText(this.arbitrator.url().toString());

        // setup contract template table
//        this.actionColumn.setCellValueFactory(ct -> ct.getValue().idProperty());
//        this.actionColumn.setCellFactory(column -> this.newTableCell());

//        this.idColumn.setCellValueFactory(ct -> ct.getValue().idProperty());
//        this.currencyUnitColumn.setCellValueFactory(ct -> ct.getValue().fiatCurrencyUnitProperty());
//        this.paymentMethodColumn.setCellValueFactory(ct -> ct.getValue().paymentMethodProperty());
        //feeColumn.setCellValueFactory(ct -> ct.getValue().arbitratorFeeProperty());

        this.contractTemplateTable.setItems(this.arbitratorManagerFxService.contractTemplates().get(this.arbitrator.url()).get());

        this.addFiatCurrencyChoiceBox.setConverter(new StringConverter<CurrencyUnit>() {
            @Override
            public String toString(CurrencyUnit cu) {
                return cu.getCode();
            }

            @Override
            public CurrencyUnit fromString(String code) {
                return CurrencyUnit.of(code);
            }
        });

        this.addFiatCurrencyChoiceBox.setItems(this.arbitratorManagerFxService.addCurrencyUnits());

        this.addPaymentMethodChoiceBox.setConverter(new StringConverter<PaymentMethod>() {
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

        this.addPaymentMethodChoiceBox.setItems(this.arbitratorManagerFxService.addPaymentMethods());

//        this.addFiatCurrencyChoiceBox.setOnAction(event -> {
//            CurrencyUnit selectedCurrencyUnit = this.addFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
//            this.arbitratorManagerFxService.setSelectedCurrencyUnit(selectedCurrencyUnit);
//            if (this.arbitratorManagerFxService.addPaymentMethods().size() == 1)
//                this.addPaymentMethodChoiceBox.getSelectionModel().selectFirst();
//        });
    }

    @FXML
    void handleAddContractTemplate() {

        CurrencyUnit fiatCurrencyUnit = this.addFiatCurrencyChoiceBox.getValue();
        PaymentMethod paymentMethod = this.addPaymentMethodChoiceBox.getValue();

        this.arbitratorManagerFxService.addContractTemplate(this.arbitrator, fiatCurrencyUnit, paymentMethod);
    }

    private TableCell<ContractUIModel, String> newTableCell() {

        return new TableCell<ContractUIModel, String>() {

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    this.setText(null);
                    this.setStyle("");
                    this.setGraphic(null);
                } else {
                    VBox vbox = new VBox();
                    vbox.alignmentProperty().setValue(Pos.CENTER);
                    Button deleteButton = new Button();
                    deleteButton.setText("DELETE");
//                    deleteButton.setOnAction(evt -> ContractsDialog.this.arbitratorManagerFxService.deleteContractTemplate(ContractsDialog.this.arbitrator, Sha256Hash.wrap(item)));
                    vbox.getChildren().addAll(deleteButton);
                    this.setGraphic(vbox);
                }
            }
        };
    }
}
