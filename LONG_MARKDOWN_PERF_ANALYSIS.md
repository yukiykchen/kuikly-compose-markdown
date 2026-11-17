# 长 Markdown 流式 P99 尾帧分析

## 问题

Debug 包的固定流式滑动窗口多次出现 `P99 350ms~450ms`，但 P90/P95 分别为 20ms/25ms。需要确认这是 Markdown 组件稳定热点，还是 debug/JIT/GC/采集窗口叠加出来的尾部噪声。

## 证据

### 1. Perfetto（debug，热启动但仍在 JIT 预热）

- 配置：[scripts/perfetto-long-markdown.pftxt](scripts/perfetto-long-markdown.pftxt)。
- 原始 trace：`/tmp/markdown-perfetto/long-markdown-warm.pftrace`（20 MB）。
- Trace Processor v59.1；trace health 有 `trace_sorter_negative_timestamp_dropped: 22`，`android_frames` 非空且解析错误为 0，但负时间戳事件仍限制绝对时间结论。
- `android_frames` 共 282 帧；超过 50ms 的帧仅 3 个：`1020.2ms`、`422.0ms`、`100.7ms`。
- 最大帧 `frame_id=58326003` 与 `Choreographer#doFrame 58326003` 的 1014.4ms 对齐；其子树包含 `input` 1004.1ms、`deliverInputEvent` 1003.4ms、`ViewPostImeInputStage` 1003.0ms。
- 同一时间窗口另有 `Recomposer:recompose` 853.6ms（HRContextQueueH 线程）、`Compose:applyChanges` 383.0ms、`Background concurrent copying GC` 90.9ms，以及 `MarkdownText`、`MarkdownBlockQuote`、`DefaultMarkdownCodeHighlighter`、`HighlightDelimiterParser` 等 baseline JIT 编译切片。
- 可复跑查询：

```sql
INCLUDE PERFETTO MODULE android.frames.timeline;
SELECT frame_id, ts, dur, process_name
FROM android_frames
WHERE dur > 50000000
ORDER BY dur DESC;

SELECT s.name, ROUND(s.dur / 1e6, 1) AS ms, s.ts / 1e6 AS start_ms
FROM slice s
WHERE s.ts BETWEEN 1434531645e6 AND 1434532500e6
  AND s.dur > 50000000
ORDER BY s.ts;
```

结论：这轮 debug 长帧是「输入事件被首轮重组/JIT/GC 阻塞」的组合，不是单个稳定可优化的 Markdown 解析函数。ATRACE 没有给业务 Kotlin 函数打点，因此不能把 853ms 全归因于 `StreamingMarkdownState` 或某个 block renderer。

### 2. Release 稳态对照（无 Perfetto）

- APK：`app/build/outputs/apk/release/app-syntax-demo-2.30-release.apk`
- SHA-256：`5cdbae1a7b7ab8e90c19fb944525edd34e5f97079ca5ab025f3eea6869f6e48c`
- 真机：`VSHEWW5LWWPVF6UG`，启动 `markdown_syntax`，等待 8 秒后点「重新流式播放」，再等 2 秒开始固定 10 次慢拖；每轮截图，单独 `dumpsys gfxinfo reset`。
- `gfxinfo`：`/tmp/markdown-release-final/gfxinfo.txt`：841 帧，Janky 12（1.43%），legacy 77（9.16%），P50 10ms、P90 13ms、P95 15ms、P99 19ms，Missed Vsync 0，Slow UI thread 8。
- 截图：`/tmp/markdown-release-final/stream-1.png` 到 `stream-10.png`；最后一帧仍显示 Mermaid 代码块、综合测试段落、引用块与柠檬黄 `==高亮==`，像素检测约 19,108 个 `#FFF44F` 像素。

结论：release 稳态窗口没有 P99 数百毫秒尾帧；此前 P99 主要是 debug 包的 JIT/GC/冷启动/采集开销。按 release/profileable 验收，当前长文流式滚动已可用的证据明显强于 debug 窗口。

最终 Android 复测（`/tmp/markdown-android-final/gfxinfo.txt`）：845 帧，Janky 8（0.95%），P50 10ms、P90 12ms、P95 13ms、P99 16ms，Missed Vsync 0，Slow UI thread 6；截图仍有 20,647 个柠檬黄高亮像素。该窗口是目前 Android 生产验收的最新基线。

