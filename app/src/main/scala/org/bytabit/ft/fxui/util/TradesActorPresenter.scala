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
 *//*
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
package org.bytabit.ft.fxui.util

import java.util.ResourceBundle
import javafx.fxml.FXML

import com.gluonhq.charm.glisten.mvc.View
import org.bytabit.ft.fxui.trade.TradeFxService

trait TradesActorPresenter extends ActorPresenter {

  protected var tradeFxService: TradeFxService = _
  @FXML protected var resources: ResourceBundle = _
  @FXML protected var tradesView: View = _
  //  @FXML protected var tradesListView: CharmListView[TradeUIModel, Money] = _

  @SuppressWarnings(Array("unchecked"))
  @FXML protected def initialize() {
    // setup trades list view
    //        tradesListView.setCellFactory(tm -> new CharmListCell<TradeUIModel>() {
    //            @Override
    //            public void updateItem(TradeUIModel item, boolean empty) {
    //                super.updateItem(item, empty);
    //                if (item != null && !empty) {
    //                    ListTile tile = new ListTile();
    //                    Money exchangeRate = item.fiatAmount().dividedBy(item.btcAmount().getAmount(), Monies.roundingMode());
    //                    tile.textProperty().addAll(item.role().toString() + " offer for " +
    //                            item.btcAmount().toString(), "@ " + exchangeRate.toString() + " per XBT" +
    //                            " = " + item.fiatAmount().toString() + " via " + item.paymentMethod().name());
    //                    setText(null);
    //                    setGraphic(tile);
    //                } else {
    //                    setText(null);
    //                    setGraphic(null);
    //                }
    //            }
    //        });
    //    tradesListView.setItems(tradeFxService.trades)
  }
}