plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") // google services gradle plugin
}

android {
    namespace = "np.ict.mad.madassg2025"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "np.ict.mad.madassg2025"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Kotlin DSL syntax (build.gradle.kts)
        buildConfigField("String", "ONEMAP_EMAIL", "\"evanthomas.goh@gmail.com\"")
        buildConfigField("String", "ONEMAP_PASSWORD", "\"Evangoh1810!\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true

        // ✅ Ensures BuildConfig is generated (needed for BuildConfig.ONEMAP_EMAIL)
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.code.gson:gson:2.13.2")

    implementation(platform("com.google.firebase:firebase-bom:34.6.0")) // firebase BoM
    implementation("com.google.firebase:firebase-auth") // Firebase Authentication
    implementation("com.google.firebase:firebase-firestore") // Cloud Firestore

    implementation("androidx.activity:activity-compose:1.9.0") // Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0") // Retrofit (Weather API)
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // Retrofit (Weather API)
    implementation("com.google.android.gms:play-services-maps:18.2.0") // Google Maps
    implementation("com.google.maps.android:maps-compose:2.11.4") // Google Maps
    implementation("com.google.maps.android:maps-compose-utils:2.11.4") // Google Maps
    implementation("androidx.compose.material:material-icons-extended")
}
