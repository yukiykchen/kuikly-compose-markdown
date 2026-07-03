apply { from("../version.compat.gradle.kts") }

plugins {
    `kotlin-dsl`
}

dependencies {
    api(gradleKotlinDsl())
    implementation(libs.kotlin.gradlePlugin)
//    api(libs.compose.gradle.plugin)
//    api(libs.compose.compiler.gradle.plugin)
    api(libs.android.gradlePluginApi)
    api(libs.android.gradlePlugin)
}
