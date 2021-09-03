plugins {
    // "org.jetbrains.kotlin.jvm"
    kotlin("jvm") version "1.3.50" apply false

    // 1.3.72
    // "org.jetbrains.kotlin.kapt"
    kotlin("kapt") version "1.3.50" apply false

//    kotlin("plugin.serialization") version "1.5.0-M2" apply false

    // "kotlinx-serialization"
    id("kotlinx-serialization") version "1.3.50" apply false

    id("org.jetbrains.intellij") version "1.1.4" apply false

    // plugin to check for dependency updates via task dependencyUpdates
    id("com.github.ben-manes.versions") version "0.39.0"
}

subprojects {
    if (name == "contracts") {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        apply(plugin = "org.jetbrains.kotlin.kapt")
        apply(plugin = "kotlinx-serialization")
//        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    }

    if (name == "merge-request-integration") {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        apply(plugin = "org.jetbrains.kotlin.kapt")
        apply(plugin = "kotlinx-serialization")
//        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    }

    if (name == "merge-request-integration-core") {
        apply(plugin = "org.jetbrains.intellij")
        apply(plugin = "org.jetbrains.kotlin.jvm")
    }

    if (name == "merge-request-integration-ce") {
        apply(plugin = "org.jetbrains.intellij")
        apply(plugin = "org.jetbrains.kotlin.jvm")
    }

    if (name == "merge-request-integration-ee") {
        apply(plugin = "org.jetbrains.intellij")
        apply(plugin = "org.jetbrains.kotlin.jvm")
    }
}
