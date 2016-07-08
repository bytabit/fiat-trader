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
package org.bytabit.ft.fxui.client;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.bytabit.ft.fxui.util.ActorController;
import org.bytabit.ft.util.PaymentMethod;
import org.joda.money.CurrencyUnit;
import scala.Tuple2;

import java.util.ResourceBundle;

public class ProfileUI implements ActorController {

    private final ProfileFxService profileFxService;

    @FXML
    private ResourceBundle resources;

    @FXML
    private Label profileIdLabel;

    @FXML
    private TextField profileNameTextField;

    @FXML
    private TextField profileEmailTextField;

    @FXML
    private TableColumn<PaymentDetailsUIModel, Tuple2<CurrencyUnit, PaymentMethod>> actionColumn;

    @FXML
    private TableColumn<PaymentDetailsUIModel, String> currencyUnitColumn;

    @FXML
    private TableColumn<PaymentDetailsUIModel, String> paymentMethodColumn;

    @FXML
    private TableColumn<PaymentDetailsUIModel, String> paymentDetailsColumn;

    @FXML
    private TableView<PaymentDetailsUIModel> paymentDetailsTable;

    @FXML
    private Button addButton;

    @FXML
    private ChoiceBox<CurrencyUnit> addDetailsFiatCurrencyChoiceBox;

    @FXML
    private ChoiceBox<PaymentMethod> addDetailsPaymentMethodChoiceBox;

    @FXML
    private TextField addPaymentDetailsTextField;

    private final ActorSystem sys;

    public ProfileUI(ActorSystem system) {
        this.sys = system;
        this.profileFxService = new ProfileFxService(system);
    }

    @FXML
    void initialize() {

        this.profileFxService.start();

        // bind data to controls

        this.profileIdLabel.textProperty().bind(this.profileFxService.profileId());
        this.addDetailsFiatCurrencyChoiceBox.setItems(this.profileFxService.addDetailsCurrencyUnits());
        this.addDetailsPaymentMethodChoiceBox.setItems(this.profileFxService.addDetailsPaymentMethods());

        // profile name and email controls

        this.profileNameTextField.textProperty().bindBidirectional(this.profileFxService.profileName());

        this.profileNameTextField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                this.profileFxService.updateProfileName(this.profileNameTextField.textProperty().get());
            }
        });

        this.profileEmailTextField.textProperty().bindBidirectional(this.profileFxService.profileEmail());

        this.profileEmailTextField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                this.profileFxService.updateProfileEmail(this.profileEmailTextField.textProperty().get());
            }
        });

        // table setup
        this.actionColumn.setCellValueFactory(ct -> ct.getValue().idProperty());
        this.actionColumn.setCellFactory(column -> this.newTableCell());
        this.currencyUnitColumn.setCellValueFactory(ct -> ct.getValue().currencyUnitProperty());
        this.paymentMethodColumn.setCellValueFactory(ct -> ct.getValue().paymentMethodProperty());
        this.paymentDetailsColumn.setCellValueFactory(ct -> ct.getValue().paymentDetailsProperty());
        this.paymentDetailsTable.setItems(this.profileFxService.paymentDetails());

        // choice box setup

        this.addDetailsFiatCurrencyChoiceBox.setConverter(new StringConverter<CurrencyUnit>() {
            @Override
            public String toString(CurrencyUnit cu) {
                return cu.getCode();
            }

            @Override
            public CurrencyUnit fromString(String code) {
                return CurrencyUnit.of(code);
            }
        });

        this.addDetailsFiatCurrencyChoiceBox.setOnAction(event -> {
            CurrencyUnit selectedCurrencyUnit = this.addDetailsFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
            this.profileFxService.setSelectedAddCurrencyUnit(selectedCurrencyUnit);
            if (this.profileFxService.addDetailsPaymentMethods().size() == 1)
                this.addDetailsPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });

        this.addDetailsPaymentMethodChoiceBox.setConverter(new StringConverter<PaymentMethod>() {
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

        this.addDetailsPaymentMethodChoiceBox.setOnAction(event -> {
            PaymentMethod selectedPaymentMethod = this.addDetailsPaymentMethodChoiceBox.getSelectionModel().getSelectedItem();
            this.profileFxService.setSelectedAddPaymentMethod(selectedPaymentMethod);
            if (this.profileFxService.addDetailsPaymentMethods().size() == 1)
                this.addDetailsPaymentMethodChoiceBox.getSelectionModel().selectFirst();
            if (!selectedPaymentMethod.requiredDetails().isEmpty()) {
                this.addPaymentDetailsTextField.promptTextProperty().setValue(selectedPaymentMethod.requiredDetails().mkString(", "));
            }
        });

    }

    @Override
    public ActorSystem system() {
        return this.sys;
    }

    @Override
    public LoggingAdapter log() {
        return this.sys.log();
    }

    @FXML
    void handleAddPaymentDetails() {

        CurrencyUnit fiatCurrencyUnit = this.addDetailsFiatCurrencyChoiceBox.getValue();
        PaymentMethod paymentMethod = this.addDetailsPaymentMethodChoiceBox.getValue();
        String paymentDetails = this.addPaymentDetailsTextField.getText();

        this.profileFxService.addPaymentDetails(fiatCurrencyUnit, paymentMethod, paymentDetails);
    }

    private TableCell<PaymentDetailsUIModel, Tuple2<CurrencyUnit, PaymentMethod>> newTableCell() {

        return new TableCell<PaymentDetailsUIModel, Tuple2<CurrencyUnit, PaymentMethod>>() {

            @Override
            protected void updateItem(Tuple2<CurrencyUnit, PaymentMethod> item, boolean empty) {
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
                    deleteButton.setOnAction(evt -> ProfileUI.this.profileFxService.removePaymentDetails(item._1, item._2));
                    vbox.getChildren().addAll(deleteButton);
                    this.setGraphic(vbox);
                }
            }
        };
    }

}
