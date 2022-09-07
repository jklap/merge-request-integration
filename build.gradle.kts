plugins {
    // "org.jetbrains.kotlin.jvm"
    kotlin("jvm") version "1.6.21" apply false

    // "org.jetbrains.kotlin.kapt"
    kotlin("kapt") version "1.6.21" apply false

    // "kotlinx-serialization"
    kotlin("plugin.serialization") version "1.6.21"

    // https://plugins.gradle.org/plugin/org.jetbrains.intellij
    id("org.jetbrains.intellij") version "1.9.0" apply false

    // plugin to check for dependency updates via task dependencyUpdates
    id("com.github.ben-manes.versions") version "0.42.0"
}

allprojects { repositories { mavenLocal() } }

subprojects {
    if (name == "contracts") {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        apply(plugin = "org.jetbrains.kotlin.kapt")
        apply(plugin = "kotlinx-serialization")
    }

    if (name == "merge-request-integration") {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        apply(plugin = "org.jetbrains.kotlin.kapt")
        apply(plugin = "kotlinx-serialization")
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
