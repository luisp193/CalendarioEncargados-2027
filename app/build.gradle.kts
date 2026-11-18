plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Número de cambio: se sube a mano (+1) en cada cambio. Nombre de la app y del APK: Calendario_V<n>
val cambio = file("../version.txt").readText().trim().toInt()

android {
    namespace = "com.luis.calendario"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.luis.calendario"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "V$cambio"
        resValue("string", "app_name", "Calendario_V$cambio")
    }

    signingConfigs {
        create("fija") {
            storeFile = file("calendario.keystore")
            storePassword = "claves-its-2026"
            keyAlias = "claves"
            keyPassword = "claves-its-2026"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fija") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
