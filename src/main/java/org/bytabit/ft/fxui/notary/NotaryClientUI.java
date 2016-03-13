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

package org.bytabit.ft.fxui.notary;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.bytabit.ft.fxui.NotaryClientFxService;
import org.bytabit.ft.fxui.model.NotaryUIModel;
import org.bytabit.ft.fxui.util.ActorController;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ResourceBundle;

public class NotaryClientUI implements ActorController {

    private NotaryClientFxService notaryClientFxService;

    @FXML
    private ResourceBundle resources;


    @FXML
    private TableView<NotaryUIModel> notariesTable;

    @FXML
    private TableColumn<NotaryUIModel, String> actionColumn;

    @FXML
    private TableColumn<NotaryUIModel, String> statusColumn;

    @FXML
    private TableColumn<NotaryUIModel, String> bondColumn;

    @FXML
    private TableColumn<NotaryUIModel, String> feeColumn;

    @FXML
    private TableColumn<NotaryUIModel, String> urlColumn;

    @FXML
    private TableColumn<NotaryUIModel, String> idColumn;

    @FXML
    private TextField urlTextField;

    @FXML
    private Button addNotaryButton;

    private ActorSystem sys;

    public NotaryClientUI(ActorSystem system) {
        sys = system;
        notaryClientFxService = new NotaryClientFxService(system);
        notaryClientFxService.start();
    }

    @FXML
    void initialize() {

        // setup notary table
        actionColumn.setCellValueFactory(a -> a.getValue().urlProperty());
        actionColumn.setCellFactory(column -> newTableCell());

        statusColumn.setCellValueFactory(a -> a.getValue().statusProperty());
        urlColumn.setCellValueFactory(a -> a.getValue().urlProperty());
        bondColumn.setCellValueFactory(a -> a.getValue().bondProperty());
        feeColumn.setCellValueFactory(a -> a.getValue().feeProperty());
        idColumn.setCellValueFactory(a -> a.getValue().idProperty());

        notariesTable.setItems(notaryClientFxService.notaries());
    }

    @FXML
    void handleAddNotary(ActionEvent event) {

        URL url = null;
        try {
            url = new URL(urlTextField.getText());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        if (url != null) notaryClientFxService.addNotary(url);
    }

    private TableCell<NotaryUIModel, String> newTableCell() {

        return new TableCell<NotaryUIModel, String>() {

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
                            notaryClientFxService.removeNotary(new URL(item));
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
