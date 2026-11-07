plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.bizmanagezz"

    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.bizmanagezz"

        minSdk = 25
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Replace with the hosted HTTPS API before preparing the release.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://bizmanage-sa-api-nsabwa03-cagmawcsaxgpg4e4.indiasouthcentral-01.azurewebsites.net/v1/\""
        )
    }

    buildTypes {
        debug {
            // Requires: adb reverse tcp:3000 tcp:3000
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://bizmanage-sa-api-nsabwa03-cagmawcsaxgpg4e4.indiasouthcentral-01.azurewebsites.net/v1/\""
            )
        }

        release {
            optimization {
                enable = false
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation("androidx.compose.material:material-icons-extended")

    // Android lifecycle and activity support
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // REST API
    implementation(libs.retrofit)
    implementation("com.squareup.retrofit2:converter-gson:2.12.0")

    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    implementation(libs.kotlinx.serialization.json)

    // Room database
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Background work
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Firebase: retain this version for the current Kotlin setup.
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")

    // Google sign-in through Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation(
        "androidx.credentials:credentials-play-services-auth:1.3.0"
    )
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Firebase Task coroutine support
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2"
    )

    // Biometrics
    implementation("androidx.biometric:biometric:1.1.0")

    // Local unit tests
    testImplementation(libs.junit)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Compose debugging
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}