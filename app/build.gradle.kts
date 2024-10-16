plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") // Make sure this is correct
    kotlin("kapt") // This should be added
    id("com.google.gms.google-services")
}

android {
    namespace = "com.capstone.pomodoro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.capstone.pomodoro"
        minSdk = 24
        targetSdk = 34
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.activity)
    implementation(libs.firebase.database.ktx)
    implementation("androidx.fragment:fragment-ktx:1.6.1") // Ensure you have this line
    implementation(libs.androidx.core.ktx) // Core KTX
    // Add kapt dependencies here if needed

    implementation("androidx.room:room-runtime:2.5.0")
    kapt("androidx.room:room-compiler:2.5.0")


    implementation("androidx.compose.material3:material3:1.2.1")

    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // Google Sign-In dependencies
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}

