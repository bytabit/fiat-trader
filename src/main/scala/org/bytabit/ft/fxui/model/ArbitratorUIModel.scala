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

package org.bytabit.ft.fxui.model

import java.net.URL
import javafx.beans.property.SimpleStringProperty

import org.bytabit.ft.client.EventClient
import org.bytabit.ft.wallet.model.Arbitrator

case class ArbitratorUIModel(status: EventClient.State, url: URL, arbitrator: Option[Arbitrator]) {

  val statusProperty = new SimpleStringProperty(status.identifier)
  val urlProperty = new SimpleStringProperty(url.toString)
  val bondProperty = new SimpleStringProperty(f"${arbitrator.map(_.bondPercent).getOrElse(0.0) * 100}%f")
  val feeProperty = new SimpleStringProperty(arbitrator.map(_.btcArbitratorFee.toString).getOrElse(""))
  val idProperty = new SimpleStringProperty(arbitrator.map(_.id.toString).getOrElse(""))

  def getUrl = urlProperty.get
}
