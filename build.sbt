import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.{GitBranchPrompt, GitVersioning}

// generate the version.conf file containing sbt app name and version
resourceGenerators in Compile += Def.task {
  val file = (resourceManaged in Compile).value / "version.conf"
  val contents = "bytabit.fiat-trader {\n  version = %s\n}\n".format(version.value)
  IO.write(file, contents)
  Seq(file)
}.taskValue

enablePlugins(GitVersioning, GitBranchPrompt)

git.useGitDescribe := true

name := "fiat-trader"

organization := "org.bytabit"

scalaVersion := "2.11.7"

scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

// allow putting fxml files along side controller classes to work better with scene builder
unmanagedResourceDirectories in Compile += (javaSource in Compile).value
excludeFilter in unmanagedResources := "*.java"

// fix for error:
// WARNING: Resource "com/sun/javafx/scene/control/skin/modena/modena.css" not found.
unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/jfxrt.jar"))
fork in run := true

val akkaVersion = "2.4.1"
val akkaStreamVersion = "2.0.1"

// akka jars
val akka = "com.typesafe.akka" %% "akka-actor" % akkaVersion
val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % akkaVersion
val leveldb = "org.iq80.leveldb" % "leveldb" % "0.7"
val leveldbjni = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
// akka http jars
val akkaHttp = "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamVersion
val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamVersion

// other jars
val logback = "ch.qos.logback" % "logback-classic" % "1.0.13"
val jodaTime = "joda-time" % "joda-time" % "2.9.1"
val jodaConvert = "org.joda" % "joda-convert" % "1.8"
val jodaMoney = "org.joda" % "joda-money" % "0.10.0"
val bitcoinj = "org.bitcoinj" % "bitcoinj-core" % "0.13.3"
val scProv = "com.madgag.spongycastle" % "prov" % "1.51.0.0"
val fontawesomefx = "de.jensd" % "fontawesomefx" % "8.0.0"
val qrgen = "net.glxn" % "qrgen" % "1.3"

// test jars
val scalatest = "org.scalatest" %% "scalatest" % "2.2.1" % "test"
val scalacheck = "org.scalacheck" %% "scalacheck" % "1.12.2" % "test"
//val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaStreamVersion % "test"

libraryDependencies ++= Seq(akka, akkaHttp, akkaHttpSprayJson, akkaPersistence, leveldb, leveldbjni,
  logback, jodaTime, jodaConvert, jodaMoney, bitcoinj, scProv, fontawesomefx, qrgen, scalatest, scalacheck)
