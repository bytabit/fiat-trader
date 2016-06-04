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

package org.bytabit.ft.fxui

import java.util.ResourceBundle
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.{FXCollections, ObservableList}
import javafx.event.ActionEvent
import javafx.fxml.{FXML, FXMLLoader}
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.TableColumn.CellDataFeatures
import javafx.scene.control._
import javafx.util.Callback

import akka.actor.ActorSystem
import org.bitcoinj.core.Sha256Hash
import org.bytabit.ft.fxui.model.TradeUIModel
import org.bytabit.ft.fxui.util.ActorController
import org.bytabit.ft.trade.TradeProcess
import org.bytabit.ft.trade.TradeProcess.{BUYER_REFUNDED, BTCBUYER_FUNDED}
import org.bytabit.ft.trade.model._
import org.bytabit.ft.util.{BTCMoney, Monies}
import org.joda.money.Money
import org.joda.time.DateTime

import scala.language.implicitConversions

case class EscrowDetailUIModel(date: DateTime, memo: String, txHash: Sha256Hash, deposit: Option[Money],
                               withdraw: Option[Money], balance: Money) {

  deposit.foreach { d => assert(Monies.isBTC(d)) }
  withdraw.foreach { w => assert(Monies.isBTC(w)) }
  assert(Monies.isBTC(balance))

  val dateProperty = new SimpleStringProperty(date.toString)
  val memoProperty = new SimpleStringProperty(memo)
  val txHashProperty = new SimpleStringProperty(txHash.toString)
  val depositProperty = new SimpleStringProperty(deposit match {
    case Some(d) => d.toString
    case None => ""
  })
  val withdrawProperty = new SimpleStringProperty(withdraw match {
    case Some(w) => w.toString
    case None => ""
  })

  val balanceProperty = new SimpleStringProperty(balance.toString)

  def deposit(memo: String, amount: Money) = {
    EscrowDetailUIModel(date, memo, txHash, Some(amount), None, balance.plus(amount))
  }

  def withdraw(memo: String, amount: Money) = {
    EscrowDetailUIModel(date, memo, txHash, None, Some(amount), balance.minus(amount))
  }
}

object FxTableHelper {

  implicit def cellValueFactory(cb: CellDataFeatures[EscrowDetailUIModel, String] => ObservableValue[String]): Callback[TableColumn.CellDataFeatures[EscrowDetailUIModel, String], ObservableValue[String]] =
    new Callback[TableColumn.CellDataFeatures[EscrowDetailUIModel, String], ObservableValue[String]] {
      override def call(d: CellDataFeatures[EscrowDetailUIModel, String]): ObservableValue[String] = cb(d)
    }
}

