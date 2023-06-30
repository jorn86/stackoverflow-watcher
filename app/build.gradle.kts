import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":compose-library"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("io.ktor:ktor-client-websockets:2.3.0")
    implementation("io.ktor:ktor-client-encoding:2.3.0")
    runtimeOnly("io.ktor:ktor-client-java:2.3.0")
}

compose.desktop {
    application {
        mainClass = "org.hertsig.commander.AppKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "commander"
            packageVersion = "1.0.0"
        }
    }
}
