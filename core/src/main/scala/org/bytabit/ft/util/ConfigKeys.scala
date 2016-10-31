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

object ConfigKeys {

  val CONFIG_ROOT = "bytabit.fiat-trader"

  // app configs

  val CONFIG_NAME = s"$CONFIG_ROOT.configName"
  val VERSION = s"$CONFIG_ROOT.version"

  // wallet configs

  val WALLET_NET = s"$CONFIG_ROOT.wallet.net"
  val WALLET_DIR = s"$CONFIG_ROOT.wallet.dir"

  // arbitrator configs

  val ARBITRATOR_ENABLED = s"$CONFIG_ROOT.arbitrator.enabled"
  val BOND_PERCENT = s"$CONFIG_ROOT.arbitrator.bond-percent"
  val ARBITRATOR_FEE = s"$CONFIG_ROOT.arbitrator.btc-arbitrator-fee"

  val PUBLIC_ADDRESS = s"$CONFIG_ROOT.server.public-address"
  val PUBLIC_PORT = s"$CONFIG_ROOT.server.public-port"
  val PUBLIC_PROTOCOL = s"$CONFIG_ROOT.server.public-protocol"
}
