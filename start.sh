#!/usr/bin/env bash
sbt clean compile
sbt '; set javaOptions += "-Dconfig.file=./src/test/resources/notary1.conf" ; runMain org.bytabit.ft.fxui.FiatTrader' > /dev/null &
sbt '; set javaOptions += "-Dconfig.file=./src/test/resources/trader1.conf" ; runMain org.bytabit.ft.fxui.FiatTrader' > /dev/null &
sbt '; set javaOptions += "-Dconfig.file=./src/test/resources/trader2.conf" ; runMain org.bytabit.ft.fxui.FiatTrader' > /dev/null &

