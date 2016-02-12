Bytabit Fiat Trader
===================

### Clone Project

```
git clone git@bitbucket.org:bytabit/fiat-trader.git 
```

### Run From Command Line

1. Install [JDK 8u66](https://jdk8.java.net/download.html)
2. Install [Scala version  2.11.5](http://www.scala-lang.org/download/)
3. Install [SBT version 0.13.7](http://www.scala-sbt.org/download.html)
4. Verify your JAVA_HOME environment variable is set to your JDK home
5. From the project directory run command:

```
sbt run
```

### Run From Command Line with Custom Config

1. From the project directory run command:

```
sbt '; set javaOptions += "-Dconfig.file=./src/test/resources/notary1.conf" ; runMain org.bytabit.ft.fxui.FiatTrader'
sbt '; set javaOptions += "-Dconfig.file=./src/test/resources/trader1.conf" ; runMain org.bytabit.ft.fxui.FiatTrader'
```

### Run From IntelliJ with Custom Config 

```
Main class: org.bytabit.ft.fxui.FiatTrader
VM options: -Dconfig.file=./src/test/resources/notary1.conf
Working directory: <project directory>
Use classpath of module: fiat-trader
```

```
Main class: org.bytabit.fxui.FiatTrader
VM options: -Dconfig.file=./src/test/resources/trader1.conf
Working directory: <project directory>
Use classpath of module: fiat-trader
```

### IntelliJ Setup

1. Install Scala and sbt plugins
2. Create IntelliJ configs from project directory with sbt command: ```sbt gen-idea```
3. Open project in IntelliJ (let IntelliJ upgrade the sbt generated config files)
4. Verify the project JDK and Java Inspections settings are correct

Note: Don't use the IntelliJ scala plugin project import, I found it doesn't work well with mixed Java/Scala projects 

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