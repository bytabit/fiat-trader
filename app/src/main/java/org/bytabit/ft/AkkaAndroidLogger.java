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

package org.bytabit.ft;

import android.util.Log;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.Logging.Debug;
import akka.event.Logging.Error;
import akka.event.Logging.Info;
import akka.event.Logging.InitializeLogger;
import akka.event.Logging.Warning;

class AkkaAndroidLogger extends UntypedActor {

    public void onReceive(Object message) throws Exception {
        if (message instanceof Error) {
            Error e = (Error) message;
            Log.e(Constants.CORE_LOG, String.format("[%s] %s: %s", e.logSource(), e.message(), e.cause()));
        } else if (message instanceof Warning) {
            Warning w = (Warning) message;
            Log.e(Constants.CORE_LOG, String.format("[%s] %s", w.logSource(), w.message()));
        } else if (message instanceof Info) {
            Info i = (Info) message;
            Log.e(Constants.CORE_LOG, String.format("[%s] %s", i.logSource(), i.message()));
        } else if (message instanceof Debug) {
            Debug d = (Debug) message;
            Log.e(Constants.CORE_LOG, String.format("[%s] %s", d.logSource(), d.message()));
        } else if (message instanceof InitializeLogger) {
            Log.d(Constants.CORE_LOG, "Logging started.");
            getSender().tell(Logging.loggerInitialized(), getSelf());
        } else {
            unhandled(message);
        }
    }
}