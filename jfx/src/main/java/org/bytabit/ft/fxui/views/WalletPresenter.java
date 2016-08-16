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
import javafx.fxml.FXML;

public class WalletPresenter {

    @FXML
    private View secondary;

    public void initialize() {
//        secondary.setShowTransitionFactory(BounceInRightTransition::new);

//        secondary.getLayers().add(new FloatingActionButton(MaterialDesignIcon.INFO.text,
//                e -> System.out.println("Info")));

//        secondary.showingProperty().addListener((obs, oldValue, newValue) -> {
//            if (newValue) {
//                AppBar appBar = MobileApplication.getInstance().getAppBar();
//                appBar.setNavIcon(MaterialDesignIcon.MENU.button(e ->
//                        MobileApplication.getInstance().showLayer(FiatTrader.MENU_LAYER)));
//                appBar.setTitleText("Wallet");
//                appBar.getActionItems().add(MaterialDesignIcon.FAVORITE.button(e ->
//                        System.out.println("Favorite")));
//            }
//        });
    }
}
