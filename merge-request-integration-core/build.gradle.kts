val artifactGroup: String by project
val artifactVersion: String by project
val intellijVersion: String by project
val jvmTarget: String by project
val foundationVersion: String by project
val gitlab4jVersion: String by project
val githubApiVersion: String by project
val prettyTimeVersion: String by project
val flexMarkVersion: String by project
val mockkVersion: String by project

group = artifactGroup
version = artifactVersion

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.github.nhat-phan.foundation:foundation-jvm:$foundationVersion")
    implementation("org.gitlab4j:gitlab4j-api:$gitlab4jVersion")
    implementation("org.kohsuke:github-api:$githubApiVersion")
    implementation("org.ocpsoft.prettytime:prettytime:$prettyTimeVersion")
    implementation("com.vladsch.flexmark:flexmark-all:$flexMarkVersion")

    implementation(project(":contracts"))
    implementation(project(":merge-request-integration"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("io.mockk:mockk:$mockkVersion")
}

intellij {
    version.set(intellijVersion)
    plugins.set(listOf("git4idea"))
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    compileKotlin {
        kotlinOptions.jvmTarget = jvmTarget
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = jvmTarget
    }

    setupDependencies {
        doLast {
            // Fixes IDEA-298989.
            fileTree("$buildDir/instrumented/instrumentCode") { include("**/*.class") }.files.forEach { delete(it) }
        }
    }
}