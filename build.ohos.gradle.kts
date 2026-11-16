plugins {
    kotlin("multiplatform") apply false
    kotlin("plugin.compose") version "2.0.21-KBA-010" apply false
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}
