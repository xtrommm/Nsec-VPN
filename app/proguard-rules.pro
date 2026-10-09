# Nsec VPN ProGuard / R8 Rules

# Keep App DTOs and Data Models for Moshi JSON serialization
-keep class com.nsec.vpn.data.api.** { *; }
-keep class com.nsec.vpn.data.model.** { *; }

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
