import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.InvalidUserCodeException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

fun KotlinMultiplatformExtension.setupKmmOhos(config: KotlinNativeTarget.() -> Unit) {
    val name = "ohosArm64"
    val existing = targets.findByName(name)
    if (existing != null) {
        if (existing !is KotlinNativeTarget) throw InvalidUserCodeException("Target '$name' is not native")
        config(existing)
        return
    }

    // KBA 2.0 supplies this legacy factory; avoid linking its removed API in standard KGP builds.
    val getPresets = javaClass.methods.firstOrNull { it.name == "getPresets" && it.parameterCount == 0 }
        ?: throw InvalidUserCodeException("OHOS requires the KBA Kotlin plugin with target presets")
    val presets = getPresets.invoke(this) as NamedDomainObjectCollection<*>
    val preset = presets.findByName(name)
        ?: throw InvalidUserCodeException("The Kotlin plugin does not supply the '$name' target")
    val factory = preset.javaClass.getMethod("createTargetInternal", String::class.java)
    val target = factory.invoke(preset, name) as KotlinNativeTarget
    targets.add(target)
    config(target)
}
