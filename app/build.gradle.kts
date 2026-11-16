import java.util.Properties
import java.io.FileInputStream

/**
 * App module build.gradle.kts - Magic Remote Application
 * Android TV Remote Protocol v2 client with Magic Remote features
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vijay.aimagicremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vijay.aimagicremote"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Enable multidex for large dependency graphs
        multiDexEnabled = true

        // Vector drawables support
        vectorDrawables.useSupportLibrary = true
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePropertiesFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug signing handled by IDE
            isDebuggable = true
        }
    }

    // Java 8 compatibility (required for some dependencies)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Kotlin JVM target
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Compose Compiler options
    composeOptions {
        // Kotlin 2.0.20 -> Compose Compiler 2.0.20
        kotlinCompilerExtensionVersion = "2.0.20"
    }

    // Packaging options for native libraries / protobuf
    packagingOptions {
        resources.excludes += "META-INF/*"
        jniLibs.pickFirsts += "lib/**/*"
    }
}

dependencies {
    // ============================================================
    // VERSION CATALOG / BOMS
    // ============================================================

    // Compose BOM - manages all Compose library versions
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom)

    // ============================================================
    // ANDROIDX CORE
    // ============================================================
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

    // ============================================================
    // JETPACK COMPOSE UI
    // ============================================================
    // Material Design 3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")

    // Core Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-viewbinding")

    // Layout / Foundation
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")

    // Animation
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")

    // Activity & Lifecycle integration
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // ============================================================
    // LIFECYCLE & VIEWMODEL
    // ============================================================
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // ============================================================
    // KOTLIN COROUTINES & FLOW
    // ============================================================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // ============================================================
    // NETWORKING - OKHTTP 4.x (modern, maintained)
    // ============================================================
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ============================================================
    // MEDIA3 EXOPLAYER (for media control)
    // ============================================================
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")

    // ============================================================
    // MATERIAL COMPONENTS (Legacy Views - for interop)
    // ============================================================
    implementation("com.google.android.material:material:1.11.0")

    // ============================================================
    // PERMISSION HANDLING
    // ============================================================
    // No extra dependency needed - uses android.net.nsd

    // ============================================================
    // TESTING
    // ============================================================
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-data")
}

// ============================================================
// KSP / KAPT CONFIGURATION (if needed for Room, etc.)
// ============================================================
// Not currently needed - using reflection-based approaches