## 重新流式播放中慢拖的额外对照

- Debug + Perfetto 同窗口（`/tmp/markdown-perfetto/stream-scroll-live.pftrace`）：429 帧、Janky 22.14%、P99 300ms。`android_frames` 有 8 个 >50ms 长帧，最长 1014ms；最长切片累计为 `deliverInputEvent` 3.97s、`Recomposer:recompose` 2.12s、`Compose:applyChanges` 1.04s、`Background concurrent copying GC` 1.90s、`GcSupressionClassLinker` 1.07s。trace 有 16 个负时间戳丢弃，且同时运行采集器，因此该窗口只用于归因，不作为业务帧率结论。
- Release、无 trace、初始流式播放中慢拖（`/tmp/stream-live-release-initial-gfxinfo.txt`）：833 帧、Janky 0.60%、P95 13ms、P99 16ms、Slow UI 4。
- Release、无 trace、点击「重新流式播放」后立即慢拖（`/tmp/stream-live-release-gfxinfo.txt`）：864 帧、Janky 0.46%、P95 13ms、P99 16ms、Slow UI 3。
- Release、无 trace、修正「重新播放」不再切换 `streaming` 模式且输出间隔从 50ms 调为 80ms 后（`/tmp/stream-replay-release-gfxinfo.txt`）：874 帧、Janky 0.57%、P95 12ms、P99 17ms、Slow UI 4。

结论：用户感知的「重新播放时很卡滑不动」主要来自 Debug 包热启动/二次 effect、JIT、GC 与输入事件投递叠加，而不是 Markdown 输出频率或表格解析本身。Release 稳态下同样连续慢拖没有长帧。输出间隔从 50ms 调到 80ms 只是降低 demo 的发布压力，不是性能结论的必要前提；真正的瓶颈证据是 `deliverInputEvent` 被 UI 重组/GC/采集窗口阻塞。生产性能判断应始终使用 release/profileable 包，不应把 Debug + Perfetto 的 P99 当成业务回归。

## 仍保留的边界

- 这里是固定全语法文档回放，不等同于所有真实 API 输出、所有图片尺寸、所有表格宽度和所有设备上都不卡。
- Perfetto trace 的 `android_frames` 有负时间戳丢弃；因此只用它解释 debug 长任务构成，不用它给 release 帧率背书。
- `High input latency` 在 gfxinfo 中仍高，和 adb `input swipe` 的事件注入节奏有关；不能把它直接解释为用户触屏延迟。
- iOS、JS 与 OHOS 未做相同 release 运行级窗口；OHOS 仍是 KBA parser 兼容层和完整 parse，不能共享 Android 0.7.18 增量解析结论。

## 下一步

1. 保持 release/profileable 固定回放作为生产回归基线，不再用 debug + Perfetto 的数字判断业务 P99。
2. 若业务现场仍出现长帧，用 profileable release 包录 Perfetto，并给 `append()`、`publishSnapshot()`、`RenderMarkdownBlock()`、表格/代码块测量加 app trace section。
3. 多端运行级验证和远端新版本发布按 [PRODUCTION_LONG_MARKDOWN_PLAN.md](PRODUCTION_LONG_MARKDOWN_PLAN.md) 执行。

## 2026-11-17 重组局部失效优化与验证边界

### 优化前固定基线

- 设备：`VSHEWW5LWWPVF6UG`，release 语法 Demo，固定「重新流式播放 + 10 次慢拖」。
- `gfxinfo`：`/tmp/markdown-opt-20261117/baseline-gfxinfo.txt`：547 帧，Janky 10（1.83%），P50 8ms、P90 11ms、P95 14ms、P99 25ms，Missed Vsync 4，Slow UI thread 8。
- logcat final：`screen=26 stableRegion=16 activeTail=17 stableBlocks=384 tailBlocks=18 stableBatches=0`。

### 已落地优化

