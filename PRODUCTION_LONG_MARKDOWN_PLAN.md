# Android 生产级长 Markdown / 流式渲染方案与验收清单

用户已明确本阶段先收口 Android。本文档定义当前仓库在 **Android / Kuikly Compose 2.30** 上从「功能可用」推进到「生产级长 Markdown 组件」的口径。所有结论以当前工作树、构建输出、Android 真机运行证据和远端发布证据为准；iOS/JS/OHOS 当前只保留编译与发布产物验证，不纳入本阶段完成条件。早期审查文档保留历史，不作为当前状态。

## 目标

1. 文本高亮：`==highlight==` 使用 Kuikly 2.30+ 原生 RichText span 背景，不使用下划线或自绘 bounding box 冒充。
2. 流式输出：逐 delta 增量接收；滚动和惯性期间不拿 UI commit 与手势竞争；后台继续累积正文；`finish()` 保证最终一致。
3. 长文滚动：流式期间和完成后都不出现持续白屏、表格处回弹、无法滚到后段；一个消息气泡一个外层 item，回答内部不按 Markdown block 做 Lazy。
4. 稳定结构：完整 Markdown 语义解析；稳定区使用非 Lazy 的有界渲染分片；active tail 是唯一高频变化区域；`finish()` 后不切换整棵布局树。
5. 生产门禁：多端编译、JVM/JS 回归、release AAR、lint、POM/module metadata、OHOS KBA 编译、2.30 Demo/AI Chat shell 全部通过。
6. 性能验收：固定正文、固定 delta、固定窗口，记录总帧数、Janky、P90/P95/P99、重组计数、截图白屏时序和 Perfetto 主线程长任务；不能用 Debug 包的单次结果宣称 release 性能百分比。
7. 发布验收：新版本号、README/CHANGELOG/发布清单、远端 Maven 产物 HTTP 可达、Android 远端-only consumer clean build、版本不可覆盖。

## 架构

```text
外层消息 LazyColumn（唯一滚动容器）
├── 用户消息 / 历史回答：稳定 key、冻结 snapshot 或完成态 state
└── 当前回答：一个消息 item
    └── StreamingMarkdownContent（非 Lazy）
        ├── stable render segments：普通 Column + key(segment.id, linksRevision)
        │   └── 短段落按预算合片；代码、表格、图片等重块单独成片
        └── active tail：唯一高频变化区域
```

解析层仍按完整 Markdown 语义进行；`MarkdownRenderSegment` 只定义渲染组合边界，不按字符切分后独立 parse。长代码在渲染器内部按完整行分块；大表格目前作为一个逻辑块渲染，后续如出现列宽/行窗口瓶颈，应做表格专用行窗口，而不是回退到逐块 Lazy。

## 已验证能力

- 文本高亮：`SpanStyle.background` 映射到 Kuikly 2.30+ RichText span `backgroundColor`；默认柠檬黄 `#FFF44F` 与深色文字 `#202124`；`HighlightRenderingTest` 覆盖内联样式、未配对 `=`、数学文本、默认主题与 ExtendedSpans 不吞背景；真机截图检测到柠檬黄像素。
- 滚动期间发布闸门：`pauseUiUpdates()` / `resumeUiUpdates()` 暂停 UI StateFlow commit，source/parser 继续前进；`finish()` 强制最终发布；`StreamingUiCommitGateTest` 覆盖暂停、finish、reset。
- 锚定与 cache window：`rememberStreamingMarkdownHost` 做可见内容锚定、回到底部、空闲合并发布；`rememberStreamingMarkdownListState` 暴露 Kuikly 2.30 `LazyLayoutCacheWindow`。
- 分片与完成态：确定性稳定分片；完成态继续复用 `StreamingMarkdownContent`，不切到另一棵 snapshot 布局。
- 实时高度与滚动边界：全语法 Demo 通过 `onSizeChanged` 获取回答当前高度，并按视口比例追加零高度 `content-height-slot` item，触发 Kuikly `LazyList` 的 item-count contentSize 扩容分支；不调用框架 internal/reflection。真机慢拖从「九、表格」继续滚到「十五、综合测试段落」，无 FATAL。
- 框架修复核对：2.30.0-2.1.21 compose sources jar 中含 Kuikly #1523 的 `semanticsChangePending` 与 #1791 的 `requestDrawFlush` / `MaxDrawFlushPasses`。

