plugins {
    id("submaker.kmp.library")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.navigation3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.eygraber.uri.kmp)
            implementation(libs.vinceglb.filekit.dialogs)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.tencent.mmkv)
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "com.funny.submaker.core"
}
