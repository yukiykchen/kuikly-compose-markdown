<div align="center">

# Kuikly Compose Markdown

基于 Kuikly Compose DSL 的 Markdown 渲染组件

[![KMP](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Android-✓-3DDC84?logo=android)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-✓-000000?logo=apple)](https://developer.apple.com)
[![HarmonyOS](https://img.shields.io/badge/HarmonyOS-✓-000000)](https://developer.harmonyos.com)

</div>

## 简介

`kuikly-compose-markdown` 是一个为 [KuiklyUI](https://github.com/Tencent-TDS/KuiklyUI) **Compose DSL** 提供的 Markdown 渲染组件，支持将 Markdown 文本渲染为原生 Compose UI，适用于 AI 对话、文档展示、富文本内容等场景。

> **注意**：本组件面向 Kuikly **Compose DSL**。如果你使用的是 Kuikly DSL，请关注后续的 Kuikly DSL 版本 Markdown 组件。

## 功能特性

- 📝 **标题**：支持 H1 ~ H6 多级标题
- **加粗** / *斜体* / ~~删除线~~
- 📋 **列表**：有序列表、无序列表
- 💬 **引用块**（Blockquote）
- 📊 **表格**：含表头样式
- 💻 **代码块** 与 `行内代码`
- 🔗 **链接**
- 🎨 **可自定义颜色**（`MarkdownColors`）与 **排版样式**（`MarkdownTypography`）
- ⚡ **流式渲染**：支持 SSE 等流式数据逐步渲染，适配 AI 对话场景

## 平台支持

| 平台 | 支持 |
|------|------|
| Android | ✅ |
| iOS | ✅ |
| HarmonyOS (OHOS) | ✅ |
| macOS | ✅ |
| JS | ✅ |

## 快速开始

### 1. 添加 Maven 仓库

```kotlin
// settings.gradle.kts
repositories {
    maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
}
```

### 2. 添加依赖

```kotlin
// build.gradle.kts
dependencies {
    // Android / iOS / macOS / JS
    implementation("com.tencent.kuiklybase:markdown:0.4.1")
    
    // HarmonyOS
    implementation("com.tencent.kuiklybase:markdown:0.4.1-ohos")
}
```

## 使用示例

### 基础用法

```kotlin
import com.tencent.kuiklybase.markdown.compose.Markdown
import com.tencent.kuiklybase.markdown.model.rememberMarkdownState

@Composable
fun MarkdownDemo() {
    val markdownState = rememberMarkdownState()

    LaunchedEffect(Unit) {
        markdownState.parse("# Hello Markdown\n这是一段 **Markdown** 文本", false)
    }

    Markdown(state = markdownState)
}
```

### 自定义颜色与排版

```kotlin
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownColors
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownTypography

@Composable
fun StyledMarkdown(content: String) {
    val markdownState = rememberMarkdownState()

    LaunchedEffect(content) {
        markdownState.parse(content, false)
    }

    Markdown(
        state = markdownState,
        colors = DefaultMarkdownColors(
            text = Color.Black,
            codeBackground = Color(0xFFF5F5F5),
            tableBackground = Color(0xFFFAFAFA),
            tableStroke = Color(0xFFE0E0E0),
            // ... 更多颜色配置
        ),
        typography = DefaultMarkdownTypography(
            h1 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            text = TextStyle(fontSize = 16.sp, lineHeight = 28.sp),
            code = TextStyle(fontSize = 14.sp),
            // ... 更多排版配置
        )
    )
}
```

### AI 对话流式渲染

```kotlin
@Composable
fun StreamingMarkdown(streamingText: String) {
    val markdownState = rememberMarkdownState()

    // 每次文本更新时重新解析
    LaunchedEffect(streamingText) {
        markdownState.parse(streamingText, false)
    }

    Markdown(
        state = markdownState,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
```

## API 参考

### Markdown

主渲染 Composable 组件。

| 参数 | 类型 | 说明 |
|------|------|------|
| `state` | `MarkdownState` | Markdown 解析状态，通过 `rememberMarkdownState()` 创建 |
| `colors` | `MarkdownColors` | 颜色配置，可使用 `DefaultMarkdownColors(...)` 自定义 |
| `typography` | `MarkdownTypography` | 排版配置，可使用 `DefaultMarkdownTypography(...)` 自定义 |
| `modifier` | `Modifier` | Compose Modifier |

### MarkdownColors

可自定义的颜色属性：

| 属性 | 说明 |
|------|------|
| `text` | 正文文本颜色 |
| `codeText` | 代码块文本颜色 |
| `inlineCodeText` | 行内代码文本颜色 |
| `linkText` | 链接文本颜色 |
| `codeBackground` | 代码块背景色 |
| `inlineCodeBackground` | 行内代码背景色 |
| `dividerColor` | 分割线颜色 |
| `tableText` | 表格文本颜色 |
| `tableBackground` | 表格背景色 |
| `tableHeaderBackground` | 表头背景色 |
| `tableStroke` | 表格边框颜色 |

### MarkdownTypography

可自定义的排版样式（均为 `TextStyle` 类型）：

`h1` · `h2` · `h3` · `h4` · `h5` · `h6` · `text` · `quote` · `code` · `inlineCode` · `paragraph` · `ordered` · `bullet` · `list` · `link` · `textLink` · `table`

## 完整示例

查看 KuiklyUI 官方 Demo 中的 AI Chat 示例：

👉 [ChatDemo 源码](https://github.com/Tencent-TDS/KuiklyUI/tree/main/demo/src/commonMain/kotlin/com/tencent/kuikly/demo/pages/compose/chatDemo)

<div style="display: flex; flex-wrap: wrap; justify-content: center; gap: 20px;">
    <img src="https://raw.githubusercontent.com/Tencent-TDS/KuiklyUI/main/img/chatdemo_android.png" width="200" />
    <img src="https://raw.githubusercontent.com/Tencent-TDS/KuiklyUI/main/img/chatdemo_ios.png" width="200" />
    <img src="https://raw.githubusercontent.com/Tencent-TDS/KuiklyUI/main/img/chatdemo_ohos.jpg" width="200" />
</div>

## 许可证

本项目遵循 [MIT License](LICENSE)。