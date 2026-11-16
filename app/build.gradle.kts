plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

        val dummyEmail = providers.environmentVariable("DUMMY_EMAIL").orNull ?: "pdapur@goorder.com"
        val dummyPassword = providers.environmentVariable("DUMMY_PASSWORD").orNull ?: "123456"
        buildConfigField("String", "DUMMY_EMAIL", "\"$dummyEmail\"")
        buildConfigField("String", "DUMMY_PASSWORD", "\"$dummyPassword\"")

        buildConfigField("String", "BASE_URL", "\"http://192.168.0.153:8080/graphql\"")
        buildConfigField("String", "BASE_API", "\"ws://192.168.0.153:4001/ws\"")
        buildConfigField("String", "MQTT_BROKER", "\"tcp://192.168.0.153:1883\"")
        buildConfigField("String", "MQTT_USERNAME", "\"admin_mqtt\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"hajar93\"")
//        buildConfigField("String", "BASE_URL", "\"https://apis.iconfacade.com.my/graphql\"")
//        buildConfigField("String", "BASE_API", "\"https://apis.iconfacade.com.my\"")
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