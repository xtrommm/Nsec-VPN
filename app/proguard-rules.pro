# Nsec VPN ProGuard / R8 Rules

# Keep attributes required for Reflection, Annotations, and Serialization
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, ParameterNames, SourceFile, LineNumberTable, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations

# Kotlin Metadata & Reflection (Required by Moshi KotlinJsonAdapterFactory)
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Keep App DTOs, API models, and Data classes (Moshi JSON serialization)
-keep class com.nsec.vpn.data.api.** { *; }
-keepclassmembers class com.nsec.vpn.data.api.** { *; }
-keep class com.nsec.vpn.data.model.** { *; }
-keepclassmembers class com.nsec.vpn.data.model.** { *; }
-keep class com.nsec.vpn.data.config.** { *; }
-keepclassmembers class com.nsec.vpn.data.config.** { *; }
-keep class com.nsec.vpn.data.monetization.** { *; }
-keepclassmembers class com.nsec.vpn.data.monetization.** { *; }

# Moshi rules
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <init>(...);
}

# Retrofit & OkHttp
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# WireGuard Tunnel Native and Java classes (JNI & Service)
-keep class com.wireguard.** { *; }
-keepclassmembers class com.wireguard.** { *; }
-dontwarn com.wireguard.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# AndroidX WorkManager & Room (Transitive dependency of AdMob / Google Play Services)
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**
-dontwarn androidx.work.impl.**

# Play Billing Library
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Android Architecture Components & Coroutines
-keepclassmembers class * extends kotlinx.coroutines.CoroutineScope { *; }
-dontwarn kotlinx.coroutines.**

# Keep BuildConfig
-keep class com.nsec.vpn.BuildConfig { *; }
