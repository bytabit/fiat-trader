/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

//package akka.dispatch
//
//import com.typesafe.config.Config
//import akka.util.Helpers.ConfigOps
//
///**
//  * Configurator for creating [[akka.dispatch.AndroidDispatcher]].
//  * Returns the same dispatcher instance for for each invocation
//  * of the `dispatcher()` method.
//  */
//class AndroidMessageDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
//  extends MessageDispatcherConfigurator(config, prerequisites) {
//
//  private val instance = new AndroidDispatcher(
//    this,
//    config.getString("id"),
//    config.getInt("throughput"),
//    config.getNanosDuration("throughput-deadline-time"),
//    configureExecutor(),
//    config.getMillisDuration("shutdown-timeout"))
//
//  /**
//    * Returns the same dispatcher instance for each invocation
//    */
//  override def dispatcher(): MessageDispatcher = instance
//}
