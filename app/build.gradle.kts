plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.example.myapplication"

    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {

    // Compose
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(
        "androidx.compose.material:material-icons-extended"
    )

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // AndroidX
    implementation(libs.androidx.core.ktx)

    implementation(
        "androidx.navigation:navigation-compose:2.9.8"
    )

    implementation(
        "androidx.datastore:datastore-preferences:1.1.7"
    )

    implementation(
        "androidx.biometric:biometric:1.1.0"
    )

    implementation(
        "com.google.android.material:material:1.13.0"
    )

    // Firebase
    implementation(
        platform("com.google.firebase:firebase-bom:34.2.0")
    )

    implementation(
        "com.google.firebase:firebase-analytics"
    )

    implementation(
        "com.google.firebase:firebase-crashlytics"
    )

    implementation(
        "com.google.firebase:firebase-config"
    )
    dependencies {

        implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    }
    // Google AdMob
    implementation(
        "com.google.android.gms:play-services-ads:24.6.0"
    )

    // Facebook Ads Mediation
    implementation(
        "com.google.ads.mediation:facebook:6.20.0.1"
    )

    // Camera
    implementation(
        "androidx.camera:camera-camera2:1.5.0"
    )

    implementation(
        "androidx.camera:camera-lifecycle:1.5.0"
    )

    implementation(
        "androidx.camera:camera-core:1.5.0"
    )

    // Google Play Review
    implementation(
        "com.google.android.play:review:2.0.2"
    )

    // Coil
    implementation(
        "io.coil-kt:coil-compose:2.7.0"
    )

    // Lottie
    implementation(
        "com.airbnb.android:lottie-compose:6.6.7"
    )

    // Google Play Billing
    implementation(
        "com.android.billingclient:billing-ktx:9.1.0"
    )

    // Unit tests
    testImplementation(libs.junit)

    // Android tests
    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    // Debug
    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
}