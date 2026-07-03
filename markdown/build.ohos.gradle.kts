//import com.tencent.kuikly.gradle.publish.AndroidArtifactTask
//import com.tencent.news.registerPublicationsFromKotlinPlugin

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    id("maven-publish")
}

group = "com.tencent.news"
version = "1.0.3-SNAPSHOT"

publishing {
    repositories {
        mavenLocal()
    }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {

    ohosArm64()

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

    val ohosArm64Main by sourceSets.getting {
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
