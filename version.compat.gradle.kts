@file:Suppress("UnstableApiUsage")

import java.io.File
import java.util.Properties


val BUILD_TYPE_ANDROID = "android"
val BUILD_TYPE_IOS = "ios"
val BUILD_TYPE_OHOS = "ohos"

val AGP = "agp"
val KOTLIN = "kotlin"
val KSP = "ksp"
val COMPOSE = "compose-multiplatform"
val KUIKLY = "kuikly"
val KUIKLY_COMPOSE = "kuikly-compose"
val KUIKLY_SHORT = "kuikly-short"

// KGP、AGP、Gradle版本兼容：https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin
val versions = hashMapOf(
    // android 产物编译环境
    BUILD_TYPE_ANDROID to mutableMapOf(
        AGP to "8.6.1",
        KOTLIN to "2.1.21",
        KSP to "2.1.21-2.0.1",
        COMPOSE to "1.7.3",
        KUIKLY_COMPOSE to "2.30.0-2.1.21",
        KUIKLY to "2.30.0-2.1.21",
        KUIKLY_SHORT to "2.30.0"
    ),

    // iOS 产物编译环境
    BUILD_TYPE_IOS to mutableMapOf(
        AGP to "8.6.1",
        KOTLIN to "2.1.21",
        KSP to "2.1.21-2.0.1",
        COMPOSE to "1.7.3",
        KUIKLY_COMPOSE to "2.30.0-2.1.21",
        KUIKLY to "2.30.0-2.1.21",
        KUIKLY_SHORT to "2.30.0"
    ),

    // ohos 产物编译环境
    /* Kotlin 2.0.21 */
    BUILD_TYPE_OHOS to mutableMapOf(
        AGP to "8.1.0",
        KOTLIN to "2.0.21-KBA-004",
        COMPOSE to "1.7.3",
        KSP to "2.0.21-1.0.27",
        KUIKLY_COMPOSE to "2.12.0-2.0.21-ohos",
        KUIKLY to "2.12.0-2.0.21-ohos",
        KUIKLY_SHORT to "2.1.1"
    ),

    /* Kotlin 1.9.22
    BUILD_TYPE_OHOS to mutableMapOf(
        AGP to "8.1.0",
        KOTLIN to "1.9.22",
        COMPOSE to "1.7.3",
        KSP to "1.9.22-1.0.18",
        KUIKLY_COMPOSE to "2.1.1-1.9.22",
        KUIKLY to "2.1.1-1.9.22",
        KUIKLY_SHORT to "2.1.1"
    )
    */
)


    fun getLocalPropsFile(): File {
        val localFile = File(rootDir, "local.properties")
        if (localFile.exists()) {
            return localFile
        }
        return File(rootDir.parentFile, "local.properties")
    }

    fun localProps(key: String): String {
        val localPropFile = getLocalPropsFile()
        if (!localPropFile.exists()) {
            return ""
        }
        val props = Properties().also { it.load(localPropFile.inputStream()) }
        return props.getProperty(key)?.toString() ?: ""
    }

    fun getCompatBuildType(): String {

        // iOS本地XCode编译时的环境变量
        val xCodeFamilyName = System.getenv("PLATFORM_FAMILY_NAME")
        if (xCodeFamilyName == "iOS") {
            println("> CompatBuildType: $BUILD_TYPE_IOS")
            return BUILD_TYPE_IOS
        }

        // 脚本编译
        var result = System.getenv("COMPAT_BUILD_TYPE")?.takeIf { it.isNotBlank() }

        if (!result.isNullOrEmpty()) {
            println("> CompatBuildType: $result")
            return result
        }

        // 用户自定义
        val platform = extra.properties.getOrDefault("qqkuiklybase.kmm.build.platform", "").toString().ifEmpty {
            localProps("qqkuiklybase.kmm.build.platform")
        }

        if (!platform.isNullOrEmpty()) {
            println("> CompatBuildType: $platform")
            return platform
        }

        // 默认
        if (result.isNullOrEmpty()) {
            result = BUILD_TYPE_ANDROID
        }

        println("> CompatBuildType: $result，${gradle.gradleVersion}")

        return result!!
    }

    fun versions(): MutableMap<String, String> {
        val buildType = getCompatBuildType()
        return versions[buildType]!!
    }

    extra.let {
        val buildType = getCompatBuildType()
        it.set("versions", versions())
        it.set(
            "qqkuiklybase.build.env",
            mutableMapOf(
                "build.platform" to buildType
            )
        )
    }




