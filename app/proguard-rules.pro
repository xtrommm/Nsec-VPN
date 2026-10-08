# WireGuard Tunnel Native and Java classes
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# Retrofit, OkHttp, Moshi
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.* <fields>;
}

# Android Architecture Components
-keep class * extends androidx.room.RoomDatabase
