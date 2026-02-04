plugins {
    id("submaker.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.squareup.okhttp)
            implementation(libs.squareup.okhttp.logging.interceptor)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "com.funny.submaker.network"
}

