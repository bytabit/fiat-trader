# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/steve/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

-dontskipnonpubliclibraryclasses
-dontoptimize
-dontpreverify
-dontobfuscate
-verbose

-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepattributes *Annotation*

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers,includedescriptorclasses public class * extends android.view.View {
    void set*(***);
    *** get*();
}

-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

# android-support
-dontwarn android.support.**
-dontnote android.support.**
-keep class android.support.v7.widget.RoundRectDrawable { *; }

# bitcoinj
-keep,includedescriptorclasses class org.bitcoinj.wallet.Protos$** { *; }
-keepclassmembers class org.bitcoinj.wallet.Protos { com.google.protobuf.Descriptors$FileDescriptor descriptor; }
-keep,includedescriptorclasses class org.bitcoin.protocols.payments.Protos$** { *; }
-keepclassmembers class org.bitcoin.protocols.payments.Protos { com.google.protobuf.Descriptors$FileDescriptor descriptor; }
-dontwarn org.bitcoinj.store.WindowsMMapHack
-dontwarn org.bitcoinj.store.LevelDBBlockStore
-dontnote org.bitcoinj.crypto.DRMWorkaround
-dontnote org.bitcoinj.crypto.TrustStoreLoader$DefaultTrustStoreLoader
-dontnote com.subgraph.orchid.crypto.PRNGFixes
-dontwarn okio.DeflaterSink
-dontwarn okio.Okio
-dontnote com.squareup.okhttp.internal.Platform
-dontwarn org.bitcoinj.store.LevelDBFullPrunedBlockStore**

# java-wns-resolver
-dontwarn com.netki.WalletNameResolver
-dontwarn com.netki.dns.DNSBootstrapService
-dontnote org.xbill.DNS.ResolverConfig
-dontwarn org.xbill.DNS.spi.DNSJavaNameServiceDescriptor
-dontnote org.xbill.DNS.spi.DNSJavaNameServiceDescriptor
-dontwarn org.apache.log4j.**

# zxing
-dontwarn com.google.zxing.common.BitMatrix

# Guava
-dontwarn sun.misc.Unsafe
-dontnote com.google.common.reflect.**
-dontnote com.google.common.util.concurrent.MoreExecutors
-dontnote com.google.common.cache.Striped64,com.google.common.cache.Striped64$Cell

# slf4j
-dontwarn org.slf4j.MDC
-dontwarn org.slf4j.MarkerFactory

# logback-android
-dontwarn javax.mail.**
-dontnote ch.qos.logback.core.rolling.helper.FileStoreUtil


# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontwarn java.lang.invoke.*

-dontshrink

-dontwarn scala.**
-ignorewarnings

# temporary workaround; see Scala issue SI-5397
-keep class scala.collection.SeqLike {
    public protected *;
}

-keep class org.bytabit.**
-keep class com.typesafe.**
-keep class akka.**
-keep class org.fusesource.**
-keep class scala.collection.immutable.StringLike {
    *;
}


#akka

-keepclasseswithmembers class * {
    public <init>(com.typesafe.config.Config, akka.event.LoggingAdapter, java.util.concurrent.ThreadFactory);
}

-keepclasseswithmembers class * {
    public <init>(com.typesafe.config.Config, akka.event.EventStream);
}

-keep class akka.remote.transport.ProtocolStateActor{
  *;
}

#public <init>(akka.actor.OneForOneStrategy, class akka.actor.LocalActorRef);
-keep class akka.actor.LocalActorRefProvider$SystemGuardian {
  *;
}

-keep class akka.actor.LocalActorRefProvider$Guardian{
  *;
}

-keep class akka.remote.RemoteActorRefProvider$RemotingTerminator{
  *;
}

-keep class akka.remote.EndpointManager{
  *;
}


-keep class akka.remote.transport.AkkaProtocolManager{
  *;
}

-keep class akka.remote.RemoteWatcher{
  *;
}

-keep class akka.remote.transport.netty.NettyTransport {
  public <init>(akka.actor.ExtendedActorSystem, com.typesafe.config.Config);
}

-keep class akka.remote.ReliableDeliverySupervisor {
  *;
}

-keep class akka.remote.EndpointWriter {
  *;
}

-keepclassmembernames class * implements akka.actor.Actor {
  akka.actor.ActorContext context;
  akka.actor.ActorRef self;
}
-keep class * implements akka.actor.ActorRefProvider {
  public <init>(...);
}
-keep class * implements akka.actor.ExtensionId {
  public <init>(...);
}
-keep class * implements akka.actor.ExtensionIdProvider {
  public <init>(...);
}
-keep class akka.actor.SerializedActorRef {
  *;
}
-keep class * implements akka.actor.SupervisorStrategyConfigurator {
  public <init>(...);
}
-keep class * extends akka.dispatch.ExecutorServiceConfigurator {
  public <init>(...);
}
-keep class * implements akka.dispatch.MailboxType {
  public <init>(...);
}
-keep class * extends akka.dispatch.MessageDispatcherConfigurator {
  public <init>(...);
}
-keep class akka.event.Logging*
-keep class akka.event.Logging$LogExt {
  public <init>(...);
}
-keep class akka.remote.DaemonMsgCreate {
  *;
}
-keep class * extends akka.remote.RemoteTransport {
  public <init>(...);
}
-keep class * implements akka.routing.RouterConfig {
  public <init>(...);
}
-keep class * implements akka.serialization.Serializer {
  public <init>(...);
}
