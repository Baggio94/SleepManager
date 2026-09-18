import java.util.Properties

plugins {
    id("com.android.application")
}

val helperId = providers.gradleProperty("SLEEPMANAGER_HELPER_ID").get()
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
    namespace = helperId
    compileSdk = 36

    defaultConfig {
        // Android update identity: keep this stable for every published build.
        applicationId = helperId
        minSdk = 28
        targetSdk = 28
        versionCode = appVersionCode
        versionName = appVersionName
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
