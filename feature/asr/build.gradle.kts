plugins {
    id("submaker.kmp.library")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.database)
            implementation(projects.network)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}

android {
    namespace = "com.funny.submaker.feature.asr"
}
