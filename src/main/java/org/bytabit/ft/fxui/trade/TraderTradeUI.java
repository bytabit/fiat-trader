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
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
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

    // trade row

    @FXML
    private Button btcBuyButton;

    @FXML
    private Button btcSellButton;

    @FXML
    private ChoiceBox<CurrencyUnit> btcBuyFiatCurrencyChoiceBox;

    @FXML
    private TextField btcBuyFiatAmtField;

    @FXML
    private TextField btcBuyBtcAmtField;

    @FXML
    private TextField btcBuyExchRateField;

    @FXML
    private ChoiceBox<PaymentMethod> btcBuyPaymentMethodChoiceBox;

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

        btcBuyFiatCurrencyChoiceBox.setItems(tradeFxService.btcBuyCurrencyUnits());
        btcBuyPaymentMethodChoiceBox.setItems(tradeFxService.btcBuyPaymentMethods());

        // handle change events

        tradeFxService.tradeUncommitted().addListener((observable1, oldValue1, newValue1) -> {
            btcBuyButton.disableProperty().setValue(newValue1);
        });

        btcBuyFiatCurrencyChoiceBox.setConverter(new StringConverter<CurrencyUnit>() {
            @Override
            public String toString(CurrencyUnit cu) {
                return cu.getCode();
            }

            @Override
            public CurrencyUnit fromString(String code) {
                return CurrencyUnit.of(code);
            }
        });

        btcBuyFiatCurrencyChoiceBox.setOnAction((event) -> {
            CurrencyUnit selectedCurrencyUnit = btcBuyFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
            tradeFxService.setSelectedAddCurrencyUnit(selectedCurrencyUnit);
            if (tradeFxService.btcBuyPaymentMethods().size() == 1)
                btcBuyPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });

        btcBuyPaymentMethodChoiceBox.setConverter(new StringConverter<PaymentMethod>() {
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

        btcBuyPaymentMethodChoiceBox.setOnAction((event) -> {
            PaymentMethod selectedPaymentMethod = btcBuyPaymentMethodChoiceBox.getSelectionModel().getSelectedItem();
            tradeFxService.setSelectedContract(selectedPaymentMethod);
            if (tradeFxService.btcBuyPaymentMethods().size() == 1)
                btcBuyPaymentMethodChoiceBox.getSelectionModel().selectFirst();
        });

        btcBuyFiatAmtField.textProperty().addListener((observable, oldValue, newValue) -> {
            String exchRate = btcBuyExchRateField.getText();
            if (!newValue.isEmpty() && !oldValue.equals(newValue) && !exchRate.isEmpty()) updateAddTradeBtcAmt();
        });

        btcBuyExchRateField.textProperty().addListener((observable, oldValue, newValue) -> {
            String fiatAmt = btcBuyFiatAmtField.getText();
            //String btcAmt = btcBuyBtcAmtField.getText();
            if (!newValue.isEmpty() && !oldValue.equals(newValue) && !fiatAmt.isEmpty()) updateAddTradeBtcAmt();
        });
    }

    public void handleCreateBtcBuyOffer() {
        CurrencyUnit cu = btcBuyFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
        Money fa = FiatMoney.apply(cu, btcBuyFiatAmtField.getText());
        Money ba = BTCMoney.apply(btcBuyBtcAmtField.getText());
        PaymentMethod dm = btcBuyPaymentMethodChoiceBox.getSelectionModel().getSelectedItem();
        tradeFxService.createBtcBuyOffer(cu, fa, ba, dm);
    }

    public void handleCreateBtcSellOffer() {
        // TODO
    }

    private void updateAddTradeBtcAmt() {
        String fiatAmt = btcBuyFiatAmtField.getText();
        String exchRate = btcBuyExchRateField.getText();
        if (!fiatAmt.isEmpty() && !exchRate.isEmpty()) {
            btcBuyBtcAmtField.setText(tradeFxService.calculateAddBtcAmt(fiatAmt, exchRate));
        }
    }
}
