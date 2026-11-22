plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bareglass.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bareglass.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.2-demo"
        // Dirección de la API: ./gradlew assembleDebug -PapiBase=https://api.tudominio.com
        val apiBase = (project.findProperty("apiBase") as String?) ?: ""
        buildConfigField("String", "API_BASE", "\"$apiBase\"")
        // Modo demostración (sin servidor: cuentas de prueba, pago simulado, mapa de pantallas).
        // Para quitarlo: -Pdemo=false  (o cambiar el valor por defecto de abajo a "false").
        val demo = (project.findProperty("demo") as String?) ?: "true"
        buildConfigField("boolean", "DEMO", demo)
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation("junit:junit:4.13.2")
}
