/**
 * Root build.gradle.kts - Project-level configuration
 * Contains only plugin management
 */

// Plugin management - versions defined here and applied in subprojects
plugins {
    // Android Gradle Plugin 8.13.2 - compatible with Gradle 9.x
    id("com.android.application") version "8.13.2" apply false

    // Kotlin 2.0.20 - compatible with Java 25 and Gradle 9.x
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false

    // Compose Compiler Plugin - MUST match Kotlin version
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}