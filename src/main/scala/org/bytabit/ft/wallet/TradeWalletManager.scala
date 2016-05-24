package org.bytabit.ft.wallet

import java.io.File
import java.net.URL

import akka.actor.{ActorSystem, Props}
import com.google.common.util.concurrent.Service.Listener
import org.bitcoinj.core._
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.wallet.{KeyChain, SendRequest}
import org.bytabit.ft.trade.model.{CertifyFiatEvidence, Offer, SellOffer, TakenOffer}
import org.bytabit.ft.util.{AESCipher, BTCMoney, Config, Monies}
import org.bytabit.ft.wallet.TradeWalletManager._
import org.bytabit.ft.wallet.WalletManager._
import org.bytabit.ft.wallet.model._
import org.joda.money.Money

import scala.collection.JavaConversions._
import scala.util.Try

object TradeWalletManager {

  val props = Props(new TradeWalletManager)
  val name = s"tradeWalletManager"

  def actorOf(system: ActorSystem) = system.actorOf(props, name)

  // wallet commands

  sealed trait Command

  case object Start extends Command

  case object FindTransactions extends Command

  case object FindBalance extends Command

  case class FindCurrentAddress(purpose: KeyChain.KeyPurpose) extends Command

  case class CreateArbitrator(url: URL, bondPercent: Double, btcNotaryFee: Money) extends Command

  case class CreateSellOffer(offer: Offer) extends Command

  case class TakeSellOffer(sellOffer: SellOffer, deliveryDetails: String) extends Command

  case class SignTakenOffer(takenOffer: TakenOffer) extends Command

  case class CertifyFiatSent(certifyFiatEvidence: CertifyFiatEvidence) extends Command

  case class CertifyFiatNotSent(certifyFiatEvidence: CertifyFiatEvidence) extends Command

  case class BroadcastTx(tx: Tx, escrowPubKey: Option[PubECKey] = None) extends Command

  case class WithdrawXBT(toAddress: String, amount: Money) extends Command

}

class TradeWalletManager extends WalletManager {

  val kit: WalletAppKit = new WalletAppKit(btcContext, new File(Config.walletDir), Config.config)

  val kitListener = new Listener {

    override def running(): Unit = {
      self ! TradeWalletRunning
    }
  }

  startWith(STARTING, Data(kit))

  when(STARTING) {

    case Event(Start, Data(k, wl, _)) =>
      startWallet(downloadProgressTracker)
      goto(STARTING) using Data(k, wl + sender)

    case Event(TradeWalletRunning, Data(k, wl, _)) =>
      Context.propagate(btcContext)
      k.wallet().addTransactionConfidenceEventListener(txConfidenceEventListener)
      sendToListeners(TradeWalletRunning, wl.toSeq)
      goto(RUNNING) using Data(k, wl)

    case Event(e: TransactionUpdated, Data(k, wl, al)) =>
      sendToListeners(e, wl.toSeq)
      stay()

    // handle block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      handleBlockChainEvents(e, d)
  }

  when(RUNNING) {

    case Event(FindBalance, Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val c = w.getBalance
      sender ! BalanceFound(c)
      goto(RUNNING) using Data(k, wl + sender, al)

    case Event(FindTransactions, Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val txs = w.getTransactions(false)
      txs.foreach(tx => sender ! TransactionUpdated(tx, tx.getValue(w),
        tx.getConfidence.getConfidenceType,
        tx.getConfidence.getDepthInBlocks))
      goto(RUNNING) using Data(k, wl + sender, al)

    case Event(FindCurrentAddress(p), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val a = w.currentAddress(p)
      log.debug(s"current wallet address: $a")
      sender ! CurrentAddressFound(a)
      stay()

    case Event(CreateArbitrator(u, bp, nf), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! ArbitratorCreated(Arbitrator(u, bp, nf)(w))
      stay()

    case Event(CreateSellOffer(offer: Offer), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! SellOfferCreated(offer.withSeller(w))
      stay()

    case Event(TakeSellOffer(sellOffer: SellOffer, deliveryDetails: String), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      val key = AESCipher.genRanData(AESCipher.AES_KEY_LEN)
      sender ! SellOfferTaken(sellOffer.take(deliveryDetails, key)(w))
      stay()

    case Event(SignTakenOffer(takenOffer: TakenOffer), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! TakenOfferSigned(takenOffer.sign(w))
      stay()

    case Event(CertifyFiatSent(fiatEvidence), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! FiatSentCertified(fiatEvidence.certifyFiatSent(w))
      stay()

    case Event(CertifyFiatNotSent(fiatEvidence), Data(k, wl, al)) =>
      Context.propagate(btcContext)
      val w = k.wallet
      sender ! FiatNotSentCertified(fiatEvidence.certifyFiatNotSent(w))
      stay()

    case Event(tu:TransactionUpdated, Data(w, wl, al)) =>
      sendToListeners(tu, wl.toSeq)
      stay()

    case Event(BroadcastTx(ot: OpenTx, None), Data(k, wl, al)) =>
      val w = k.wallet
      val bcTx = broadcastOpenTx(w, ot)
      sendToListeners(TxBroadcast(bcTx), wl.toSeq)
      stay()

    case Event(BroadcastTx(ft: FundTx, None), Data(k, wl, al)) =>
      val w = k.wallet
      val bcTx = broadcastFundTx(w, ft)
      sendToListeners(TxBroadcast(bcTx), wl.toSeq)
      stay()

    case Event(BroadcastTx(pt: PayoutTx, Some(pk: PubECKey)), Data(k, wl, al)) =>
      val w = k.wallet
      val bcTx = broadcastPayoutTx(w, pt, pk)
      sendToListeners(TxBroadcast(bcTx), wl.toSeq)
      stay()

    case Event(WithdrawXBT(withdrawAddress, withdrawAmount), Data(k, wl, al)) =>
      val w = k.wallet
      Context.propagate(btcContext)
      assert(Monies.isBTC(withdrawAmount))
      val coinAmt = BTCMoney.toCoin(withdrawAmount)
      val btcAddr: Option[Address] = Try(Address.fromBase58(netParams, withdrawAddress)).toOption
      if (btcAddr.isEmpty) log.error(s"Can't withdraw XBT, invalid address: $withdrawAddress")
      btcAddr.map { a =>
        val sr = SendRequest.to(a, coinAmt)
        sr.memo = s"Withdraw to $a"
        sr
      }.foreach(w.sendCoins)
      stay()

    // handle block chain events
    case Event(e: WalletManager.BlockChainEvent, d: WalletManager.Data) =>
      handleBlockChainEvents(e, d)

  }

  def handleBlockChainEvents(e: WalletManager.BlockChainEvent, d: WalletManager.Data): State = Event(e,d) match {

    case Event(e: BlockDownloaded, Data(k, wl, al)) =>
      //sendToListeners(e, wl.toSeq)
      stay()

    case Event(e: DownloadProgress, Data(k, wl, al)) =>
      sendToListeners(e, wl.toSeq)
      stay()

    case Event(DownloadDone, Data(k, wl, al)) =>
      sendToListeners(DownloadDone, wl.toSeq)
      stay()
  }

}
