plugins {
    id(libs.plugins.android.application.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
}

import java.util.Properties

android {
    namespace = "com.tencent.kuiklybase.markdown.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tencent.kuiklybase.markdown.app"
        minSdk = 21
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val aiChatProps = Properties().also { props ->
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
        }
        fun aiChatProperty(name: String, fallback: String): String =
            (aiChatProps.getProperty(name) ?: fallback).replace("\"", "\\\"")
        buildConfigField(
            "String",
            "AI_CHAT_API_URL",
            "\"${aiChatProperty("aiChatApiUrl", "https://api.moonshot.cn/v1/chat/completions")}\"",
        )
        buildConfigField(
            "String",
            "AI_CHAT_MODEL",
            "\"${aiChatProperty("aiChatModel", "kimi-k2.6")}\"",
        )
        buildConfigField(
            "String",
            "AI_CHAT_API_KEY",
            "\"${aiChatProperty("aiChatApiKey", "")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.kuikly.core.render.android)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)

    implementation(libs.kotlinx.coroutines.core)

    implementation(project(":markdown"))
    implementation(project(":demo"))
}
