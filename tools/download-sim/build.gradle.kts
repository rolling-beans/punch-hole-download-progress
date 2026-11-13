plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "eu.hxreborn.downloadsim"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.hxreborn.downloadsim"
        minSdk = 34
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        disable += setOf("UnusedAttribute", "OldTargetApi")
    }
}

dependencies {
    testImplementation(libs.junit)
}
