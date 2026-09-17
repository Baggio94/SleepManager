plugins {
    id("com.android.application")
}

android {
    namespace = "com.med.sleepmanager.helper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.med.sleepmanager.helper"
        minSdk = 28
        targetSdk = 28
        versionCode = 212
        versionName = "0.2.12"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
