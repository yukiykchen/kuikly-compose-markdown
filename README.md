<div align="center">

# Kuikly Compose Markdown

基于 Kuikly Compose DSL 的 Markdown 渲染组件

[![KMP](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Android-✓-3DDC84?logo=android)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-✓-000000?logo=apple)](https://developer.apple.com)
[![HarmonyOS](https://img.shields.io/badge/HarmonyOS-✓-2F80ED)](https://developer.harmonyos.com)
[![macOS](https://img.shields.io/badge/macOS-✓-111111?logo=apple)](https://developer.apple.com/macos/)
[![JS](https://img.shields.io/badge/JS-✓-F7DF1E?logo=javascript)](https://developer.mozilla.org/docs/Web/JavaScript)

</div>

## 简介

`kuikly-compose-markdown` 是一个为 [KuiklyUI](https://github.com/Tencent-TDS/KuiklyUI) **Compose DSL** 提供的全平台 Markdown 渲染组件，支持将 Markdown 文本渲染为原生 Compose UI，适用于 AI 对话、文档展示、富文本内容等场景。

> **注意**：本组件面向 Kuikly **Compose DSL**。如果你使用的是 Kuikly DSL，请关注后续的 Kuikly DSL 版本 Markdown 组件。

## 功能特性

- 📝 **标题**：支持 H1 ~ H6 多级标题
- **加粗** / *斜体* / ~~删除线~~ / `==高亮文字与柠檬黄背景==`（Kuikly 2.30+ Android 原生 RichText span 已验证）
- 📋 **列表**：有序列表、无序列表
- 💬 **引用块**（Blockquote）
- 🚨 **GitHub Alerts**：`NOTE` / `TIP` / `IMPORTANT` / `WARNING` / `CAUTION`
- 📊 **表格**：含表头样式
- 💻 **代码块语法着色** 与 `行内代码`
- 🔗 **链接**：内联/引用链接、GFM 裸链接、`<user@example.com>` 邮箱自动链接
- 🖼️ **图片**：支持引用式图片 destination 与 alt 文本
- 💲 **数学公式原文保留**：`$...$` / `$$...$$` 暂不做公式排版，但不会丢字
- 😄 **Emoji 短代码扩展**：默认把普通正文中的 `:rocket:` 等 GitHub 风格短代码转成 Unicode Emoji；代码片段、链接 URL、未知/未闭合/反斜杠转义短代码保持原文
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

支持 Android、iOS、HarmonyOS (OHOS)、macOS 与 JS 的 Markdown 渲染和流式更新。

## 快速开始

### 1. 添加 Maven 仓库

```kotlin
// settings.gradle.kts 的 dependencyResolutionManagement.repositories
repositories {
    maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
}
```

### 2. 添加依赖

标准平台（Android / iOS / macOS / JS）：

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.tencent.kuiklybase:markdown:0.4.4")
}
```

HarmonyOS (OHOS)：

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.tencent.kuiklybase:markdown:0.4.4-ohos")
}
```

`0.4.4` 与 `0.4.4-ohos` 均已发布到公共仓库，按上述仓库地址即可解析，不需要 `mavenLocal()`。请显式指定目标平台对应的版本；Android-only consumer 若直接使用 Gradle JVM 依赖，应解析 `com.tencent.kuiklybase:markdown-android:0.4.4`。

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

### Emoji 短代码

`:shortcode:` 不是 CommonMark / GFM 标准语法，而是部分 Markdown 平台的扩展。组件默认启用截图中的常用别名；严格 Markdown 场景可以关闭，业务也可以传入自己的映射：

```kotlin
import com.tencent.kuiklybase.markdown.compose.Markdown
import com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes

Markdown(
    content = "部署状态 :rocket: :white_check_mark:",
    emojiShortcodes = null, // 保持 :rocket: 原文
)

Markdown(
    content = ":done:",
    emojiShortcodes = MarkdownEmojiShortcodes { alias ->
        if (alias == "done") "✅" else null
    },
)
```

替换只作用于渲染后的正文文本，不改变 Markdown AST，也不改写源文本；流式过程中只显示已完成的 `:alias:`，半截短代码会原样保留直到闭合。

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

`StreamingMarkdownContent(state)` 可直接使用默认样式，会提供该文档独立的链接处理器与缓存；有主题需求时用 `Markdown(state, colors, typography)` 或 CompositionLocal 覆盖。聊天页外层使用一个对话 `LazyColumn`，每条回答一个 item；非滚动的 `StreamingMarkdownContent` 不会引入嵌套 LazyColumn。AI Chat Demo 的跟底采用固定 footer + `contentRevision` 更新时机：近底且 `autoFollow=true` 时内容提交后自动到底；用户上滑离底则关闭跟底并暂停 UI commit，滑回近底再恢复合并发布和最终 parse，不需要回底按钮。组件仍保留通用 `rememberStreamingMarkdownHost`，供需要可见锚点恢复的全语法 Demo 使用。

单条回答超过视口 50% 时，可用 `rememberStreamingMarkdownListState(aheadFraction, behindFraction)` 扩大 Kuikly `LazyLayoutCacheWindow`；2.30 默认预取窗口对超高 item 可能触发过晚。该值必须按设备 A/B 调整，不是越大越好。当前 2.30.0-2.1.21 Compose 产物源码中已包含 Kuikly #1523 的每帧合并 semantics 刷新与 #1791 的 measure 后 draw flush；若其他宿主版本仍有白屏，应先核对实际解析到的依赖产物。

全语法 Demo 仍使用 `onSizeChanged` 和零高度 `content-height-slot` 扩容原生滚动边界；AI Chat Demo 已改为固定 footer 和 `scrollChatToBottom()`，不再按正文长度追加 slot。两种策略分别服务于「语法回放时精确扩容」和「对话流式时稳定跟底」，不能混在同一个外层 `LazyColumn` 中叠加使用。

历史回答保存 `state.finish()` 返回的快照；需要脱离 live state 渲染时才用 `MarkdownSnapshotContent(snapshot)`。当前回答与全语法 Demo 的完成态都继续保留 `StreamingMarkdownContent` 的同一套结构，不在 `finish()` 后切换整棵布局树；稳定区由非 Lazy 的有界 `MarkdownRenderSegment` 组成，短段落合片，代码块、表格、图片等重块单独成片，active tail 仍是唯一高频变化区域。解析仍按完整 Markdown 语义进行，分片只影响渲染组合边界，不按字符切分后独立 parse。分组不是天然优化：固定真机回放中，仅改分片曾使 P95 从 25ms 升到 61ms；叠加 `LazyLayoutCacheWindow` 与滚动发布闸门后才恢复到 Janky 11.11%、P95 25ms、P99 350ms。流式回答内部不再使用逐块 Lazy：逐块虚拟化会让动态高度 block 在滑动时出现空白，而且 Kuikly `LazyList` 在 `isScrollInProgress` 期间不会扩容 item 高度，流式改高容易让原生滚动边界回弹。此前逐块 Lazy 的真机窗口 Janky 22.14%、P95 31ms、P99 53ms 说明该路径不适合正在增长的 Markdown。OHOS 2.12/KBA 使用 `markdown-parser:0.7.4-ohos` 兼容层：KBA 编译已验证，但该 parser 没有流式 AST API，每次 append 会做完整 parse，不能与标准 0.7.18 的增量解析性能画等号。

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

## 发布状态

当前版本为 `0.4.4` / `0.4.4-ohos`，已发布至公共 Maven 仓库；标准 KMP 产物覆盖 Android、iOS、macOS 与 JS，OHOS KBA 使用独立坐标。本阶段按 Android / Kuikly 2.30 完成生产级验证，包含流式渲染分片、文本高亮、发布闸门和 cache window 适配。远端发布与接入验证见 `PRODUCTION_RELEASE_CHECKLIST.md`，Android 验收边界见 `PRODUCTION_LONG_MARKDOWN_PLAN.md`。

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
| `highlightText` | `==高亮==` 文字颜色 |
| `highlightBackground` | `==高亮==` 背景色 |
| `alertColors` | GitHub Alert 五种类型的强调色 |
| `dividerColor` | 分割线颜色 |
| `tableText` | 表格文本颜色 |
| `tableBackground` | 表格背景色 |
| `tableHeaderBackground` | 表头背景色 |
| `tableStroke` | 表格边框颜色 |

### MarkdownTypography

可自定义的排版样式（均为 `TextStyle` 类型）：

`h1` · `h2` · `h3` · `h4` · `h5` · `h6` · `text` · `quote` · `code` · `inlineCode` · `paragraph` · `ordered` · `bullet` · `list` · `link` · `textLink` · `table`

### 代码块高亮

默认对 fenced code / indented code 做关键字、字符串、数字、注释、注解、标点着色，并显示语言标签；保持完整行分块与流式增量渲染。能力对齐上游 `multiplatform-markdown-renderer-code` 的 token 分类；实现使用组件内、无额外 Maven 依赖的 Kuikly Compose 词法器，因此不会把官方 Compose 或不含 `ohos_arm64` target 的第三方 klib 拉进依赖图。主题颜色由 `DefaultMarkdownCodeHighlighter` 提供；如需接入自己的词法器：

```kotlin
CompositionLocalProvider(
    LocalMarkdownCodeHighlighter provides MarkdownCodeHighlighter { code, language ->
        emptyList() // 返回 MarkdownCodeHighlight 列表；空列表表示不着色
    },
) {
    Markdown(state = markdownState)
}
```

`MarkdownCode(code, language, highlighterEnabled = false)` 可关闭单个代码块的着色。

Kuikly Compose 2.30+ 会把 `SpanStyle.background` 映射为 RichText span 的 `backgroundColor`；Android 2.30 renderer 使用 `BackgroundColorSpan` 绘制，真机验证 `==高亮==` 为柠檬黄背景加深色文字。默认背景为 `#FFF44F`（Lemon Yellow），默认文字为 `#202124`，可通过 `MarkdownColors.highlightBackground` / `highlightText` 覆盖。不要启用旧的 `RoundedCornerSpanPainter`：Kuikly 的 `TextLayoutResult` 端口没有字符 bounding boxes，该 painter 会移除背景却无法补画。Kuikly 2.24 及更早版本没有这条 bridge，只能得到文字着色；请升级到 2.30+，不要使用下划线冒充高亮。iOS 模拟器最小探针显示文字可渲染但未出现背景像素；JS 与 OHOS KBA renderer 未做运行级验证。本阶段只承诺 Android 高亮背景，其他平台接入时应单独截图确认。

### 上游能力同步边界

当前组件保留自己的 Kuikly Compose 类型、流式 stable/tail 增量渲染和 OHOS/KBA 兼容层。上游的 `retainState` 状态机、Coil/Material 主题、官方 Compose `ImageVector` 图标、clipboard、semantics 与 hover popup 没有直接搬入；它们依赖另一套 Compose API 或会替换现有流式优化。GitHub Alerts 使用 Kuikly 原生布局与文本标题实现，颜色、尺寸、内边距和标题样式可通过 `markdownAlertColors()` / `markdownAlertDimens()` / `markdownAlertPadding()` / `MarkdownTypography.alertTitle` 覆盖。

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
