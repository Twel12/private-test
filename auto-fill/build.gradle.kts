import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "foundation.e.auto_fill"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.12.2")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.credentials:credentials:1.7.0-alpha01")
}
