//import com.tencent.kuikly.gradle.publish.AndroidArtifactTask
//import com.tencent.kuiklybase.registerPublicationsFromKotlinPlugin

import com.tencent.kuiklybase.MavenConfig
import com.tencent.kuiklybase.configureMavenCentralMetadata
import com.tencent.kuiklybase.signPublicationIfKeyPresent

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    id("maven-publish")
}

group = "com.tencent.kuiklybase"
version = "0.4.4-ohos"

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
                credentials { setUsername(username); setPassword(password) }
                url = uri(repoUrl)
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom.configureMavenCentralMetadata()
        signPublicationIfKeyPresent(project)
    }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {

    ohosArm64()

    targets.configureEach {
        compilations.configureEach {
            compilerOptions.configure {
                freeCompilerArgs.add("-opt-in=org.intellij.markdown.ExperimentalApi")
            }
        }
    }

    val commonMain by sourceSets.getting {

        dependencies {
            compileOnly(libs.kotlin.stdlib)

            compileOnly(libs.kuikly.compose)
            compileOnly(libs.kuikly.core.annotation)

            // The standard parser has no ohos_arm64 klib. OHOS uses the KBA-compatible fork.
            val markdownDep = "com.tencent.kuiklybase:markdown-parser:0.7.4-ohos"
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
    namespace = "com.tencent.kuiklybase.markdown"
    compileSdk = 30
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
}
