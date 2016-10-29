/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.bytabit.ft.util

import java.io.File
import java.net.URL

import com.typesafe.config.ConfigFactory

import scala.util.Try

trait Config {

  def filesDir: Option[File]

  val appConfig = ConfigFactory.load()

  val configRoot = "bytabit.fiat-trader"

  // app configs

  val configName = getString(s"$configRoot.configName", "default")
  val version = getString(s"$configRoot.version", "0.0.0")

  // persistence configs

  val akkaPersistence = "akka.persistence"

  def snapshotStoreDir = getFile(s"$akkaPersistence.snapshot-store.local.dir", s"$configName/snapshots")

  def journalDir = getFile(s"$akkaPersistence.journal.leveldb.dir", s"$configName/journal")

  // wallet configs

  val walletNet = getString(s"$configRoot.wallet.net", "org.bitcoin.test")
  val walletDir = getFile(s"$configRoot.wallet.dir", s"$configName/wallet")

  // arbitrator configs

  val arbitratorEnabled = getBoolean(s"$configRoot.arbitrator.enabled", default = false)

  val bondPercent = getDouble(s"$configRoot.arbitrator.bond-percent", 0.0)
  val btcArbitratorFee = getDouble(s"$configRoot.arbitrator.btc-arbitrator-fee", 0.0)

  // server configs

  val serverEnabled = getBoolean(s"$configRoot.server.enabled", default = false)

  val localAddress = getString(s"$configRoot.server.local-address", "0.0.0.0")
  val localPort = getInt(s"$configRoot.server.local-port", 9000)
  val localProtocol = getString(s"$configRoot.server.local-protocol", "http")

  val publicAddress = getString(s"$configRoot.server.public-address", "127.0.0.1")
  val publicPort = getInt(s"$configRoot.server.public-port", 9000)
  val publicProtocol = getString(s"$configRoot.server.public-protocol", "http")

  val publicUrl = new URL(s"$publicProtocol://$publicAddress:$publicPort")

  // helper functions

  def getString(key: String, default: String): String = {
    if (appConfig.hasPath(key)) appConfig.getString(key) else default
  }

  def getBoolean(key: String, default: Boolean): Boolean = {
    if (appConfig.hasPath(key)) appConfig.getBoolean(key) else default
  }

  def getInt(key: String, default: Int): Int = {
    if (appConfig.hasPath(key)) appConfig.getInt(key) else default
  }

  def getDouble(key: String, default: Double): Double = {
    if (appConfig.hasPath(key)) appConfig.getDouble(key) else default
  }

  def getFile(key: String, default: String): Option[File] = {
    if (filesDir.isDefined) {
      if (appConfig.hasPath(key))
        Some(new File(filesDir.get, appConfig.getString(key)))
      else
        Some(new File(filesDir.get, default))
    } else None
  }

  // Create data directories if not existing
  def createDir(dir: File): Try[File] = Try {

    if (dir.exists && dir.isDirectory) {
      dir
    } else if (dir.mkdirs) {
      dir
    } else {
      throw new SecurityException(s"Unable to create directory: ${dir.toString}")
    }
  }
}
