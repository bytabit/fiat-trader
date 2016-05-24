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

import javafx.beans.property.{SimpleIntegerProperty, SimpleStringProperty}

import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionConfidence.ConfidenceType
import org.joda.money.Money
import org.joda.time.LocalDateTime

object TransactionUIModel {
  def apply(tx: Transaction, btcAmt: Money, ct:ConfidenceType, cd:Int) = {
    new TransactionUIModel(tx.getHash.toString, ct.toString, cd, LocalDateTime.fromDateFields(tx.getUpdateTime), tx.getMemo, btcAmt)
  }
}

class TransactionUIModel(hash: String, confidenceType: String, depth: Integer, date: LocalDateTime, memo: String,
                         btcAmt: Money) {

  val hashProperty = new SimpleStringProperty(hash)
  val confidenceTypeProperty = new SimpleStringProperty(confidenceType)
  val depthProperty = new SimpleIntegerProperty(depth)
  val dateProperty = new SimpleStringProperty(date.toString)
  val memoProperty = new SimpleStringProperty(memo)
  val btcAmtProperty = new SimpleStringProperty(btcAmt.toString)

  def getHash = hashProperty.get
}