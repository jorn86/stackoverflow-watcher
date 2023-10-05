import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlin-library:compose"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
                implementation("io.ktor:ktor-client-websockets:2.3.0")
                implementation("io.ktor:ktor-client-encoding:2.3.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                runtimeOnly("io.ktor:ktor-client-java:2.3.0")
                runtimeOnly("org.slf4j:slf4j-simple:2.0.6")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.hertsig.stackoverflow.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "commander"
            packageVersion = "1.0.0"
        }
    }
}
