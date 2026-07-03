import com.tencent.news.MavenConfig
import com.tencent.news.configureMavenCentralMetadata
import com.tencent.news.signPublicationIfKeyPresent

//import com.tencent.kuikly.gradle.publish.AndroidArtifactTask
//import com.tencent.news.registerPublicationsFromKotlinPlugin

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    id("maven-publish")
    signing
}

group = "com.tencent.kuiklybase"
version = "0.4.1"

publishing {
    repositories {
        val username = MavenConfig.getUsername(project)
        val password = MavenConfig.getPassword(project)
        if (username.isNotEmpty() && password.isNotEmpty()) {
            maven {
                credentials {
                    setUsername(username)
                    setPassword(password)
                }
                url = uri(MavenConfig.getRepoUrl(version as String))
            }
        } else {
            mavenLocal()
        }

        publications.withType<MavenPublication>().configureEach {
            pom.configureMavenCentralMetadata()
            signPublicationIfKeyPresent(project)
        }
    }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
//    jvm()

    androidTarget() {
        publishLibraryVariants("release")
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

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    macosX64()
    macosArm64()

    js(IR) {
        browser()
    }

    val commonMain by sourceSets.getting {

        dependencies {
                compileOnly(libs.kotlin.stdlib)

                compileOnly(libs.kuikly.compose)
                compileOnly(libs.kuikly.core.annotation)

                val markdownDep = libs.jetbrains.markdown.get().let { "${it.module}:${it.version}" }
                api(markdownDep) {
                    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
                }
            }
    }

    sourceSets.iosMain {
        dependsOn(commonMain)
    }
}

android {
    namespace = "com.tencent.news.markdown"
    compileSdk = 30
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
}
