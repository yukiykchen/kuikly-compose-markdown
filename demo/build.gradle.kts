plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    id("maven-publish")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs += listOf(
                    "-Xno-call-assertions",
                    "-Xno-receiver-assertions",
                    "-Xno-param-assertions",
                    "-Xjvm-default=all-compatibility"
                )
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlin.stdlib)
                implementation(libs.kuikly.compose)
                implementation(libs.kuikly.core.annotation)
                implementation(project(":markdown"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kuikly.core.render.android)
                implementation(libs.androidx.dynamicanimation)
                implementation(libs.androidx.appcompat)
            }
        }
    }
}

android {
    namespace = "com.tencent.kuiklybase.markdown.demo"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

ksp {
    arg("moduleId", "demo")
    arg("isMainModule", "true")
    arg("enableMultiModule", "true")
    arg("caughtException", "false")

    dependencies {
        val kuiklyVersion = libs.versions.kuikly.asProvider().get()
        add("compileOnly", "com.tencent.kuikly-open:core-ksp:${kuiklyVersion}")?.apply {
            add("kspIosArm64", this)
            add("kspIosX64", this)
            add("kspIosSimulatorArm64", this)
            add("kspAndroid", this)
        }
    }
}
