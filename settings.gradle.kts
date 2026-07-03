apply { from("version.compat.gradle.kts") }

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        addAll(pluginManagement.repositories)
    }
    versionCatalogs {
        create("libs") {
            (extra.get("versions") as Map<String, String>).forEach {
                version(it.key, it.value)
            }
        }
    }
}

val buildEnv: MutableMap<String, Any> = extra.properties["qqnews.build.env"] as MutableMap<String, Any>
gradle.allprojects {
    buildEnv.forEach { (t, u) -> extra.set(t, u) }
}

rootProject.name = "markdown-compose-ui"
//include(":app")
//include(":demo")
include(":markdown")
