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

package org.bytabit.ft.fxui.trade;

import akka.actor.ActorSystem;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.bytabit.ft.fxui.TraderTradeFxService;
import org.bytabit.ft.fxui.model.TradeUIActionTableCell;
import org.bytabit.ft.fxui.model.TradeUIModel;
import org.bytabit.ft.fxui.util.AbstractTradeUI;
import org.bytabit.ft.util.BTCMoney;
import org.bytabit.ft.util.FiatMoney;
import org.bytabit.ft.util.PaymentMethod;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

public class TraderTradeUI extends AbstractTradeUI {

    private TraderTradeFxService tradeFxService;

    @FXML
    protected TableColumn<TradeUIModel, String> roleColumn;

    // sell row

    @FXML
    private Button sellButton;

    @FXML
    private ChoiceBox<CurrencyUnit> sellFiatCurrencyChoiceBox;

    @FXML
    private TextField sellFiatAmtField;

    @FXML
    private TextField sellBtcAmtField;

    @FXML
    private TextField sellExchRateField;

    @FXML
    private ChoiceBox<PaymentMethod> sellPaymentMethodChoiceBox;

    @FXML
    private Label sellBondPercentLabel;

    @FXML
    private Label sellNotaryFeeLabel;

    public TraderTradeUI(ActorSystem system) {

        super(system);
        tradeFxService = new TraderTradeFxService(system);
        tradeFxService.start();
    }

    @FXML
    protected void initialize() {

        super.initialize();

        // setup trade table

        actionColumn.setCellValueFactory(t -> t.getValue().actionProperty());
        actionColumn.setCellFactory(column -> new TradeUIActionTableCell(tradeFxService));
        roleColumn.setCellValueFactory(t -> t.getValue().roleProperty());

        tradeTable.setItems(tradeFxService.trades());

        // bind data to controls

        sellFiatCurrencyChoiceBox.setItems(tradeFxService.sellCurrencyUnits());
        sellPaymentMethodChoiceBox.setItems(tradeFxService.sellPaymentMethods());
        sellBondPercentLabel.textProperty().bind(tradeFxService.sellBondPercent());
        sellNotaryFeeLabel.textProperty().bind(tradeFxService.sellArbitratorFee());

        // handle change events

        tradeFxService.tradeUncommitted().addListener((observable1, oldValue1, newValue1) -> {
            sellButton.disableProperty().setValue(newValue1);
        });

        sellFiatCurrencyChoiceBox.setConverter(new StringConverter<CurrencyUnit>() {
            @Override
            public String toString(CurrencyUnit cu) {
                return cu.getCode();
            }

            @Override
            public CurrencyUnit fromString(String code) {
                return CurrencyUnit.of(code);
            }
        });

        sellFiatCurrencyChoiceBox.setOnAction((event) -> {
            CurrencyUnit selectedCurrencyUnit = sellFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
            tradeFxService.setSelectedAddCurrencyUnit(selectedCurrencyUnit);
            if (tradeFxService.sellPaymentMethods().size() == 1)
                sellPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });

        sellPaymentMethodChoiceBox.setConverter(new StringConverter<PaymentMethod>() {
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

        sellPaymentMethodChoiceBox.setOnAction((event) -> {
            PaymentMethod selectedPaymentMethod = sellPaymentMethodChoiceBox.getSelectionModel().getSelectedItem();
            tradeFxService.setSelectedContract(selectedPaymentMethod);
            if (tradeFxService.sellPaymentMethods().size() == 1)
                sellPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });

        sellFiatAmtField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateAddTradeBtcAmt();
        });

        sellExchRateField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateAddTradeBtcAmt();
        });
    }

    public void handleCreateSellOffer() {
        CurrencyUnit cu = sellFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
        Money fa = FiatMoney.apply(cu, sellFiatAmtField.getText());
        Money ba = BTCMoney.apply(sellBtcAmtField.getText());
        PaymentMethod dm = sellPaymentMethodChoiceBox.getSelectionModel().getSelectedItem();
        tradeFxService.createSellOffer(cu, fa, ba, dm);
    }

    private void updateAddTradeBtcAmt() {
        String fiatAmt = sellFiatAmtField.getText();
        String exchRate = sellExchRateField.getText();
        sellBtcAmtField.setText(tradeFxService.calculateAddBtcAmt(fiatAmt, exchRate));
    }
}
