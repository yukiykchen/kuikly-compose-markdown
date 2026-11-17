#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SHELL_ROOT="${SYNTAX_SHELL:-/Users/yukiykchen/project/KuiklyMarkdown}"
WORK="${SYNTAX_WORK:-/tmp/kuikly-compose-markdown-syntax-230-shell}"
VARIANT="${SYNTAX_VARIANT:-debug}"
case "$VARIANT" in
  debug|release) ;;
  *) echo "SYNTAX_VARIANT must be debug or release" >&2; exit 2 ;;
esac

mkdir -p "$WORK"
rsync -a \
  --exclude '.git' \
  --exclude '.gradle' \
  --exclude 'build' \
  --exclude '*/build' \
  --exclude '.kotlin' \
  --exclude '.idea' \
  --exclude 'local.properties' \
  "$SHELL_ROOT/" "$WORK/"

mkdir -p "$WORK/ComposeMarkdown/src/commonMain"
cp -R "$ROOT/markdown/src/commonMain/" "$WORK/ComposeMarkdown/src/commonMain/"
mkdir -p "$WORK/ComposeMarkdown/src/nativeStreamingMain"
cp -R "$ROOT/markdown/src/nativeStreamingMain/" "$WORK/ComposeMarkdown/src/nativeStreamingMain/"
mkdir -p "$WORK/ComposeMarkdown/src/nativeMain" "$WORK/ComposeMarkdown/src/androidMain" "$WORK/ComposeMarkdown/src/jsMain"
cp -R "$ROOT/markdown/src/nativeMain/" "$WORK/ComposeMarkdown/src/nativeMain/"
cp -R "$ROOT/markdown/src/androidMain/" "$WORK/ComposeMarkdown/src/androidMain/"
cp -R "$ROOT/markdown/src/jsMain/" "$WORK/ComposeMarkdown/src/jsMain/"

mkdir -p "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown"
cp "$ROOT/demo/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/MarkdownSyntaxDemoPage.kt" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/MarkdownSyntaxDemoPage.kt"
cp "$ROOT/demo/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/Platform.kt" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/Platform.kt"
cp -R "$ROOT/demo/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/demo/" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/demo/"
cp "$ROOT/demo/src/androidMain/kotlin/com/tencent/kuiklybase/markdown/Platform.android.kt" \
   "$WORK/shared/src/androidMain/kotlin/com/tencent/kuiklybase/markdown/Platform.android.kt"
mkdir -p "$WORK/androidApp/src/main/assets"
cp "$ROOT/app/src/main/assets/markdown_full_syntax.md" \
   "$WORK/androidApp/src/main/assets/markdown_full_syntax.md"

python3 - "$WORK" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1])

def replace(path, pairs):
    p = root / path
    s = p.read_text()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(f"missing expected text in {path}: {old}")
        s = s.replace(old, new)
    p.write_text(s)

replace("buildSrc/src/main/java/KotlinBuildVar.kt", [
    ('private const val KUIKLY_VERSION = "2.24.0"', 'private const val KUIKLY_VERSION = "2.30.0"'),
])

replace("settings.gradle.kts", [
    ('include(":KuiklyMarkdown")\ninclude(":KuiklyMarkdownCompose")', 'include(":ComposeMarkdown")'),
])

(root / "ComposeMarkdown/build.gradle.kts").write_text('''plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs += listOf("-Xskip-metadata-version-check")
            }
        }
    }
    js(IR) { browser() }
    iosX64(); iosArm64(); iosSimulatorArm64()
    macosX64(); macosArm64()

    sourceSets {
        val commonMain by getting {
            val coreVersion = Version.getKuiklyVersion()
            dependencies {
                implementation("com.tencent.kuikly-open:core:${coreVersion}")
                implementation("com.tencent.kuikly-open:core-annotations:${coreVersion}")
                implementation("com.tencent.kuikly-open:compose:${coreVersion}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                api("org.jetbrains:markdown:0.7.18") {
                    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
                }
            }
        }
        val nativeStreamingMain by creating {
            dependsOn(commonMain)
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val androidMain by getting {
            dependsOn(nativeStreamingMain)
        }
        val jsMain by getting {
            dependsOn(nativeStreamingMain)
        }
        listOf("iosArm64Main", "iosX64Main", "iosSimulatorArm64Main", "macosX64Main", "macosArm64Main").forEach {
            findByName(it)?.dependsOn(nativeStreamingMain)
            findByName(it)?.dependsOn(nativeMain)
        }
    }
}

android {
    namespace = "com.tencent.kuiklybase.markdown"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
}
''')

build = root / "build.gradle.kts"
build.write_text(build.read_text().replace('version("8.5.2")', 'version("8.6.1")'))

replace("shared/build.gradle.kts", [
    ('implementation(project(":KuiklyMarkdown"))', ''),
    ('implementation(project(":KuiklyMarkdownCompose"))', 'implementation(project(":ComposeMarkdown"))'),
])

replace("androidApp/build.gradle.kts", [
    ('implementation(project(":KuiklyMarkdownCompose"))', 'implementation(project(":ComposeMarkdown"))'),
])

legacy_pages = root / "shared/src/commonMain/kotlin/com/kuikly/kuiklymarkdown"
if legacy_pages.exists():
    import shutil
    shutil.rmtree(legacy_pages)

replace("androidApp/src/main/java/com/kuikly/kuiklymarkdown/KuiklyRenderActivity.kt", [
    ('"router"', 'intent.getStringExtra("pageName") ?: "markdown_syntax"'),
])

host_activity = root / "androidApp/src/main/java/com/kuikly/kuiklymarkdown/KuiklyRenderActivity.kt"
host_activity.write_text(''.join(
    line for line in host_activity.read_text().splitlines(keepends=True)
    if 'bindLatexActivity' not in line and 'unbindLatexActivity' not in line
       and 'import com.tencent.kuiklybase.markdown.compose.bindLatexActivity' not in line
       and 'import com.tencent.kuiklybase.markdown.compose.unbindLatexActivity' not in line
))

manifest = root / "androidApp/src/main/AndroidManifest.xml"
s = manifest.read_text()
if 'android.permission.INTERNET' not in s:
    s = s.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
                  '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.INTERNET" />')
s = s.replace(
    'android:name=".KuiklyRenderActivity"\n            android:exported="true"',
    'android:name=".KuiklyRenderActivity"\n            android:exported="true"\n            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|density|uiMode|smallestScreenSize"',
)
manifest.write_text(s)
PY

cd "$WORK"
if [ "$VARIANT" = "release" ]; then
  ./gradlew :androidApp:assembleRelease --console=plain --no-daemon
else
  ./gradlew :androidApp:assembleDebug --console=plain --no-daemon
fi

APK="$WORK/androidApp/build/outputs/apk/$VARIANT/androidApp-$VARIANT.apk"
OUT="$ROOT/app/build/outputs/apk/$VARIANT/app-syntax-demo-2.30-$VARIANT.apk"
mkdir -p "$(dirname "$OUT")"
cp "$APK" "$OUT"
echo "SYNTAX_DEMO_2_30_APK=$OUT"