## 当前证据边界

- `PRODUCTION_GATES_OK version=0.4.4 ... tests=52 lint_errors=0`，JS 回归、OHOS KBA 编译、2.30 全语法 Demo APK、AI Chat Demo APK 均通过。
- 固定真机回放：逐块 Lazy 曾为 Janky 22.14% / P95 31ms / P99 53ms 且白屏；单 item + 发布闸门为 376 帧 / Janky 11.97% / P90 20ms / P95 25ms / P99 450ms；仅分片退化为 329 帧 / Janky 13.98% / P95 61ms；分片 + `LazyLayoutCacheWindow` + 发布闸门为 396 帧 / Janky 11.11% / P90 20ms / P95 25ms / P99 350ms。
- P99 350ms 已做 Perfetto 归因：debug/采集窗口的长帧由首轮 `Recomposer:recompose`、baseline JIT、GC、输入事件和 Compose applyChanges 叠加；release 稳态固定窗口先为 841 帧、Janky 1.43%、P90 13ms、P95 15ms、P99 19ms，最终复测为 845 帧、Janky 0.95%、P90 12ms、P95 13ms、P99 16ms、Missed Vsync 0，且高亮像素 20,647。详见 `LONG_MARKDOWN_PERF_ANALYSIS.md`。
- 本阶段只承诺 Android 2.30 真机运行级像素验证。独立 iOS 模拟器最小探针曾显示 `==highlight==` 的内层文字能渲染，但截图无柠檬黄背景像素，说明 iOS renderer 尚未确认消费 `SpanStyle.background`；JS/OHOS 同样只做编译验证，均不计入 Android 完成结论。OHOS 仍使用 0.7.4-ohos parser 兼容层，每次 append 是完整 parse，不等价标准 0.7.18 增量解析。
- 0.4.4 / 0.4.4-ohos 已完成公共发布、远端 HTTP/哈希/metadata 验证和远端-only Android consumer clean build；详见 `PRODUCTION_RELEASE_CHECKLIST.md`。当前工作树仍有未提交改动，不能把本地后续改动称为已发布内容。

## 实施顺序

1. 高亮：已完成，保持编译/单测/像素回归。
2. 基础架构：消息级列表、非 Lazy 分片、发布闸门、cache window 已完成并保持固定回放回归。
3. P99 攻坚：已完成 debug Perfetto 归因与 release 稳态对照，确认此前 P99 是 debug/JIT/GC/采集窗口噪声；后续如业务现场仍有长帧，改用 profileable release 并给解析、发布、渲染和测量加 app trace section。
4. 跨端边界：Android release/profileable 已完成；OHOS/JS/iOS 本阶段不作为完成条件，接入方应自行做目标平台截图与滚动验证。
5. 发布：0.4.4 / 0.4.4-ohos 已完成；README、CHANGELOG、发布清单已同步，远端-only Android consumer clean build 已留证。

## 完成判定

只有以下条件同时成立，才能标记目标完成：

- 高亮源码、单测和 Android 真机像素通过；README 明确 API 与 2.30+ 边界。
- 流式期间固定窗口无连续白屏/回弹；P95 有可比 A/B 且不退化；P99 有明确归因并修复到可解释预算，或 release 稳态证据证明 debug P99 是采集/JIT/GC 噪声且产品已采用兜底。
- 完成态结构复用、稳定分片身份、发布闸门不丢字、final parse 一致性均有单测。
- 标准多端编译门禁、JS 回归、OHOS KBA 编译、2.30 Android Demo/AI Chat shell 通过。
- 0.4.4 / 0.4.4-ohos 产物发布、Android 远端接入验证、README/CHANGELOG/发布清单同步完成。

当前状态：**按用户最新范围，Android 文本高亮、长文架构、release 稳态性能、门禁、发布与远端接入验证均已完成；本阶段 Android 目标已达成。iOS/JS/OHOS 为后续跨端扩展边界，不阻塞本阶段完成。**
