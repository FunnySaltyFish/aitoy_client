plugins {
    id("submaker.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(libs.squareup.okhttp)
            implementation(libs.squareup.okhttp.logging.interceptor)
            implementation(libs.squareup.retrofit)
            implementation(libs.squareup.retrofit.kotlinx.serialization)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "com.funny.submaker.network"
}
