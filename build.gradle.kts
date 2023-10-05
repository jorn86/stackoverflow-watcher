plugins {
    id("org.jetbrains.compose") version "1.5.1" apply false
    kotlin("multiplatform") version "1.9.0" apply false
    kotlin("plugin.serialization") version "1.8.10" apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}
