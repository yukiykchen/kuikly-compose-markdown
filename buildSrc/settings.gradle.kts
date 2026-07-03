apply { from("../version.compat.gradle.kts") }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        addAll(pluginManagement.repositories)
        mavenCentral()
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
            (extra.get("versions") as Map<String, String>).forEach {
                version(it.key, it.value)
            }
        }
    }
}
