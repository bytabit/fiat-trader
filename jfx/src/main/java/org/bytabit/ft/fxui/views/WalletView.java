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

package org.bytabit.ft.fxui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;

import java.io.IOException;

public class WalletView {

    private final String name;

    public WalletView(String name) {
        this.name = name;
    }

    public View getView() {
        try {
            View view = FXMLLoader.load(WalletView.class.getResource("wallet.fxml"));
            view.setName(name);
            return view;
        } catch (IOException e) {
            System.out.println("IOException: " + e);
            return new View(name);
        }
    }
}
