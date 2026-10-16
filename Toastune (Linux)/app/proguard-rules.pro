# NewPipeExtractor usa Rhino (JavaScript) y clases que no existen en Android
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**

# Extractor: usa reflexión y serialización protobuf
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }

# Protobuf (lo usa el extractor para hablar con YouTube)
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn com.google.protobuf.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Rhino referencia clases de Java de escritorio (jdk.dynalink) que no existen en Android
-dontwarn jdk.dynalink.**
-dontwarn jdk.internal.**
-dontwarn sun.misc.**
-dontwarn org.jspecify.**
-dontwarn javax.naming.**
-dontwarn org.slf4j.**
-dontwarn kotlin.concurrent.atomics.**
