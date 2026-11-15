#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SHELL_ROOT="${AI_CHAT_230_SHELL:-/Users/yukiykchen/project/KuiklyMarkdown}"
WORK="${AI_CHAT_230_WORK:-/tmp/kuikly-compose-markdown-ai-chat-230-shell}"
LOCAL_PROPS="$ROOT/local.properties"
VARIANT="${AI_CHAT_VARIANT:-debug}"
case "$VARIANT" in
  debug|release) ;;
  *) echo "AI_CHAT_VARIANT must be debug or release" >&2; exit 2 ;;
esac

if ! grep -q '^aiChatApiKey=' "$LOCAL_PROPS"; then
  echo "Missing aiChatApiKey in $LOCAL_PROPS" >&2
  exit 1
fi

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
cp "$LOCAL_PROPS" "$WORK/local.properties"

mkdir -p "$WORK/ComposeMarkdown/src/commonMain"
cp -R "$ROOT/markdown/src/commonMain/" "$WORK/ComposeMarkdown/src/commonMain/"

mkdir -p "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown"
cp "$ROOT/demo/src/aiChat230Main/kotlin/com/tencent/kuiklybase/markdown/SseEventParser.kt" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/SseEventParser.kt"
cp "$ROOT/demo/src/aiChat230Main/kotlin/com/tencent/kuiklybase/markdown/AiChatDemoPage.kt" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/AiChatDemoPage.kt"
cp "$ROOT/demo/src/aiChat230Main/kotlin/com/tencent/kuiklybase/markdown/MarkdownRegressionPage.kt" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/MarkdownRegressionPage.kt"
cp "$ROOT/demo/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/ThemedMarkdown.kt" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/ThemedMarkdown.kt"
cp -R "$ROOT/demo/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/demo/" \
   "$WORK/shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/demo/"

python3 - "$WORK" <<'PY'
import sys
import os
import shutil
from pathlib import Path

root = Path(sys.argv[1])

def write(path, text):
    (root / path).write_text(text)

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
    ('include(":KuiklyMarkdownCompose")', 'include(":KuiklyMarkdownCompose")\ninclude(":ComposeMarkdown")'),
])

write("ComposeMarkdown/build.gradle.kts", '''plugins {
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
    }
}

android {
    namespace = "com.tencent.kuiklybase.markdown"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
}
''')

library_version = os.environ.get("AI_CHAT_LIBRARY_VERSION")
if library_version:
    if not all(char.isalnum() or char in '.-' for char in library_version):
        raise SystemExit("Invalid AI_CHAT_LIBRARY_VERSION")
    shutil.rmtree(root / "ComposeMarkdown/src/commonMain")
    module = root / "ComposeMarkdown/build.gradle.kts"
    text = module.read_text()
    text = text.replace('api("org.jetbrains:markdown:0.7.18")',
                        f'api("com.tencent.kuiklybase:markdown:{library_version}")')
    module.write_text(text)

build = root / "build.gradle.kts"
build.write_text(build.read_text().replace('version("8.5.2")', 'version("8.6.1")'))

replace("shared/build.gradle.kts", [
    ('implementation(project(":KuiklyMarkdown"))', ''),
    ('implementation(project(":KuiklyMarkdownCompose"))', 'implementation(project(":ComposeMarkdown"))'),
])

# The test shell must not carry its own parser fork alongside the dependency under test.
legacy_pages = root / "shared/src/commonMain/kotlin/com/kuikly/kuiklymarkdown"
if legacy_pages.exists():
    shutil.rmtree(legacy_pages)

replace("androidApp/build.gradle.kts", [
    ('plugins {', 'import java.util.Properties\n\nplugins {'),
    ('implementation(project(":KuiklyMarkdownCompose"))', ''),
    ('versionName = "1.0"', '''versionName = "1.0"

        val aiChatProps = Properties().also { props ->
            file("../local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
        }
        val aiChatApiKey = aiChatProps.getProperty("aiChatApiKey") ?: ""
        buildConfigField("String", "AI_CHAT_API_URL", "\\"https://api.moonshot.cn/v1/chat/completions\\"")
        buildConfigField("String", "AI_CHAT_MODEL", "\\"kimi-k2.6\\"")
        buildConfigField("String", "AI_CHAT_API_KEY", "\\"$aiChatApiKey\\"")'''),
])

