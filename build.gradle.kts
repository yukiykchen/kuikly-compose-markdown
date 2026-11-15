import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec

// android & iOS gradle
plugins {
    kotlin("multiplatform") apply false
    kotlin("plugin.compose") version "2.1.21" apply false
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.1" apply false
}

gradle.projectsEvaluated {
    allprojects {
        providers.gradleProperty("markdownNodePath").orNull?.let { path ->
            extensions.findByType<NodeJsEnvSpec>()?.apply {
                download.set(false)
                command.set(path)
            }
        }
        if (providers.gradleProperty("markdownUseSystemYarn").orNull == "true") {
            extensions.findByType<YarnRootEnvSpec>()?.apply {
                download.set(false)
                providers.gradleProperty("markdownYarnCommand").orNull?.let { command.set(it) }
            }
        }
    }
}

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
}
