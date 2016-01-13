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

package com.bytabit.ft.fxui.trade;

import akka.actor.ActorSystem;
import com.bytabit.ft.fxui.TradeFxService;
import com.bytabit.ft.fxui.model.TradeUIActionTableCell;
import com.bytabit.ft.fxui.model.TradeUIActionTableCell.TradeOriginState;
import com.bytabit.ft.fxui.model.TradeUIModel;
import com.bytabit.ft.fxui.util.ActorController;
import com.bytabit.ft.util.BTCMoney;
import com.bytabit.ft.util.FiatMoney;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.util.ResourceBundle;

public class TradeUI extends ActorController {

    private TradeFxService tradeFxService;

    @FXML
    private ResourceBundle resources;

    // table

    @FXML
    private TableView<TradeUIModel> tradeTable;

    @FXML
    private TableColumn<TradeUIModel, TradeOriginState> actionColumn;

    @FXML
    private TableColumn<TradeUIModel, String> statusColumn;

    @FXML
    private TableColumn<TradeUIModel, String> fiatCurrencyColumn;

    @FXML
    private TableColumn<TradeUIModel, String> fiatAmountColumn;

    @FXML
    private TableColumn<TradeUIModel, String> btcAmountColumn;

    @FXML
    private TableColumn<TradeUIModel, String> exchRateColumn;

    @FXML
    private TableColumn<TradeUIModel, String> deliveryMethodColumn;

    @FXML
    private TableColumn<TradeUIModel, String> bondPercentColumn;

    @FXML
    private TableColumn<TradeUIModel, String> notaryFeeColumn;


    // sell row

    @FXML
    private Button sellButton;

    @FXML
    private ChoiceBox<String> sellFiatCurrencyChoiceBox;

    @FXML
    private TextField sellFiatAmtField;

    @FXML
    private TextField sellBtcAmtField;

    @FXML
    private TextField sellExchRateField;

    @FXML
    private ChoiceBox<String> sellDeliveryMethodChoiceBox;

    @FXML
    private Label sellBondPercentLabel;

    @FXML
    private Label sellNotaryFeeLabel;


    public TradeUI(ActorSystem system) {

        super(system);
        tradeFxService = new TradeFxService(system);
        tradeFxService.start();
    }

    @FXML
    void initialize() {

        // setup trade table

        actionColumn.setCellValueFactory(t -> t.getValue().actionProperty());
        actionColumn.setCellFactory(column -> new TradeUIActionTableCell(tradeFxService));

        statusColumn.setCellValueFactory(t -> t.getValue().statusProperty());
        fiatCurrencyColumn.setCellValueFactory(t -> t.getValue().fiatCurrencyUnitProperty());
        fiatAmountColumn.setCellValueFactory(t -> t.getValue().fiatAmountProperty());
        btcAmountColumn.setCellValueFactory(t -> t.getValue().btcAmountProperty());
        exchRateColumn.setCellValueFactory(t -> t.getValue().exchangeRateProperty());
        deliveryMethodColumn.setCellValueFactory(t -> t.getValue().deliveryMethodProperty());
        bondPercentColumn.setCellValueFactory(t -> t.getValue().bondPercentProperty());
        notaryFeeColumn.setCellValueFactory(t -> t.getValue().notaryFeeProperty());

        tradeTable.setItems(tradeFxService.trades());

        // bind data to controls

        sellFiatCurrencyChoiceBox.setItems(tradeFxService.sellCurrencyUnits());
        sellDeliveryMethodChoiceBox.setItems(tradeFxService.sellDeliveryMethods());
        sellBondPercentLabel.textProperty().bind(tradeFxService.sellBondPercent());
        sellNotaryFeeLabel.textProperty().bind(tradeFxService.sellNotaryFee());

        // handle change events

        tradeFxService.tradeActive().addListener((observable1, oldValue1, newValue1) -> {
            sellButton.disableProperty().setValue(newValue1);
        });

        sellFiatCurrencyChoiceBox.setOnAction((event) -> {
            String selectedCurrencyUnit = sellFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem();
            tradeFxService.setSelectedAddCurrencyUnit(selectedCurrencyUnit);
            if (tradeFxService.sellDeliveryMethods().size() == 1)
                sellDeliveryMethodChoiceBox.getSelectionModel().selectFirst();
        });

        sellDeliveryMethodChoiceBox.setOnAction((event) -> {
            String selectedDeliveryMethod = sellDeliveryMethodChoiceBox.getSelectionModel().getSelectedItem();
            tradeFxService.setSelectedContract(selectedDeliveryMethod);
            if (tradeFxService.sellDeliveryMethods().size() == 1)
                sellDeliveryMethodChoiceBox.getSelectionModel().selectFirst();
        });

        sellFiatAmtField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateAddTradeExchRate();
        });

        sellBtcAmtField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateAddTradeExchRate();
        });
    }

    public void handleCreateSellOffer() {
        CurrencyUnit cu = CurrencyUnit.getInstance(sellFiatCurrencyChoiceBox.getSelectionModel().getSelectedItem());
        Money fa = FiatMoney.apply(cu, sellFiatAmtField.getText());
        Money ba = BTCMoney.apply(sellBtcAmtField.getText());
        String dm = sellDeliveryMethodChoiceBox.getSelectionModel().getSelectedItem();
        tradeFxService.createSellOffer(cu, fa, ba, dm);
    }

    private void updateAddTradeExchRate() {
        String fiatAmt = sellFiatAmtField.getText();
        String btcAmt = sellBtcAmtField.getText();
        sellExchRateField.setText(tradeFxService.calculateAddExchRate(fiatAmt, btcAmt));
    }
}
