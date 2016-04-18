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

package org.bytabit.ft.util

import java.net.URL

import com.typesafe.config.ConfigFactory

object Config {

  val appConfig = ConfigFactory.load()

  val configRoot = "bytabit.fiat-trader"

  // app configs

  val home = getString("user.home").getOrElse("./")
  val config = getString(s"$configRoot.config").getOrElse("default")
  val version = getString(s"$configRoot.version").getOrElse("0.0.0")

  // wallet configs

  val walletNet = getString(s"$configRoot.wallet.net").getOrElse("org.bitcoin.test")
  val walletDir = getString(s"$configRoot.wallet.dir").getOrElse(s"$home/.bytabit/$config/wallet")

  // arbitrator configs

  val arbitratorEnabled = getBoolean(s"$configRoot.arbitrator.enabled").getOrElse(false)

  val bondPercent = getDouble(s"$configRoot.arbitrator.bond-percent").getOrElse(0.0)
  val btcArbitratorFee = getDouble(s"$configRoot.arbitrator.btc-arbitrator-fee").getOrElse(0.0)

  // server configs

  val serverEnabled = getBoolean(s"$configRoot.server.enabled").getOrElse(false)

  val localAddress = getString(s"$configRoot.server.local-address").getOrElse("0.0.0.0")
  val localPort = getInt(s"$configRoot.server.local-port").getOrElse(9000)
  val localProtocol = getString(s"$configRoot.server.local-protocol").getOrElse("http")

  val publicAddress = getString(s"$configRoot.server.public-address").getOrElse("127.0.0.1")
  val publicPort = getInt(s"$configRoot.server.public-port").getOrElse(9000)
  val publicProtocol = getString(s"$configRoot.server.public-protocol").getOrElse("http")

  val publicUrl = new URL(s"$publicProtocol://$publicAddress:$publicPort")

  // helper functions

  def getString(key: String): Option[String] = {
    if (appConfig.hasPath(key)) Some(appConfig.getString(key)) else None
  }

  def getBoolean(key: String): Option[Boolean] = {
    if (appConfig.hasPath(key)) Some(appConfig.getBoolean(key)) else None
  }

  def getInt(key: String): Option[Int] = {
    if (appConfig.hasPath(key)) Some(appConfig.getInt(key)) else None
  }

  def getDouble(key: String): Option[Double] = {
    if (appConfig.hasPath(key)) Some(appConfig.getDouble(key)) else None
  }
}
