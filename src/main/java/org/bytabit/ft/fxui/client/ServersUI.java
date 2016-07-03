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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.bytabit.ft.fxui.arbitrator.ArbitratorManagerFxService;
import org.bytabit.ft.fxui.arbitrator.ArbitratorUIModel;
import org.bytabit.ft.fxui.arbitrator.ContractsDialog;
import org.bytabit.ft.fxui.util.ActorController;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ResourceBundle;

public class ServersUI implements ActorController {

    private ServerManagerFxService ServerManagerFxService;
    private ArbitratorManagerFxService arbitratorManagerFxService;

    @FXML
    private ResourceBundle resources;

    @FXML
    private TableView<ArbitratorUIModel> eventClientTable;

    @FXML
    private TableColumn<ArbitratorUIModel, String> actionColumn;

    @FXML
    private TableColumn<ArbitratorUIModel, String> statusColumn;

    @FXML
    private TableColumn<ArbitratorUIModel, String> bondColumn;

    @FXML
    private TableColumn<ArbitratorUIModel, String> feeColumn;

    @FXML
    private TableColumn<ArbitratorUIModel, String> urlColumn;

    @FXML
    private TableColumn<ArbitratorUIModel, String> idColumn;

    @FXML
    private TextField urlTextField;

    @FXML
    private Button addArbitratorButton;

    private ActorSystem sys;

    public ServersUI(ActorSystem system) {
        sys = system;
        ServerManagerFxService = new ServerManagerFxService(system);
        ServerManagerFxService.start();

        arbitratorManagerFxService = new ArbitratorManagerFxService(system);
        arbitratorManagerFxService.start();
    }

    @FXML
    void initialize() {

        eventClientTable.setRowFactory(tv -> {
            TableRow row = new TableRow<ArbitratorUIModel>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && ((ArbitratorUIModel) row.getItem()).arbitrator().isDefined()) {
                    ArbitratorUIModel rowData = (ArbitratorUIModel) row.getItem();

                    ContractsDialog dialog = new ContractsDialog(arbitratorManagerFxService, rowData.arbitrator().get());
                    dialog.showAndWait();
                }
            });
            return row;
        });

        // setup event client table

        actionColumn.setCellValueFactory(a -> a.getValue().urlProperty());
        actionColumn.setCellFactory(column -> newTableCell());

        statusColumn.setCellValueFactory(a -> a.getValue().statusProperty());
        urlColumn.setCellValueFactory(a -> a.getValue().urlProperty());
        bondColumn.setCellValueFactory(a -> a.getValue().bondProperty());
        feeColumn.setCellValueFactory(a -> a.getValue().feeProperty());
        idColumn.setCellValueFactory(a -> a.getValue().idProperty());

        eventClientTable.setItems(ServerManagerFxService.arbitrators());
    }

    @FXML
    void handleAddArbitrator(ActionEvent event) {

        URL url = null;
        try {
            url = new URL(urlTextField.getText());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        if (url != null) ServerManagerFxService.addArbitrator(url);
    }

    private TableCell<ArbitratorUIModel, String> newTableCell() {

        return new TableCell<ArbitratorUIModel, String>() {

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
                    deleteButton.setOnAction(evt -> {
                        try {
                            ServerManagerFxService.removeArbitrator(new URL(item));
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    });
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
