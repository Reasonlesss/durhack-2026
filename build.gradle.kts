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
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
