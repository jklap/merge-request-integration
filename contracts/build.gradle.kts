val artifactGroup: String by project
val artifactVersion: String by project
val jvmTarget: String by project
val foundationVersion: String by project
val foundationProcessorVersion: String by project
val jodaTimeVersion: String by project
val kotlinxSerializationRuntimeVersion: String by project

group = artifactGroup
version = artifactVersion

repositories {
    jcenter()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.nhat-phan.foundation:foundation-jvm:$foundationVersion")
    implementation("joda-time:joda-time:$jodaTimeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationRuntimeVersion")

    kapt("com.github.nhat-phan.foundation:foundation-processor:$foundationProcessorVersion")
    kaptTest("com.github.nhat-phan.foundation:foundation-processor:$foundationProcessorVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

kapt {
    arguments {
        arg("foundation.processor.mode", "contractOnly")
        arg("foundation.processor.settingsClass", "net.ntworld.mergeRequestIntegration.ContractData")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = jvmTarget
}
