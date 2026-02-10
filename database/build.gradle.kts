plugins {
    id("submaker.kmp.library")
    alias(libs.plugins.google.ksp)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.common)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.room.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.androidx.room.runtime.jvm)
            implementation(libs.androidx.sqlite.bundled.jvm)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

android {
    namespace = "com.funny.submaker.database"
}
