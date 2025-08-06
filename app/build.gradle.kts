// Top-level build.gradle.kts

import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

android {
    namespace = "com.example.onetapsos"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }


    defaultConfig {
        applicationId = "com.example.onetapsos"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

// 🛠️ Inject BuildConfig fields using androidComponents
androidComponents {
    onVariants { variant ->
        variant.buildConfigFields.put(
            "SUPABASE_URL",
            BuildConfigField("String", "\"https://fitudovdjqogynijfipd.supabase.co\"", "Supabase URL")
        )
        variant.buildConfigFields.put(
            "SUPABASE_KEY",
            BuildConfigField("String", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZpdHVkb3ZkanFvZ3luaWpmaXBkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM2MzMwMzYsImV4cCI6MjA2OTIwOTAzNn0.BLiCdRjGHY81ts7MyxhJyCDQ7Leqb4EW93KqQSSnBs0\"", "Supabase Key")
        )
    }
}

dependencies {
    // AndroidX and Material Design
    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.v192)
    implementation(libs.androidx.constraintlayout.v214)

    // Voice recording and speech recognition
    implementation(libs.androidx.annotation)

    // Location Services
    implementation(libs.play.services.location)

    // Supabase
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.ktor.client.android.v2312)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json.v171)

    // EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto.v110beta01)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}