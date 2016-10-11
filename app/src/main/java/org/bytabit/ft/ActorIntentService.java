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


import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.bytabit.ft.client.ClientManager;
import org.bytabit.ft.util.Config;

import akka.actor.ActorSystem;
import scala.concurrent.duration.Duration;

public class ActorIntentService extends Service {

    private ActorSystem actorSystem;
    private Config config;

    public ActorIntentService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Create config
        config = new Config(getFilesDir(), getCacheDir());

        // Create actor system
        actorSystem = ActorSystem.create(config.configName());

        // create data directories if they don't exist
        if (config.createDir(config.snapshotStoreDir()).isFailure()) {
            Log.e(Constants.APP_LOG, "Unable to create snapshot directory.");
        }
        if (config.createDir(config.journalDir()).isFailure()) {
            Log.e(Constants.APP_LOG, "Unable to create journal directory.");
        }
        if (config.createDir(config.walletDir()).isFailure()) {
            Log.e(Constants.APP_LOG, "Unable to create wallet directory.");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ClientManager.actorOf(actorSystem, config);
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {

        // Shutdown Actors
        actorSystem.shutdown();
        try {
            actorSystem.awaitTermination(Duration.create(1, "second"));
        } catch (Exception e1) {
            Log.e(Constants.APP_LOG, String.format("Exception during shutdown [%s]:%s", e1.getMessage(), e1.getCause()));
        }
        super.onDestroy();
    }


}
