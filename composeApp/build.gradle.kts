import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.network)
            implementation(projects.database)
            implementation(projects.feature.auth)
            implementation(projects.feature.asr)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.navigation3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "com.funny.submaker"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.funny.submaker"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        val propFile = File("signing.properties")
        if (propFile.exists()) {
            create("release") {
                // 如果需要打 release 包，请在项目根目录下自行添加此文件
                /**
                 *  STORE_FILE=yourAppStroe.keystore
                 *  STORE_PASSWORD=yourStorePwd
                 *  KEY_ALIAS=yourKeyAlias
                 *  KEY_PASSWORD=yourAliasPwd
                 */
                val props = Properties()
                val reader =
                    BufferedReader(InputStreamReader(FileInputStream(propFile), "utf-8"))
                props.load(reader)

                storeFile = file(props["STORE_FILE"] as String)
                storePassword = props["STORE_PASSWORD"] as String
                keyAlias = props["KEY_ALIAS"] as String
                keyPassword = props["KEY_PASSWORD"] as String

                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }

    }

    buildTypes {
        val usedSigningConfig = kotlin.runCatching { signingConfigs.getByName("release") }
            .getOrDefault(signingConfigs.getByName("debug"))
        getByName("release") {
            // 临时可调试
            isDebuggable = false
            // 开启代码混淆
            isMinifyEnabled = true
            // 移除无用的 resource 文件
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = usedSigningConfig
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            signingConfig = usedSigningConfig
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.funny.submaker.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.funny.submaker"
            packageVersion = "1.0.0"
        }
    }
}
