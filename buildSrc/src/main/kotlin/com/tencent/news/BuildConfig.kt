import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra

fun Project.runIfOhosBuild(action: (Project) -> Unit) {
    if (isOhosBuild()) {
        action(this)
    }
}

fun Project.runIfNotOhosBuild(action: (Project) -> Unit) {
    if (!isOhosBuild()) {
        action(this)
    }
}

fun Project.isOhosBuild() = "ohos" == getBuildPlatform()

fun Project.getBuildPlatform() = findPropertyAnyWhere("build.platform")

fun Project.findPropertyAnyWhere(key: String): String? {
    return when {
        extra.has(key) -> extra.get(key).toString()
        project.hasProperty(key) -> project.properties[key].toString()
        else -> null
    }
}

