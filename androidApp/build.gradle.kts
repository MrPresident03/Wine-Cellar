plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.houseofvino.android"
    compileSdk = 37

    defaultConfig {
        // PLACEHOLDER: choose the final application id before the first store upload. It can never change afterwards.
        applicationId = "com.houseofvino.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(projects.shared.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
