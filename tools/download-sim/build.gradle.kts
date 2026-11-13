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
        versionCode = project.findProperty("sim.version.code")?.toString()?.toIntOrNull() ?: 2
        versionName = project.findProperty("sim.version.name")?.toString() ?: "1.1"
    }

    signingConfigs {
        create("ciRelease") {
            val storeFilePath = providers.environmentVariable("RELEASE_STORE_FILE").orNull
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ciRelease").takeIf { it.storeFile != null }
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