1. 诊断标签在独立 composable 内刷新，避免每 100ms 更新外层 `MarkdownSyntaxScreen`。
2. 稳定 Demo 的 `UriHandler`、`MarkdownColors`、`MarkdownTypography`、`MarkdownPadding`、`MarkdownDimens`、`MarkdownExtendedSpans` 和 `MarkdownComponents` 身份，避免外层重组重建 `AnnotatorSettings` 与缓存 key。
3. `onSizeChanged` 仅在高度槽位跨过 `0.75 * viewportHeight` 阈值时写回 `heightSlotCount`，避免每次测量都触发 Composition 写回。
4. 引用链接定义变化不再让所有稳定分片跟随全局 `linksRevision` 失效，也不再调用 `renderCache.clear()`。新增按 block 的 link generation：只失效包含变化 label 的稳定分片和 active tail；`LocalMarkdownBlockLinkGeneration` 同时进入 `cachedMarkdownAnnotatedString` 的 `remember` key 与 `MarkdownRenderCache` key，防止复用 stale `AnnotatedString`。
5. `rememberStreamingMarkdownHost` 增加 120ms 手势冷却，减少两段慢拖之间误判 idle 后的锚点补偿/程序滚动。
6. `RecompositionCounters.reset()` 支持每轮重播从 0 计数；新增局部链接失效、暂停后 finish 链接一致性、generation 缓存失效与 counter reset 单测。

### 已完成的验证

- `./gradlew :markdown:testDebugUnitTest :markdown:compileDebugKotlinAndroid`：通过。
- `bash scripts/production-gates.sh`：`PRODUCTION_GATES_OK version=0.4.4 ... tests=61 lint_errors=0`，多端编译、release AAR、lint、本地 Maven 元数据通过。
- `bash scripts/js-regression.sh`：通过。
- `COMPAT_BUILD_TYPE=ohos ./gradlew -c settings.ohos.gradle.kts :markdown:compileKotlinOhosArm64`：通过。
- `SYNTAX_VARIANT=release bash ./build-syntax-demo-2.30.sh`：通过；最终 release APK SHA-256 为 `8d17e66c980bb1631bc2fa2b4b049036fac9cf08c7f2ecbacc9165e4d5bfec58`。

### 未完成的验证边界

优化后真机性能窗口未跑成。设备在卸载旧包后无法完成新包安装：`adb install` 与设备侧 `pm install` 挂起，`pm install` 返回 `Failure [-99]`，`cmd package install` 在 20/45 秒超时；`adb devices` 仍显示设备在线，系统 Activity 停留在 `com.oplus.appdetail/.modelv2.parsing.PackageParsingV2Activity`。因此当前不能声称优化后 Janky/P95/P99 或重组计数已真机复测。需要恢复设备安装验证链路后，使用同一 release APK、同一 10 次慢拖口径补测 `gfxinfo`、`recomposition final`、截图与 FATAL。

### 优化后 Android 模拟器运行级对照

真机安装器阻塞后，使用 `Medium_Phone` / `emulator-5554` 安装同一份最终 release APK，按相同「重新流式播放 + 10 次慢拖」口径完成两轮稳定窗口：

- 第一轮：`/tmp/markdown-opt-20261117/optimized-emulator-gfxinfo-2.txt`，407 帧，Janky 37（9.09%，legacy 0），P50 17ms、P90 19ms、P95 19ms、P99 20ms，Slow UI thread 0。`recomposition final screen=1 stableRegion=25 activeTail=27 stableBlocks=252 tailBlocks=27 stableBatches=0`。
- 最终轮：`/tmp/markdown-opt-20261117/final-emulator-gfxinfo.txt`，407 帧，Janky 35（8.60%，legacy 4 / 0.98%），P50 17ms、P90 19ms、P95 19ms、P99 22ms，Slow UI thread 0。`recomposition final screen=1 stableRegion=25 activeTail=27 stableBlocks=252 tailBlocks=27 stableBatches=0`。
- 最终 APK SHA-256：`0b7e1b30edd5c631680a327c255f6f64a313b211c0f1995a71024a0d28c9cfa9`。

解释：模拟器软件渲染的 `Janky frames` / `Frame deadline missed` 口径与真机 gfxinfo 不能直接同比，且首次冷启动轮曾出现 36.59% Janky，应以后两轮稳态为准。可比的组件内部重组口径显示，外层 `screen` 由真机优化前的 26 降到 1，稳定块累计由 384 降到 252；`stableRegion` / `activeTail` 仍接近 append 次数，说明 stable segments 和 active tail 的 StateFlow 边界还有继续收窄空间，不能据此宣称「稳定块完全不重组」。真机后测仍是生产性能结论的必要补充。

