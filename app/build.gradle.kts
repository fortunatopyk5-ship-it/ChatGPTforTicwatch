plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ua.wristgpt.bridge"
    compileSdk = 36
    defaultConfig {
        applicationId = "ua.wristgpt.bridge"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.2-experimental"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}