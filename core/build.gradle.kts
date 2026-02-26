plugins {
    id("submaker.kmp.library")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        // 此处为 core 层，依赖可能通用的均使用 api 传递
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.navigation3)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.eygraber.uri.kmp)
            implementation(libs.vinceglb.filekit.dialogs)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.tencent.mmkv)
            api(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "com.funny.submaker.core"
}
