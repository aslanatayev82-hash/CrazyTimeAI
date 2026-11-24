
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "az.crazytime.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "az.crazytime.ai"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")

    // Google ML Kit - mətn tanıma (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}    // OpenCV - çarxın avtomatik tanınması
    implementation("org.opencv:opencv:4.14.0")
}
