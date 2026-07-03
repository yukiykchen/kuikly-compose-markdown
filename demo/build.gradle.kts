plugins {
//    id(libs.plugins.android.library.get().pluginId)
//    alias(libs.plugins.kuikly)
//    alias(libs.plugins.ksp)
//    alias(libs.plugins.kotlin.compose)
//    // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
//    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    id("maven-publish")
}

//android {
//    namespace = "com.tencent.news.markdown.demo"
//    compileSdk = 34
//
//    defaultConfig {
//        minSdk = 21
//        targetSdk = 30
//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//    }
//
//}
//
//kotlin {
//
//
//    runIfNotOhosBuild {
//        androidTarget {
//            compilations.all {
//                kotlinOptions {
//                    jvmTarget = "1.8"
//                    freeCompilerArgs += listOf(
//                        "-Xno-call-assertions",           // 不为函数非空参数进行断言
//                        "-Xno-receiver-assertions",       // 不为扩展函数和属性的Receiver生成非空断言检查
//                        "-Xno-param-assertions",          // 不为函数调用生成非空断言检查
//                        "-Xjvm-default=all-compatibility" // 高版本kt中，compatibility模式已废弃
//                    )
//                }
//            }
//        }
//
//        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
//            it.binaries.framework {
//                baseName = "markdown-compose-ui"
//                isStatic = true
//            }
//        }
//    }
//
///*    runIfOhosBuild {
//        setupKmmOhos { }
//    }*/
//
//
//    sourceSets {
//        commonMain {
//            dependencies {
//                implementation(libs.kotlinx.coroutines.core)
////                implementation(libs.kotlinx.collections.immutable)
//                implementation(libs.kotlin.stdlib)
//                // Add KMP dependencies here
//
//                implementation(libs.kuikly.compose)
//                implementation(libs.kuikly.core.annotation)
//
//                implementation(project(":markdown"))
//            }
//        }
//
//        commonTest {
//            dependencies {
//                implementation(libs.kotlin.test)
//            }
//        }
//
//        runIfNotOhosBuild {
//            androidMain {
//                dependencies {
//                    implementation(libs.kuikly.core.render.android)
//                    implementation(libs.androidx.dynamicanimation)
//                    implementation(libs.androidx.appcompat)
//                }
//            }
//        }
//    }
//
//}
//
//ksp {
//
//    arg("moduleId", "demo")          // 标识模块Id
//    arg("isMainModule", "true")         // 是否是主模块
//    arg("enableMultiModule", "true")     // 启用多模块
//    arg("caughtException", "false")      // 关闭kuikly异常捕获
//
//    dependencies {
//        val kuiklyVersion = libs.versions.kuikly.asProvider().get()
//        add("compileOnly", "com.tencent.kuikly:core-ksp:${kuiklyVersion}")?.apply {
//            runIfNotOhosBuild {
//                add("kspIosArm64", this)
//                add("kspIosX64", this)
//                add("kspIosSimulatorArm64", this)
//                add("kspAndroid", this)
//            }
//
//            runIfOhosBuild {
//                // add("kspOhosArm64", this)
//            }
//        }
//
//    }
//}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targets.all {
        compilations.all {
            kotlinOptions {
                // 设置部分优化标志
                freeCompilerArgs += listOf(
                    "-Xinline-classes",
                    "-opt-in=kotlin.ExperimentalStdlibApi",
                    "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
                    "-opt-in=kotlin.experimental.ExperimentalNativeApi",
                    "-opt-in=kotlin.contracts.ExperimentalContracts",
//                    "-P", "plugin:androidx.compose.compiler.plugins.kotlin:nonSkippingGroupOptimization=true",
                    "-P", "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true",
                    "-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true",
                    "-Xcontext-receivers"
                )
            }
        }
    }

    ohosArm64 {
        binaries.sharedLib("shared"){
            if(debuggable){
                freeCompilerArgs += "-Xadd-light-debug=enable"
                freeCompilerArgs += "-Xbinary=sourceInfoType=libbacktrace"
            }else{
                freeCompilerArgs += "-Xadd-light-debug=enable"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here

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
    }
}