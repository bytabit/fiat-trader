[![wercker status](https://app.wercker.com/status/4b45baa4a18cf289674fff2d3db7079a/s/master "wercker status")](https://app.wercker.com/project/bykey/4b45baa4a18cf289674fff2d3db7079a)

Bytabit Fiat Trader
===================

### Clone Project

```
git clone git@bitbucket.org:bytabit/fiat-trader.git 
```

### Run Client with Gradle From Command Line using Default (testnet) Config

1. Install [JDK 8u73](https://jdk8.java.net/download.html)
2. Install [Scala version  2.11.5](http://www.scala-lang.org/download/)
3. Install [Gradle version 2.12](https://gradle.org/gradle-download/)
4. Verify your JAVA_HOME environment variable is set to your JDK home
5. From the project directory run command:

```
gradle run
```

### Run Client with IntelliJ using Custom Config 

```
Main class: org.bytabit.ft.fxui.FiatTrader
VM options: -Dconfig.file=./src/test/resources/arbitrator1-regtest.conf
Working directory: <project directory>
Use classpath of module: fiat-trader
```

### Run Event Servers with Gradle From Command Line using Custom Config

1. From the project directory run command:

```
gradle run gradle run -Dconfig.file=./src/test/resources/server1-regtest.conf
```

### Run Event Servers with IntelliJ using Custom Configs 

```
Main class: org.bytabit.fxui.FiatTrader
VM options: -Dconfig.file=./src/test/resources/server1-regtest.conf
Working directory: <project directory>
Use classpath of module: fiat-trader
```

### IntelliJ Setup

1. Install scala and gradle plugins (if not already installed)
2. Create IntelliJ configs from project directory with gradle command: ```gradle idea```
3. Open project in IntelliJ
4. Verify the project JDK and Java Inspections settings are correct

### JavaFX Scene Builder

1. Install [JavaFX Scene Builder 2.0](http://www.oracle.com/technetwork/java/javase/downloads/index.html). Find it under “Additional Resources”.
2. Open main UI file: ```src/main/java/org/bytabit/ft/fxui/MainUI.fxml```

### Testnet In a Box

1. Clone bitcoin-testnet-box repo
    
    ```
    git clone https://github.com/freewil/bitcoin-testnet-box.git
    ```

2. Change 1/bitcoin.conf port to 18444  
    
    ```
    port=18444
    ```

3. Follow README.md instructions

### Versioning

We follow the [Semantic Versioning 2.0](http://semver.org/spec/v2.0.0.html) specification for this project.