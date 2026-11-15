<div align="center">

# Kuikly Compose Markdown

基于 Kuikly Compose DSL 的 Markdown 渲染组件

[![KMP](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Android-✓-3DDC84?logo=android)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-✓-000000?logo=apple)](https://developer.apple.com)

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
| iOS | 编译产物已发布，未做本轮 UI 运行验证 |
| HarmonyOS (OHOS) | 标准 0.4.3 不含 OHOS target，不提供 0.4.3-ohos 坐标 |
| macOS | 编译产物已发布，未做本轮 UI 运行验证 |
| JS | parser/state Node 运行测试通过，未做完整 UI 运行验证 |

## 快速开始

### 1. 添加 Maven 仓库

```kotlin
// settings.gradle.kts 的 dependencyResolutionManagement.repositories
repositories {
    maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
}
```

### 2. 添加依赖

```kotlin
// build.gradle.kts
dependencies {
    // Android / iOS / macOS / JS
    implementation("com.tencent.kuiklybase:markdown:0.4.3")
}
```

`0.4.3` 已发布到公共仓库，可直接按上述仓库地址解析，不需要 `mavenLocal()`。
远端 `0.4.2` 是不可变的旧版本，存在会话重置、链接失效等问题，新接入应使用 `0.4.3`。
`maven-metadata.xml` 的 `latest/release` 均为 `0.4.3`；已用禁用 `mavenLocal()` 的最小工程验证 `:app:assembleDebug`，依赖树为 `markdown-android:0.4.3 -> org.jetbrains:markdown:0.7.18`。

标准 Android / Kuikly 2.30 的 release 接入与模拟器真实 SSE、多轮、Stop、旋转恢复已通过，35 个 JVM/Android 回归及 JS Node 运行测试通过。当前没有已知阻断该范围接入的审查项，完整证据与资源边界见 [PRODUCTION_REVIEW.md](PRODUCTION_REVIEW.md)。这不代表 OHOS、所有宿主或无限输入都经过验证。

当前标准构建使用 Kotlin 2.1.21、Kuikly Compose 2.30.0、AGP 8.6.1 和 Gradle 8.7。
Compose 与协程依赖作为公共 API 传递；宿主仍需要对应的 Kuikly 原生 render 容器。
0.4.3 不承诺与旧 Kotlin 1.9.22 / Kuikly 2.12 的二进制兼容。

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

### 静态文档 / 全量更新

```kotlin
@Composable
fun StaticMarkdown(content: String) {
    val markdownState = rememberMarkdownState()
    LaunchedEffect(content) {
        markdownState.parse(content, false)
    }
    Markdown(state = markdownState)
}
```

`Markdown(content)` / `MarkdownState.parse(content)` 适合一次性文档或非 append-only 文本替换。

### AI 对话流式渲染（推荐）

```kotlin
import com.tencent.kuiklybase.markdown.compose.StreamingMarkdownContent
import com.tencent.kuiklybase.markdown.model.rememberStreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.bufferMarkdownChunks

@Composable
fun StreamingAnswer(deltas: Flow<String>) {
    val state = rememberStreamingMarkdownState()

    LaunchedEffect(deltas) {
        state.reset()
        deltas.bufferMarkdownChunks().collect { delta -> state.append(delta) }
        state.finish() // flush CRLF 边界并做一次完整 parse 校验
    }

    StreamingMarkdownContent(state)
}
```

要点：`append()` 只接受 append-only delta，不传完整前缀；`finish()` 必须调用且可重复调用；完成后再次追加必须先 `reset()`。生产环境保持 `diagnosticsEnabled = false`。网络 delta 以 33-50ms 合并后再 `append()`；巨大未闭合代码块/表格会扩大 active tail，demo 在 16K/64K 尾部时采用 100/200ms 的发布间隔。取消/解析失败后先 `reset()` 再开始新会话，不继续使用已取消的解析器。

`StreamingMarkdownContent(state)` 可直接使用默认样式，会提供该文档独立的链接处理器与缓存；有主题需求时用 `Markdown(state, colors, typography)` 或 CompositionLocal 覆盖。聊天页外层使用一个对话 `LazyColumn`，每条回答一个 item；非滚动的 `StreamingMarkdownContent` 不会引入嵌套 LazyColumn。

历史回答保存 `state.finish()` 返回的快照，并用 `MarkdownSnapshotContent(snapshot)` 渲染；重置/继续其他会话不会修改快照内容和引用定义。默认单 item 是全量挂载，不是无限规模虚拟化；超大文档可用逐块 `LazyMarkdownSuccess`，宿主须提供有界高度。

自定义 `ReferenceLinkHandler` 必须实现 `clear()`，方法须线程安全且不可阻塞。UI 风格、宽度及组件配置发生合法变化时允许重新组合/布局，不应把这种必要工作算成性能回归。

流式默认预算为文档 1,000,000 字符、待重新解析的尾部 262,144 字符，可通过 `StreamingMarkdownLimits` 调整。
超出预算会抛出明确异常并保留最后已发布画面，不静默截断；失败后先 `reset()`。宿主应处理异常、限制模型输出，并按设备资源配置预算。
增量 parser 仍会重解析未闭合的大块，预算与合帧是资源保护，不代表任意长度的未闭合 Markdown 都能保持固定帧耗时。

`Flow.collectAsStreamingMarkdownState()` 自动合并输入；上游失败时完成已收到的部分正文并写入 `state.error`，不从内部 effect 抛出未处理异常。取消仍按协程约定传播。直接调用 `append()` / `finish()` 的业务代码仍应捕获异常。

诊断模式仅在性能调查时开启：

```kotlin
val state = rememberStreamingMarkdownState(diagnosticsEnabled = true)
StreamingMarkdownContent(state, diagnosticsEnabled = true)
```

诊断模式会暴露重组计数、记录稳定区测量并允许 demo 侧启用 Kuikly `RecompositionProfiler`；它不是生产默认路径，不应在业务包中长期开启。

## 发布验证

`0.4.3` 公共 Maven 发布已验证根 POM、Android POM/module/AAR 以及 iOS/macOS/JS 平台 POM 可下载；远端 AAR 与本地 release AAR SHA-256 一致：`3e5ddd35d7bc5f6eb27e4177b1240f2bed9b50218397d5145c06e7d5c413892c`。发布记录见 [PRODUCTION_RELEASE_CHECKLIST.md](PRODUCTION_RELEASE_CHECKLIST.md)。

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
