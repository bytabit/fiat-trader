[![wercker status](https://app.wercker.com/status/4b45baa4a18cf289674fff2d3db7079a/s/master "wercker status")](https://app.wercker.com/project/bykey/4b45baa4a18cf289674fff2d3db7079a) 
[![Download](https://api.bintray.com/packages/bytabit/generic/fiat-trader/images/download.svg) ](https://bintray.com/bytabit/generic/fiat-trader/_latestVersion)

Bytabit Fiat Trader
===================

### Clone Project

```
git clone git@bitbucket.org:bytabit/fiat-trader.git 
```

### Install projects dependencies

1. Install [JDK 8u92](https://jdk8.java.net/download.html)
2. Install [Scala version  2.11.8](http://www.scala-lang.org/download/)
3. Install [Gradle version 2.8](https://gradle.org/gradle-download/)
4. Verify your JAVA_HOME environment variable is set to your JDK home

### Run event server with Gradle using custom config

```
gradle server -Dconfig.file=./src/test/resources/server1-regtest.conf
```

### Run trader client with Gradle using default (testnet) config

```
gradle run
```

### Run arbitrator client on regtest network with Grade using custom config 

```
gradle run -Dconfig.file=./src/test/resources/arbitrator1-regtest.conf
```

### Run trader client on regtest network with Gradle using custom config 

```
gradle run -Dconfig.file=./src/test/resources/trader1-regtest.conf
```

### IntelliJ Setup

1. Install scala and gradle plugins (if not already installed)
2. Import gradle project in IntelliJ
3. Verify the project JDK and Java Inspections settings are correct

### JavaFX Scene Builder

1. Install [JavaFX Scene Builder 2.0](http://www.oracle.com/technetwork/java/javase/downloads/index.html). Find it under “Additional Resources”.
2. Open main UI file: ```src/main/java/org/bytabit/ft/fxui/MainUI.fxml```

### Testnet In a Box 

1. Clone bitcoin-testnet-box project
    
    ```
    git clone https://github.com/freewil/bitcoin-testnet-box.git
    ```

2. Start bitcoin-testnet-box, see bitcoin-testnet-box [README.md](https://github.com/freewil/bitcoin-testnet-box) instructions

3. Reverse map Anddroid Studio device emulator VM port 18444 to localhost bitcoin-testnet-box port 19000 
    
    ```
    ~/Library/Android/sdk/platform-tools/adb reverse tcp:18444 tcp:19000
    ```

Note: do not use the bitcoin-testnet-box docker image, docker can not be run on the same machine with Android Studio device emulator VM

### Versioning

We follow the [Semantic Versioning 2.0](http://semver.org/spec/v2.0.0.html) specification for this project.