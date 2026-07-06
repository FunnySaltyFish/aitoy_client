import com.android.build.gradle.internal.api.ApkVariantOutputImpl
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
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.squareup.okhttp)
            implementation(libs.mikepenz.markdown.renderer)
            implementation(libs.mikepenz.markdown.renderer.m3)
            implementation(libs.openai.java)
        }
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.network)
            implementation(projects.database)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kaml)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.funny.aitoy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.funny.aitoy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.project.versionCode.get().toInt()
        versionName = libs.versions.project.versionName.get()
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    applicationVariants.all {
        val channel = flavorName.takeIf { it.isNotBlank() } ?: "common"
        outputs.all {
            if (this is ApkVariantOutputImpl) {
                outputFileName = "$channel-$versionName.APK"
            }
        }
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