case class TradeInfoDialog(system: ActorSystem, tm: TradeUIModel) extends Alert(AlertType.INFORMATION) with ActorController {

  @FXML
  private var resources: ResourceBundle = null

  @FXML
  private var fiatAmountLabel: Label = null

  @FXML
  private var exchRateLabel: Label = null

  @FXML
  private var btcAmountLabel: Label = null

  @FXML
  private var arbitratorFeeLabel: Label = null

  @FXML
  private var bondPercentLabel: Label = null

  @FXML
  private var bondAmountLabel: Label = null

  @FXML
  private var btcTxFeeLabel: Label = null

  @FXML
  private var paymentMethodLabel: Label = null

  @FXML
  private var paymentDetailsLabel: Label = null

  @FXML
  private var escrowAddressLabel: Label = null

  @FXML
  private var escrowDetailsTable: TableView[EscrowDetailUIModel] = null

  @FXML
  private var dateColumn: TableColumn[EscrowDetailUIModel, String] = null

  @FXML
  private var memoColumn: TableColumn[EscrowDetailUIModel, String] = null

  @FXML
  private var txHashColumn: TableColumn[EscrowDetailUIModel, String] = null

  @FXML
  private var depositColumn: TableColumn[EscrowDetailUIModel, String] = null

  @FXML
  private var withdrawColumn: TableColumn[EscrowDetailUIModel, String] = null

  @FXML
  private var balanceColumn: TableColumn[EscrowDetailUIModel, String] = null

  val escrowDetails: ObservableList[EscrowDetailUIModel] = FXCollections.observableArrayList[EscrowDetailUIModel]

  val fxmlLoader = new FXMLLoader()
  fxmlLoader.setLocation(classOf[FiatTrader].getResource("/org/bytabit/ft/fxui/trade/TradeInfoDialog.fxml"))
  fxmlLoader.setController(this)

  getDialogPane.setContent(fxmlLoader.load())

  @FXML
  private def initialize(): Unit = {

    import FxTableHelper._

    escrowDetailsTable.setItems(escrowDetails)

    dateColumn.setCellValueFactory((d: CellDataFeatures[EscrowDetailUIModel, String]) => d.getValue.dateProperty)
    memoColumn.setCellValueFactory((d: CellDataFeatures[EscrowDetailUIModel, String]) => d.getValue.memoProperty)
    txHashColumn.setCellValueFactory((d: CellDataFeatures[EscrowDetailUIModel, String]) => d.getValue.txHashProperty)
    depositColumn.setCellValueFactory((d: CellDataFeatures[EscrowDetailUIModel, String]) => d.getValue.depositProperty)
    withdrawColumn.setCellValueFactory((d: CellDataFeatures[EscrowDetailUIModel, String]) => d.getValue.withdrawProperty)
    balanceColumn.setCellValueFactory((d: CellDataFeatures[EscrowDetailUIModel, String]) => d.getValue.balanceProperty)

    setTitle(tm.getId.toString)
    setHeaderText("Trade Information")

    btcTxFeeLabel.textProperty().setValue(tm.trade.btcMinerFee.toString)
    paymentMethodLabel.textProperty().setValue(tm.paymentMethod.name)
    fiatAmountLabel.textProperty().setValue(tm.fiatAmount.toString)
    exchRateLabel.textProperty().setValue(tm.exchangeRate.toString)
    btcAmountLabel.textProperty().setValue(tm.btcAmount.toString)
    arbitratorFeeLabel.textProperty().setValue(tm.arbitratorFee.toString)
    bondPercentLabel.textProperty().setValue(f"${tm.bondPercent * 100}%%")
    val bondAmount = tm.btcAmount.multipliedBy(tm.bondPercent, Monies.roundingMode)
    bondAmountLabel.textProperty().setValue(bondAmount.toString)

    tm.trade match {

      // common path

      case to: TakenOffer =>
        setEscrowAddress(to)

      case so: SignedTakenOffer =>
        setEscrowAddress(so.takenOffer)

      case ot: OpenedTrade =>
        setEscrowAddress(ot.signedTakenOffer.takenOffer)
        addEscrowTxHistory(tm.state, ot, None, None, None)

      case ft: FundedTrade =>
        setPaymentDetails(ft)
        setEscrowAddress(ft.openedTrade.signedTakenOffer.takenOffer)
        addEscrowTxHistory(tm.state, ft.openedTrade, Some(ft), None, None)

      // happy path

      case st: SettledTrade =>
        setEscrowAddress(st.fundedTrade.openedTrade.signedTakenOffer.takenOffer)
        val ft = st.fundedTrade
        val ot = ft.openedTrade
        setPaymentDetails(st.fundedTrade)
        addEscrowTxHistory(tm.state, ot, Some(ft), Some(st), None)

      // unhappy path

      case ce: CertifyPaymentEvidence =>
        val ft = ce.fundedTrade
        setPaymentDetails(ft)
        setEscrowAddress(ft.openedTrade.signedTakenOffer.takenOffer)
        addEscrowTxHistory(tm.state, ft.openedTrade, Some(ft), None, None)

      case cp: CertifiedPayment =>
        val ft = cp.certifyPaymentEvidence.fundedTrade
        setPaymentDetails(ft)
        setEscrowAddress(ft.openedTrade.signedTakenOffer.takenOffer)
        addEscrowTxHistory(tm.state, ft.openedTrade, Some(ft), None, None)

      case cs: CertifiedSettledTrade =>
        val ce = cs.certifiedPayment.certifyPaymentEvidence
        setEscrowAddress(ce.fundedTrade.openedTrade.signedTakenOffer.takenOffer)
        val ft = ce.fundedTrade
        val ot = ft.openedTrade
        setPaymentDetails(ce.fundedTrade)
        addEscrowTxHistory(tm.state, ot, Some(ft), None, Some(cs))

      case _ =>
      // do nothing
    }
  }

  def setEscrowAddress(to: TakenOffer) = {
    escrowAddressLabel.textProperty().setValue(to.escrowAddress.toString)
  }

  def setPaymentDetails(ft: FundedTrade) = {
    paymentDetailsLabel.textProperty.setValue(ft.paymentDetails)
  }


  def addEscrowTxHistory(s: TradeProcess.State, ot: OpenedTrade, ft: Option[FundedTrade], st: Option[SettledTrade],
                         cs: Option[CertifiedSettledTrade]) = {

    val TX_FEE_MSG = "Transaction fee to BTC network"

    var balance: Money = BTCMoney(0)

    // open escrow tx details
    val btcBuyerOpenDetail = EscrowDetailUIModel(ot.openTxUpdateTime, "BTC buyer bond, arbitrator and TX fees to open escrow",
      ot.openTxHash, Some(ot.btcToOpenEscrow), None, ot.btcToOpenEscrow)
    val buyerOpenDetail = btcBuyerOpenDetail.deposit("Buyer bond, arbitrator and TX fees to open escrow", ot.btcToOpenEscrow)
    val minerFee1Detail = buyerOpenDetail.withdraw(TX_FEE_MSG, ot.btcMinerFee)
    balance = minerFee1Detail.balance

    escrowDetails.add(btcBuyerOpenDetail)
    escrowDetails.add(buyerOpenDetail)
    escrowDetails.add(minerFee1Detail)

    // fund escrow tx details
    ft.foreach { t =>
      val buyerFundDetail = EscrowDetailUIModel(t.fundTxUpdateTime, "Buyer trade amount and TX fee to fund escrow",
        t.fundTxHash, Some(t.btcToFundEscrow), None, t.btcToFundEscrow.plus(balance))
      val minerFee2Detail = buyerFundDetail.withdraw(TX_FEE_MSG, ot.btcMinerFee)
      balance = minerFee2Detail.balance

      escrowDetails.add(buyerFundDetail)
      escrowDetails.add(minerFee2Detail)
    }

    // settle escrow tx details
    st.foreach { t =>
      val ft = t.fundedTrade
      val btcBuyerPayoutDetail = EscrowDetailUIModel(t.payoutTxUpdateTime, "Trade amount, bond and arbitrator fee to BTC Buyer",
        t.payoutTxHash, None, Some(ft.btcBuyer2Payout), balance.minus(ft.btcBuyer2Payout))
      val buyerPayoutDetail = btcBuyerPayoutDetail.withdraw("Bond and arbitrator fee to buyer", ft.btcBuyerPayout)
      val minerFee3Detail = buyerPayoutDetail.withdraw(TX_FEE_MSG, ot.btcMinerFee)
      balance = minerFee3Detail.balance
      escrowDetails.add(btcBuyerPayoutDetail)
      escrowDetails.add(buyerPayoutDetail)
      escrowDetails.add(minerFee3Detail)
    }

    // certified settle escrow tx details
    cs.foreach { t =>
      val ft = t.certifiedPayment.certifyPaymentEvidence.fundedTrade
      val payoutTo = s match {
        case BTCBUYER_FUNDED => "btc buyer"
        case BUYER_REFUNDED => "buyer"
        case _ => "ERROR"
      }
      val traderPayoutDetail = EscrowDetailUIModel(t.payoutTxUpdateTime, s"Trade amount, arbitrator fee, both bonds to $payoutTo",
        t.payoutTxHash, None, Some(ft.btcDisputeWinnerPayout), balance.minus(ft.btcDisputeWinnerPayout))
      val arbitratorPayoutDetail = traderPayoutDetail.withdraw("Arbitrator fee to arbitrator", ft.btcArbitratorFee)
      val minerFee3Detail = arbitratorPayoutDetail.withdraw(TX_FEE_MSG, ot.btcMinerFee)
      escrowDetails.add(traderPayoutDetail)
      escrowDetails.add(arbitratorPayoutDetail)
      escrowDetails.add(minerFee3Detail)
    }
  }

  @FXML
  def okButtonAction(event: ActionEvent): Unit = {
    close()
  }

  override def log = system.log
}
