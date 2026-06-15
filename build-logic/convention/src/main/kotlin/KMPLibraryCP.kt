
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.LibraryExtension
import com.funny.aitoy.buildlogic.findVersionAsInt
import com.funny.aitoy.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class KMPLibraryCP: Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            val android = extensions.getByType(LibraryExtension::class.java).apply {
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                compileSdk = libs.findVersionAsInt("android-compileSdk")
            }

            setupCommonKMP(android)
        }
    }
}



fun Project.setupCommonKMP(
    android: CommonExtension<*, *, *, *, *, *>
) {
    with(pluginManager) {
        apply("kotlin-multiplatform")
        apply("org.jetbrains.kotlin.plugin.serialization")
    }

    val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
    kotlin.apply {
        androidTarget { }

        jvm()

        // from https://youtrack.jetbrains.com/issue/KT-61573/Emit-the-compilation-warning-on-expect-actual-classes.-The-warning-must-mention-that-expect-actual-classes-are-in-Beta#focus=Comments-27-10358357.0-0
        targets.configureEach {
            compilations.configureEach {
                compileTaskProvider.get().compilerOptions {
                    freeCompilerArgs.addAll("-Xmulti-platform", "-Xexpect-actual-classes")
                }
            }
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }


    android.apply {
        defaultConfig {
            minSdk = libs.findVersionAsInt("android-minSdk")
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        dependencies {
            add("debugImplementation", libs.findLibrary("compose-uiTooling").get())
        }
    }
}
