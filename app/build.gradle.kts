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

    // La llave de firma NO va en el repo. Viene de los secrets de GitHub:
    // KEYSTORE_BASE64 y KEYSTORE_PASSWORD (el alias es "calendario").
    // El workflow reconstruye el archivo antes de compilar.
    val llave = file("calendario.keystore")
    val clave = System.getenv("KEYSTORE_PASSWORD")
    val firmaDisponible = llave.exists() && !clave.isNullOrBlank()

    signingConfigs {
        if (firmaDisponible) {
            create("fija") {
                storeFile = llave
                storePassword = clave
                keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "calendario"
                keyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: clave
            }
        }
    }

    buildTypes {
        // Sin la llave (compilación local de prueba) se firma con la de depuración:
        // el APK funciona, pero no sirve para actualizar encima del instalado.
        debug { if (firmaDisponible) signingConfig = signingConfigs.getByName("fija") }
        release {
            isMinifyEnabled = false
            if (firmaDisponible) signingConfig = signingConfigs.getByName("fija")
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
