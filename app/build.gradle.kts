plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "de.fabmax.kool.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.fabmax.kool.demo"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // keep the debug APK installable side-by-side with future builds:
            // same applicationId + debug signing is handled by Gradle defaults
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xcontext-parameters"
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.kool.core)
    implementation(libs.kool.physics)
    implementation(libs.kool.physics2d)
    implementation(libs.androidsvg)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.kotlin.serialization.json)
}
