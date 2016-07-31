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

import akka.actor.ActorSystem;
import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;
import org.bytabit.ft.fxui.util.ActorPresenterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TradeView {

    private final String name;
    private final ActorSystem system;
    private final Logger log;

    public TradeView(String name, ActorSystem system) {
        this.name = name;
        this.system = system;
        this.log = LoggerFactory.getLogger(TradeView.class);
    }

    public View getView() {
        try {
            FXMLLoader loader = new FXMLLoader(TradeView.class.getResource("trade.fxml"));
            loader.setControllerFactory(new ActorPresenterFactory(system));
            View view = loader.load();
            view.setName(name);
            return view;
        } catch (IOException e) {
            log.error("IOException: " + e);
            return new View(name);
        }
    }
}
