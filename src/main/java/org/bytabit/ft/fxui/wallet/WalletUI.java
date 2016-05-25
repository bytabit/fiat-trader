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

package org.bytabit.ft.fxui.wallet;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.bytabit.ft.fxui.WalletFxService;
import org.bytabit.ft.fxui.model.TransactionUIModel;
import org.bytabit.ft.fxui.util.ActorController;

import java.util.ResourceBundle;

public class WalletUI implements ActorController {

    private final WalletFxService walletFxService;

    @FXML
    private ResourceBundle resources;

    // wallet UI controls
    @FXML
    private Label walletBalanceLabel;

    @FXML
    private Label downloadMessageLabel;

    @FXML
    private ProgressBar downloadProgressBar;

    @FXML
    private TableView<TransactionUIModel> walletTable;

    @FXML
    private TableColumn<TransactionUIModel, String> walletHashColumn;
    @FXML
    private TableColumn<TransactionUIModel, Integer> walletDepthColumn;
    @FXML
    private TableColumn<TransactionUIModel, String> walletConficenceTypeColumn;
    @FXML
    private TableColumn<TransactionUIModel, String> walletDateColumn;
    @FXML
    private TableColumn<TransactionUIModel, String> walletMemoColumn;
    @FXML
    private TableColumn<TransactionUIModel, String> walletBtcAmtColumn;

    final private ActorSystem sys;

    public WalletUI(ActorSystem system) {
        sys = system;
        walletFxService = new WalletFxService(system);
    }

    @FXML
    void initialize() {

        // setup wallet download progress bar
        walletFxService.walletBalance().addListener((observable, oldValue, newValue) -> {
            walletBalanceLabel.setText(newValue);
        });

        downloadProgressBar.setProgress(0);

        walletFxService.downloadProgress().addListener((observable, oldValue, newValue) -> {
            Double percent = newValue.doubleValue() / 100;
            downloadProgressBar.setProgress(percent);
            if (percent < 1) {
                downloadMessageLabel.setText("Downloading Blockchain");
            } else {
                downloadMessageLabel.setText("Blockchain Downloaded");
            }
        });

        // Add observable list data to wallet table
        walletTable.setItems(walletFxService.transactions());

        // Initialize transaction table columns
        walletHashColumn.setCellValueFactory(t -> t.getValue().hashProperty());
        walletConficenceTypeColumn.setCellValueFactory(t -> t.getValue().confidenceTypeProperty());
        walletDepthColumn.setCellValueFactory(t -> t.getValue().depthProperty().asObject());
        walletMemoColumn.setCellValueFactory(t -> t.getValue().memoProperty());
        walletBtcAmtColumn.setCellValueFactory(t -> t.getValue().btcAmtProperty());

        // start wallet service
        walletFxService.start();
    }

    @FXML
    private void handleRequestFunds() {
        walletFxService.findNewReceiveAddress();
    }

    @FXML
    private void handleWithdrawFunds() {
        walletFxService.dialogWithdrawBtc();
    }

    @FXML
    private void handleBackupWallet() { walletFxService.generateBackupCode(); }

    @Override
    public ActorSystem system() {
        return sys;
    }

    @Override
    public LoggingAdapter log() {
        return sys.log();
    }
}
