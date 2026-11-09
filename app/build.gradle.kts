import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The signing key comes from the build (GitHub Actions secrets), never from the repository:
// every release is signed with the same key, so updates install over each other.
val keystorePath: String? = System.getenv("CARDASH_KEYSTORE")

android {
    namespace = "io.github.bornaradusin.cardash"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.bornaradusin.cardash"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "1.2.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CARDASH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CARDASH_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "cardash"
                keyPassword = System.getenv("CARDASH_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("CARDASH_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // The 3D map and saved map areas (OpenGL ES: the radio's graphics chip is happiest with it)
    implementation("org.maplibre.gl:android-sdk-opengl:13.5.0")
    // Its HTTP client, set up for slow connections (CarDashApp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // The OBD dongle's USB serial chip (CH340, FTDI, CP210x, PL2303)
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
}
