plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.nsec.vpn.speedtest"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.nsec.vpn.speedtest"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        val debugKey = file("${rootDir}/debug.keystore")
        if (debugKey.exists()) getByName("debug") {
            storeFile = debugKey; storePassword = "android"
            keyAlias = "androiddebugkey"; keyPassword = "android"
        }
        val releaseKey = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
        if (releaseKey.exists()) create("release") {
            storeFile = releaseKey; storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"; keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { warningsAsErrors = true; checkReleaseBuilds = true }
}
dependencies { testImplementation(libs.junit) }
