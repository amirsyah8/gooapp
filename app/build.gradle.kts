import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ── Baca credentials dari local.properties (fail ini TIDAK masuk git) ──
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.goorder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.goorder"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // Debug auto-fill: dibaca dari local.properties, BUKAN hardcoded
        val dummyEmail = providers.environmentVariable("DUMMY_EMAIL").orNull
            ?: localProps.getProperty("DUMMY_EMAIL", "")
        val dummyPassword = providers.environmentVariable("DUMMY_PASSWORD").orNull
            ?: localProps.getProperty("DUMMY_PASSWORD", "")
        buildConfigField("String", "DUMMY_EMAIL", "\"$dummyEmail\"")
        buildConfigField("String", "DUMMY_PASSWORD", "\"$dummyPassword\"")

        // Backend endpoints: dibaca dari local.properties
        buildConfigField("String", "BASE_URL", "\"${localProps.getProperty("BASE_URL", "")}\"")
        buildConfigField("String", "BASE_API", "\"${localProps.getProperty("BASE_API", "")}\"")
        buildConfigField("String", "MQTT_BROKER", "\"${localProps.getProperty("MQTT_BROKER", "")}\"")
        buildConfigField("String", "MQTT_USERNAME", "\"${localProps.getProperty("MQTT_USERNAME", "")}\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"${localProps.getProperty("MQTT_PASSWORD", "")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            buildConfigField("String", "DUMMY_EMAIL", "\"\"")
            buildConfigField("String", "DUMMY_PASSWORD", "\"\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // MQTT Paho Client
    implementation(libs.paho.mqttv3)

    // Socket.io
    implementation(libs.socket.io.client)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
