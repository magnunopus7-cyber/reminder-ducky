plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.reminderducky"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.reminderducky"
        minSdk = 26
        targetSdk = 35
        // CI passes the build number so every new APK installs over the old one.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.0." + versionCode
    }

    signingConfigs {
        // A fixed key kept in the repo, so updates built on GitHub keep the
        // same signature and install over the previous version.
        getByName("debug") {
            storeFile = file("ducky-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The private upload key for Google Play. It never goes in the repo: CI
        // writes it from the UPLOAD_KEYSTORE_B64 and UPLOAD_KEY_PASSWORD secrets.
        val uploadStore = System.getenv("UPLOAD_KEYSTORE_FILE")
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = System.getenv("UPLOAD_KEY_PASSWORD")
                keyAlias = "upload"
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Play builds use the upload key; the APK for direct installs keeps the repo key.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
}
