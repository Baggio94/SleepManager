import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appId = providers.gradleProperty("SLEEPMANAGER_APP_ID").get()
val appVersionCode = providers.gradleProperty("SLEEPMANAGER_VERSION_CODE").get().toInt()
val appVersionName = providers.gradleProperty("SLEEPMANAGER_VERSION_NAME").get()

val signingProperties = Properties().apply {
    val propertiesFile = rootProject.file("signing.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun signingValue(environmentName: String, propertyName: String): String? =
    System.getenv(environmentName)?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val stableStoreFile = signingValue("SLEEPMANAGER_STORE_FILE", "storeFile")
val stableStorePassword = signingValue("SLEEPMANAGER_STORE_PASSWORD", "storePassword")
val stableKeyAlias = signingValue("SLEEPMANAGER_KEY_ALIAS", "keyAlias")
val stableKeyPassword = signingValue("SLEEPMANAGER_KEY_PASSWORD", "keyPassword")

val stableSigningValues =
    listOf(stableStoreFile, stableStorePassword, stableKeyAlias, stableKeyPassword)
val hasAnyStableSigning = stableSigningValues.any { !it.isNullOrBlank() }
val hasStableSigning = stableSigningValues.all { !it.isNullOrBlank() }

if (hasAnyStableSigning && !hasStableSigning) {
    throw GradleException(
        "Stable signing is only partially configured. " +
            "Provide storeFile/storePassword/keyAlias/keyPassword in signing.properties " +
            "or the matching SLEEPMANAGER_* environment variables."
    )
}

android {
    namespace = appId
    compileSdk = 36

    defaultConfig {
        // Android update identity: keep this stable for every published build.
        applicationId = appId
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = rootProject.file(stableStoreFile!!)
                storePassword = stableStorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }

        getByName("release") {
            isMinifyEnabled = false
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core:1.17.0")

    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.material3:material3:1.4.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.4")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.4")
}
