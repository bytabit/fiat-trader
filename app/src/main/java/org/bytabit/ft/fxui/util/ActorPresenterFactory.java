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

package org.bytabit.ft.fxui.util;

import akka.actor.ActorSystem;
import javafx.util.Callback;

public class ActorPresenterFactory implements Callback<Class<?>, Object> {

    final private ActorSystem system;

    public ActorPresenterFactory(ActorSystem system) {
        this.system = system;
    }

    @Override
    public Object call(Class<?> aClass) {

        try {
            Object obj;
            if (ActorPresenter.class.isAssignableFrom(aClass)) {
                obj = aClass.getDeclaredConstructor(ActorSystem.class).newInstance(system);
            } else {
                obj = aClass.newInstance();
            }
            return obj;
        } catch (Exception exc) {
            exc.printStackTrace();
            return null;
        }
    }
}