### 2026-11-17 后续细粒度订阅与验证

后续又补了三层实现，并用固定日志区分真实结构变化与状态自扰：

1. `StreamingMarkdownState` 增加 `blockLinkGeneration(blockId): StateFlow<Int>`，每个 block 只订阅自己的 generation；`StableMarkdownHost` 和 `StableRenderSegmentContent` 不再读取整个 `Map<Long, Int>`。
2. `MarkdownRenderCache.Key` 与 `cachedMarkdownAnnotatedString` 的 `remember` key 都带 `linkGeneration`，避免局部失效后复用 stale `AnnotatedString`。
3. 修复 `finish()` 时 final full parse 新增稳定块但 `stableRenderSegments` 未补齐的 parity 问题；新增对应单测。
4. 删除 `StableRenderSegmentContent` 中不必要的 `movableContentOf`，减少稳定分片组合层对象 churn。
5. 诊断日志显示流式回放中 `stable.size` 从 18 持续增长到 240、`segments` 从 0 增长到 48，说明 `stableRegion≈24~25` 主要是真实新增稳定分片，而不是 link Map 或诊断标签造成的假重组。曾尝试按帧逐段追赶发布，但 `stableRegion` 未下降且冷启动轮 Janky 噪声偏高，已回退该层，只保留 final parity 修复。

最终保留版本的验证：

- `bash scripts/production-gates.sh`：`PRODUCTION_GATES_OK version=0.4.4 ... tests=64 lint_errors=0`。
- `bash scripts/js-regression.sh`：通过。
- `COMPAT_BUILD_TYPE=ohos ./gradlew -c settings.ohos.gradle.kts :markdown:compileKotlinOhosArm64`：通过。
- 最终 release APK SHA-256：`587025718d6275ed181d4aaea5dec8e9ed47e60d1dbaf6d20b19cff3630e3f16`。
- 最终模拟器固定窗口 `/tmp/markdown-opt-20261117/final2-emulator-gfxinfo.txt`：323 帧，Janky 114（35.29%，legacy 3 / 0.93%），P50 17ms、P90 19ms、P95 20ms、P99 23ms，Slow UI thread 0。`recomposition final screen=1 stableRegion=24 activeTail=26 stableBlocks=234 tailBlocks=25 stableBatches=0`。

解释：最终模拟器冷启动轮的 `Janky frames` / `Frame deadline missed` 仍受软件渲染与 adb swipe 注入节奏影响；可信的组件内部口径是 `screen=1`、`stableBlocks=234~240`，相对优化前真机 `screen=26`、`stableBlocks=384` 明显降低。`stableRegion≈24`、`activeTail≈27` 是真实新增稳定分片与 active tail 每次发布带来的组合，不是全局缓存清空；继续降低需要改变「每个短块立即冻结并发布」的产品策略，例如把短块延迟合批冻结或把稳定区做成真正窗口化，而不是继续微调缓存 key。

真机 `VSHEWW5LWWPVF6UG` 已唤醒并通过 `继续安装` 进入 OPPO「未验证应用提醒」，勾选风险确认后跳到 `PictureVerifyActivity` 图形滑块安全验证；该步骤需要人工拖动滑块完成，当前 `com.kuikly.kuiklymarkdown` 仍未安装。优化后真机性能窗口仍待完成图形验证后补测。

最终无诊断日志版本再次构建并用模拟器复测：`/tmp/markdown-opt-20261117/final-clean-emulator-gfxinfo.txt` 显示 411 帧，Janky 34（8.27%，legacy 3 / 0.73%），P50 17ms、P90 19ms、P95 20ms、P99 20ms，Slow UI thread 0；`recomposition final screen=1 stableRegion=25 activeTail=27 stableBlocks=240 tailBlocks=27 stableBatches=0`。最终 release APK SHA-256 为 `aea830e498f550bb6ea1ba63e0cd3cd6466b3c554c4e454a85ed243cd5899e0f`。排查用 `StreamingMarkdownDiagnostics` 日志已移除。
