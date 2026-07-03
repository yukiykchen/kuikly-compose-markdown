import org.gradle.api.InvalidUserCodeException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetPreset
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainerWithPresets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetPreset

fun KotlinMultiplatformExtension.setupKmmOhos(config: KotlinNativeTarget.() -> Unit) {
    configureOrCreate("ohosArm64", config)
}

fun KotlinTargetsContainerWithPresets.configureOrCreate(
    targetName: String,
    configure: KotlinNativeTarget.() -> Unit,
): KotlinNativeTarget {
    val targetPreset = presets.getByName("ohosArm64") as KotlinNativeTargetPreset
    val existingTarget = targets.findByName(targetName)
    when {
        existingTarget?.isProducedFromPreset(targetPreset) ?: false -> {
            @Suppress("UNCHECKED_CAST")
            configure(existingTarget as KotlinNativeTarget)
            return existingTarget
        }

        existingTarget == null -> {
            val newTarget = targetPreset.createTargetInternalCompat(targetName)
            targets.add(newTarget)
            configure(newTarget)
            return newTarget
        }

        else -> {
            @Suppress("DEPRECATION")
            throw InvalidUserCodeException(
                "The target '$targetName' already exists, but it was not created with the '${targetPreset.name}' preset. " +
                        "To configure it, access it by name in `kotlin.targets`" +
                        (" or use the preset function '${existingTarget.preset?.name}'."
                            .takeIf { existingTarget.preset != null } ?: ".")
            )
        }
    }
}

@Suppress("DEPRECATION")
fun KotlinTarget.isProducedFromPreset(kotlinTargetPreset: KotlinTargetPreset<*>): Boolean =
    preset == kotlinTargetPreset

fun KotlinNativeTargetPreset.createTargetInternalCompat(targetName: String): KotlinNativeTarget {
    val clz = this::class.java
    val createTargetInternal = clz.getMethod("createTargetInternal", String::class.java).also {
        it.isAccessible = true
    }
    return createTargetInternal.invoke(this, targetName) as KotlinNativeTarget
}
