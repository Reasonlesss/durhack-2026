plugins {
    kotlin("multiplatform") version "2.3.21"
}

group = "cloud.emilys"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    js {
        outputModuleName.set("musicApp")
        browser {
            commonWebpackConfig {
                outputFileName = "musicApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(npm("tone", "15.0.4"))
            // Tone.js's own AudioContext wrapper; same version Tone.js resolves to, so there's one copy.
            implementation(npm("standardized-audio-context", "25.3.77"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
