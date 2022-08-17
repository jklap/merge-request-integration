import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val artifactGroup: String by project
val targetIDEVersion: String by project
val enterpriseEditionVersion: String by project
val intellijVersion: String by project
val jvmTarget: String by project
val foundationVersion: String by project
val gitlab4jVersion: String by project
val githubApiVersion: String by project
val prettyTimeVersion: String by project
val intellijSinceBuild: String by project
val intellijUntilBuild: String by project
val eapRelease: String by project
val flexMarkVersion: String by project

group = artifactGroup
version = if (eapRelease == "false") {
    "$enterpriseEditionVersion-built-for-ide-$targetIDEVersion"
} else {
    "$enterpriseEditionVersion-eap-$eapRelease-for-ide-$targetIDEVersion"
}

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

    //TODO fix this dependency
    implementation("com.fasterxml.uuid:java-uuid-generator:4.0.1")

    implementation(project(":contracts"))
    implementation(project(":merge-request-integration-core"))
    implementation(project(":merge-request-integration"))
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set(intellijVersion)
    updateSinceUntilBuild.set(true)
    plugins.set(listOf("git4idea"))
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
//        val version = if (!enterpriseEditionVersion.endsWith("eap"))
//            enterpriseEditionVersion else enterpriseEditionVersion.substring(0, enterpriseEditionVersion.length - 3)
//        version.set("${project.version}")
        sinceBuild.set(intellijSinceBuild)
        untilBuild.set(intellijUntilBuild)
    }

    compileKotlin {
        kotlinOptions.jvmTarget = jvmTarget
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = jvmTarget
    }
}

fun htmlFixer(filename: String): String {
    if (!File(filename).exists()) {
        throw Exception("File $filename not found.")
    }
    return File(filename).readText().replace("<html lang=\"en\">", "").replace("</html>", "")
}
