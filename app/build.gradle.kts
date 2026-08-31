import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// v0.9.0 employee refund tracking and advance balance history
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStorePath = System.getenv("ECI_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ECI_KEYSTORE_PASSWORD")
val hasReleaseSigning = !releaseStorePath.isNullOrBlank() && !releaseStorePassword.isNullOrBlank()

android {
    namespace = "za.co.eci.slipmanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "za.co.eci.slipmanager"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "0.9.0"
        buildConfigField(
            "String",
            "ECI_SERVER_URL",
            "\"${System.getenv("ECI_SERVER_URL") ?: "https://expenses-staging.digiteclabs.co.za"}\""
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = "eci-slip-manager"
                keyPassword = releaseStorePassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