replace("androidApp/src/main/java/com/kuikly/kuiklymarkdown/KuiklyRenderActivity.kt", [
    ('"router"', 'intent.getStringExtra("pageName") ?: "ai_chat"'),
    ('''    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        return param
    }''', '''    private fun createPageData(): Map<String, Any> {
        val params = org.json.JSONObject()
            .put("aiChatApiUrl", BuildConfig.AI_CHAT_API_URL)
            .put("aiChatModel", BuildConfig.AI_CHAT_MODEL)
            .put("aiChatApiKey", BuildConfig.AI_CHAT_API_KEY)
            .put("aiChatDiagnostics", intent.getBooleanExtra("aiChatDiagnostics", false))
            .put("mode", intent.getStringExtra("mode") ?: "live")
        return mapOf(
            "param" to params.toString(),
            "aiChatApiUrl" to BuildConfig.AI_CHAT_API_URL,
            "aiChatModel" to BuildConfig.AI_CHAT_MODEL,
            "aiChatApiKey" to BuildConfig.AI_CHAT_API_KEY,
            "aiChatDiagnostics" to intent.getBooleanExtra("aiChatDiagnostics", false),
            "mode" to (intent.getStringExtra("mode") ?: "live"),
        )
    }'''),
])

host_activity = root / "androidApp/src/main/java/com/kuikly/kuiklymarkdown/KuiklyRenderActivity.kt"
host_activity.write_text(''.join(
    line for line in host_activity.read_text().splitlines(keepends=True)
    if 'bindLatexActivity' not in line and 'unbindLatexActivity' not in line
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

activity = root / "shared/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/AiChatDemoPage.kt"
s = activity.read_text()
s = s.replace('                onSend = {\n                onSend = {\n', '                onSend = {\n')
s = s.replace('pageData.params.optString(PAGE_DATA_API_KEY).ifBlank {\n            pageData.params.optString("apiKey")\n        }', 'pageData.params.optString(PAGE_DATA_API_KEY)')
s = s.replace('''    private fun chatConfig(): ChatConfig = ChatConfig(
        apiUrl = pageData.params.optString(PAGE_DATA_API_URL, DEFAULT_API_URL),
        apiKey = pageData.params.optString(PAGE_DATA_API_KEY),
        model = pageData.params.optString(PAGE_DATA_MODEL, DEFAULT_MODEL),
    )''', '''    private fun chatConfig(): ChatConfig {
        val params = pageData.params
        val rawParams = runCatching {
            com.tencent.kuikly.core.nvi.serialization.json.JSONObject(
                pageData.params.optString("param", params.toString()),
            )
        }.getOrNull() ?: params
        fun value(key: String, fallback: String = ""): String =
            params.optString(key, fallback).ifBlank { rawParams.optString(key, fallback) }
        return ChatConfig(
            apiUrl = value(PAGE_DATA_API_URL, DEFAULT_API_URL),
            apiKey = value(PAGE_DATA_API_KEY),
            model = value(PAGE_DATA_MODEL, DEFAULT_MODEL),
        )
    }''')
activity.write_text(s)
PY

cd "$WORK"
if [ "$VARIANT" = "release" ]; then
  ./gradlew :androidApp:assembleRelease --console=plain
else
  ./gradlew :androidApp:assembleDebug --console=plain
fi

APK="$WORK/androidApp/build/outputs/apk/$VARIANT/androidApp-$VARIANT.apk"
OUT="$ROOT/app/build/outputs/apk/$VARIANT/app-ai-chat-2.30-$VARIANT.apk"
mkdir -p "$(dirname "$OUT")"
cp "$APK" "$OUT"
echo "AI_CHAT_2_30_APK=$OUT"
