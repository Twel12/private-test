import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt.plugin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "foundation.e.findmydevice"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "foundation.e.findmydevice"
        minSdk = 31
        targetSdk = 36
        versionCode = 6
        versionName = "0.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("platformConfig") {
            storeFile = file("../keystore/platform.jks")
            storePassword = "platform"
            keyAlias = "platform"
            keyPassword = "platform"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("platformConfig")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("platformConfig")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_25 } }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)

    // Murena
    implementation(libs.elib)

    // Utilities
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material)

    // Tests
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
}

detekt {
    config.setFrom(file("../detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = true
}
