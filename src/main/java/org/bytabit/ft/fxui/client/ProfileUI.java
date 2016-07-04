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
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.bytabit.ft.fxui.util.ActorController;
import org.bytabit.ft.util.PaymentMethod;
import org.joda.money.CurrencyUnit;

import java.util.ResourceBundle;

public class ProfileUI implements ActorController {

    private ProfileFxService profileFxService;

    @FXML
    private ResourceBundle resources;

    @FXML
    private Label profileIdLabel;

    @FXML
    private TextField profileNameTextField;

    @FXML
    private TextField profileEmailTextField;

    @FXML
    private TableColumn<?, ?> actionColumn;

    @FXML
    private TableColumn<?, ?> fiatCurrencyColumn;

    @FXML
    private TableColumn<?, ?> paymentMethodColumn;

    @FXML
    private TableColumn<?, ?> paymentDetailsColumn;

    @FXML
    private TableView<?> paymentDetailsTable;

    @FXML
    private Button addButton;

    @FXML
    private ChoiceBox<CurrencyUnit> addDetailsFiatCurrencyChoiceBox;

    @FXML
    private ChoiceBox<PaymentMethod> addDetailsPaymentMethodChoiceBox;

    @FXML
    private TextField addPaymentDetailsTextField;

    private ActorSystem sys;

    public ProfileUI(ActorSystem system) {
        sys = system;
        profileFxService = new ProfileFxService(system);
    }

    @FXML
    void initialize() {

        profileFxService.start();

        // bind data to controls

        profileIdLabel.textProperty().bind(profileFxService.profileId());
        addDetailsFiatCurrencyChoiceBox.setItems(profileFxService.addDetailsCurrencyUnits());
        addDetailsPaymentMethodChoiceBox.setItems(profileFxService.addDetailsPaymentMethods());

        // profile name and email controls

        profileNameTextField.textProperty().bindBidirectional(profileFxService.profileName());

        profileNameTextField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                profileFxService.updateProfileName(profileNameTextField.textProperty().get());
            }
        });

        profileEmailTextField.textProperty().bindBidirectional(profileFxService.profileEmail());

        profileEmailTextField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                profileFxService.updateProfileEmail(profileEmailTextField.textProperty().get());
            }
        });

        // choice box setup

        addDetailsFiatCurrencyChoiceBox.setConverter(new StringConverter<CurrencyUnit>() {
            @Override
            public String toString(CurrencyUnit cu) {
                return cu.getCode();
            }

            @Override
            public CurrencyUnit fromString(String code) {
                return CurrencyUnit.of(code);
            }
        });

        addDetailsFiatCurrencyChoiceBox.setOnAction((event) -> {
            CurrencyUnit selectedCurrencyUnit = addDetailsFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
            profileFxService.setSelectedAddCurrencyUnit(selectedCurrencyUnit);
            if (profileFxService.addDetailsPaymentMethods().size() == 1)
                addDetailsPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });

        addDetailsPaymentMethodChoiceBox.setConverter(new StringConverter<PaymentMethod>() {
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

        addDetailsPaymentMethodChoiceBox.setOnAction((event) -> {
            PaymentMethod selectedPaymentMethod = addDetailsPaymentMethodChoiceBox.getSelectionModel().getSelectedItem();
            profileFxService.setSelectedAddPaymentMethod(selectedPaymentMethod);
            if (profileFxService.addDetailsPaymentMethods().size() == 1)
                addDetailsPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });

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
