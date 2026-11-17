### [Unreleased]

1. 全语法 Demo 增加实时回答高度观测和高度槽位：用 `onSizeChanged` 获取当前高度，按视口比例触发 Kuikly `LazyList` 的 item-count 扩容分支，解决流式增长期间滚到「九、表格」附近被旧原生 contentSize 回弹的问题；Markdown 回答仍是一个非 Lazy 气泡 item。
2. 「重新流式播放」只递增回放版本，不再切换 `streaming` 模式，避免重建 LaunchedEffect 与滚动结构；Demo 输出间隔从 50ms 调为 80ms。Release 真机重新播放慢拖窗口 874 帧、Janky 0.57%、P99 17ms；Debug + Perfetto 的 300ms P99 经 trace 归因于 JIT/GC/输入事件与采集开销，详见 `LONG_MARKDOWN_PERF_ANALYSIS.md`。
3. 新增可选 GitHub 风格 Emoji 短代码文本扩展：按 AST token 边界替换普通正文里已闭合的 `:alias:`，覆盖全语法 Demo 的 44 个别名及 `+1` / `:bulb:` 等常用变体；代码片段、链接 URL、未知/未闭合/反斜杠转义短代码保持原样。可通过 `Markdown(emojiShortcodes = ...)`、`StreamingMarkdownContent(emojiShortcodes = ...)` 或 `LocalMarkdownEmojiShortcodes` 关闭/自定义，不影响 Markdown AST 与流式增量解析结构。
4. 流式重组优化：诊断标签独立组合，稳定 Demo 的 CompositionLocal 身份，高度 slot 仅在跨阈值时写回；新增按 block 的 `StateFlow<Int>` 引用链接 generation 与 `LocalMarkdownBlockLinkGeneration`，晚到 link definition 只失效含对应 label 的 block，不再全局 `renderCache.clear()`；generation 纳入富文本缓存 key；修复 `finish()` final parse 新增稳定块未补齐 render segments 的 parity 问题；删除不必要的 `movableContentOf`；滚动恢复增加 120ms 手势冷却。新增局部失效、按 block Flow、晚到链接、final parity、缓存 generation 与 counter reset 单测。优化前真机固定慢拖基线：547 帧、Janky 1.83%、P95 14ms、P99 25ms、Slow UI 8，`screen=26 stableRegion=16 activeTail=17 stableBlocks=384 tailBlocks=18`。最终生产门禁 `tests=64 lint_errors=0`，JS 回归与 OHOS KBA 编译通过；最终无诊断日志模拟器窗口 411 帧、legacy Janky 0.73%、P95 20ms、P99 20ms、Slow UI 0，`screen=1 stableRegion=25 activeTail=27 stableBlocks=240 tailBlocks=27`。最终 APK SHA-256 `aea830e498f550bb6ea1ba63e0cd3cd6466b3c554c4e454a85ed243cd5899e0f`。真机后测被 OPPO 未验证应用提醒的图形滑块安全验证阻塞，需人工完成验证后补测。

### [0.4.4] 2026-11-16

1. 发布 `==highlight==` 柠檬黄 RichText span 背景、代码块语法着色、GitHub Alerts、数学文本保留、邮箱自动链接、引用图片与完整行分块等上游能力。
2. 完成消息级列表、一气泡一 item、非 Lazy 有界渲染分片、active tail、稳定分片身份和完成态结构复用。
3. 新增 `pauseUiUpdates()` / `resumeUiUpdates()` 滚动发布闸门、`rememberStreamingMarkdownHost` 可见内容锚定、`rememberStreamingMarkdownListState` 的 2.30 cache window 与 OHOS 2.12 兼容回退。
4. `PRODUCTION_GATES_OK version=0.4.4 ... tests=52 lint_errors=0`；JS 回归、OHOS KBA、2.30 Demo/AI Chat shell、远端 HTTP/哈希/metadata、远端-only Android consumer clean build 均通过。Android release 最终稳态窗口 845 帧、Janky 0.95%、P95 13ms、P99 16ms、Missed Vsync 0，且截图仍有 20,647 个高亮像素。
5. 详见 `PRODUCTION_LONG_MARKDOWN_PLAN.md`、`LONG_MARKDOWN_PERF_ANALYSIS.md` 和 `PRODUCTION_RELEASE_CHECKLIST.md`；iOS/JS/OHOS 真机运行级验证仍是剩余边界。

### [0.4.4-ohos] 2026-11-16

1. KBA Kotlin 2.0.21-KBA-010 / Kuikly 2.12.0-2.0.21-ohos 编译并发布 `ohos_arm64` klib。
2. OHOS 2.12 无 `LazyLayoutCacheWindow`，公共 `rememberStreamingMarkdownListState` 通过 `expect/actual` 回退普通 `rememberLazyListState()`。
3. 远端 AAR/klib 哈希与本地 release 产物一致；OHOS 真机 UI 运行验证仍待补。
4. 对应标准版 0.4.4 的上游渲染能力、流式分片、发布闸门和验收文档；OHOS parser 仍是 0.7.4-ohos 完整 parse 兼容层，不等价标准 0.7.18 增量解析。详见 `PRODUCTION_LONG_MARKDOWN_PLAN.md`、`LONG_MARKDOWN_PERF_ANALYSIS.md`。

### [0.4.3] 2026-11-15

1. 流式会话 reset/取消隔离、引用表清理及富文本缓存可观察失效。
2. 稳定块局部 source；finish 深层 AST 一致的稳定 slot 不再替换；历史快照渲染独立于 live state。
3. 默认公共 API 可直接渲染；components/回调合法更新生效；长代码完整行分块。
4. 流式合帧不丢字，上游异常可观察，文档与待解析尾部有显式资源预算。
5. 标准构建对齐 Kotlin 2.1.21 / Kuikly 2.30；增加 JVM、JS、模拟器、发布元数据验证。
6. 已发布到公共 Maven，远端 AAR 与本地 release 产物哈希一致；0.4.2 不覆盖。标准版本不含 OHOS target；OHOS 使用单独的 `0.4.3-ohos` 坐标。

### [0.4.3-ohos] 2026-11-16

1. 使用 KBA Kotlin 2.0.21-KBA-010 编译并发布 `ohos_arm64` klib。
2. 使用 OHOS 专用 Kuikly 2.12.0-2.0.21-ohos 与 `markdown-parser:0.7.4-ohos` 依赖链。
3. 为旧 KBA parser 增加流式 API 兼容层；active tail 在 finish 时统一完整解析，运行级 Harmony UI 回归仍待补。
4. 不含 macOS target；macOS 使用标准 0.4.3。

### [1.0.2]
1. 修复表格单元格内自定义Annotator不生效问题；
2. 鸿蒙版本号为1.0.2-ohos；

### [1.0.1]

1. 升级NTCompose版本到1.3.13版本，解决LazyRow方法签名问题
2. 支持显示图片
3. 支持自定义Text组件，通常可用于自定义字号缩放和修改默认字体
4. 鸿蒙版本号为1.0.1-ohos。

### [1.0.0]

1. 从Jetbrains Compose迁移到Kuikly Compose（v1.3.8)。
2. 优化流式输入性能，实现组件级别增量更新。暂不支持流式输入的字符串长度逐渐变短的Case。
3. 优化表格绘制逻辑。
4. 鸿蒙版本号为1.0.0-ohos。

You can find a detailed change log in
the [release section](https://github.com/mikepenz/multiplatform-markdown-renderer/releases)
