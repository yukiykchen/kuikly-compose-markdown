import com.tencent.kuiklybase.MavenConfig
import com.tencent.kuiklybase.configureMavenCentralMetadata
import com.tencent.kuiklybase.signPublicationIfKeyPresent

//import com.tencent.kuikly.gradle.publish.AndroidArtifactTask
//import com.tencent.kuiklybase.registerPublicationsFromKotlinPlugin

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    id("maven-publish")
    signing
}

group = "com.tencent.kuiklybase"
version = "0.4.4"

publishing {
    repositories {
        val username = (project.findProperty("mavenUserName") as? String)
            ?: System.getenv("mavenUserName")
            ?: MavenConfig.getUsername(project)
        val password = (project.findProperty("mavenPassword") as? String)
            ?: System.getenv("mavenPassword")
            ?: MavenConfig.getPassword(project)
        val repoUrl = (project.findProperty("repoUrl") as? String)
            ?: System.getenv("KUIKLY_MAVEN_REPO_URL")
            ?: ""
        if (username.isNotEmpty() && password.isNotEmpty() && repoUrl.isNotEmpty()) {
            maven {
                credentials {
                    setUsername(username)
                    setPassword(password)
                }
                url = uri(repoUrl)
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
        nodejs()
    }

    val commonMain by sourceSets.getting {
        dependencies {
                implementation(libs.kotlin.stdlib)
                api(libs.kotlinx.coroutines.core)

                api(libs.kuikly.compose)
                implementation("org.jetbrains.kotlinx:atomicfu:0.25.0") {
                    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
                }

                val markdownDep = libs.jetbrains.markdown.get().let { "${it.module}:${it.version}" }
                api(markdownDep) {
                    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
                }
            }
    }

    val nativeStreamingMain by sourceSets.creating {
        dependsOn(commonMain)
    }

    val nativeMain by sourceSets.creating {
        dependsOn(commonMain)
    }

    sourceSets.matching {
        it.name in setOf(
            "androidMain", "jsMain", "iosArm64Main", "iosX64Main",
            "iosSimulatorArm64Main", "macosX64Main", "macosArm64Main",
        )
    }.configureEach {
        dependsOn(nativeStreamingMain)
        if (name !in setOf("androidMain", "jsMain")) dependsOn(nativeMain)
    }

    val androidUnitTest by sourceSets.getting {
        dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kuikly.compose)
            implementation(libs.kuikly.core.annotation)
        }
    }

    val jsTest by sourceSets.getting {
        dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kuikly.compose)
            implementation(libs.kuikly.core.annotation)
        }
    }

}

android {
    namespace = "com.tencent.kuiklybase.markdown"
    compileSdk = 34
    testOptions.unitTests.isReturnDefaultValues = true
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }
}
