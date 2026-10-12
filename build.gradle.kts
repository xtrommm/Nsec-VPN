// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}

// Automatically ensure debug.keystore exists for local Android Studio builds
val debugKeystore = file("${rootDir}/debug.keystore")
val debugKeystoreBase64 = file("${rootDir}/debug.keystore.base64")
if (!debugKeystore.exists()) {
    if (debugKeystoreBase64.exists()) {
        try {
            val cleanBase64 = debugKeystoreBase64.readText().replace("\\s".toRegex(), "")
            val decoded = java.util.Base64.getDecoder().decode(cleanBase64)
            debugKeystore.writeBytes(decoded)
            println("Successfully decoded debug.keystore from debug.keystore.base64")
        } catch (e: Exception) {
            println("Failed to decode debug.keystore.base64: ${e.message}")
        }
    }
    if (!debugKeystore.exists()) {
        val userHomeKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
        if (userHomeKeystore.exists()) {
            try {
                userHomeKeystore.copyTo(debugKeystore, overwrite = true)
                println("Copied system debug.keystore from user home directory")
            } catch (e: Exception) {
                println("Failed to copy user debug.keystore: ${e.message}")
            }
        }
    }
}
