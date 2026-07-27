import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Watch app uses its own key alias inside the same release keystore used by the mobile
// app (release/adaptify-release.jks), kept in a separate properties file so the watch
// module doesn't need to share the mobile app's key alias/password.
val keystorePropertiesFile = rootProject.file("release/keystore-watch.properties")
val keystoreProperties = Properties()
val hasKeystoreProperties = keystorePropertiesFile.exists()
if (hasKeystoreProperties) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.adaptify.adaptifywearos"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.adaptify.adaptifywearos"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    signingConfigs {
        if (hasKeystoreProperties) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Explicit modern Fragment version — some transitive dependency pulls an old
    // Fragment (<1.3.0) which fails lintVitalRelease's InvalidFragmentVersionForActivityResult
    // check against the registerForActivityResult() call in MainActivity. This forces
    // Gradle's conflict resolution to pick a compliant version without touching
    // MainActivity's permission-handling code.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation(libs.play.services.wearable)
    implementation(libs.health.services.client)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
}
