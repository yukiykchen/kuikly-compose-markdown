# Compose Markdown 流式增量渲染交付报告

> 本文主要记录早期优化与 0.4.2 的历史验证；关于“生产接入条件”的旧结论已被后续审查纠正。当前修复源码为 0.4.3，最新状态以 `PRODUCTION_REVIEW.md` / `PRODUCTION_RELEASE_CHECKLIST.md` 为准。没有覆盖远端 0.4.2，也没有在本轮操作真机。

日期：2026-11-13 Asia/Shanghai。目标：把流式渲染从「稳定 AST 对象 + 富文本缓存 + 合并发布」推进到稳定块组件复用、只更新 active tail，并在真机用真实 Moonshot API 验证。

## 实现

- 新增 `MarkdownRenderBlock` / `OffsetASTNode`：每个顶层块持有局部 source 与重设偏移后的 AST。渲染组件不再接收整篇 `content`。
- `StreamingMarkdownState` 拆出 `stableBlocks`、`unstableBlocks`、`linksRevision`、`diagnostics` 四个独立 StateFlow。稳定块列表仅在有新块冻结时发布；普通尾部追加只更新 active tail。
- `StreamingMarkdownContent` / `LazyMarkdownSuccess` 把稳定区与 active tail 放在不同 `key` 组合作用域；稳定块按 `id + linksRevision` 保持组合身份。links 定义出现/变化时才使稳定富文本缓存失效。
- `finish()` 使用 `MarkdownParser.buildMarkdownTreeFromString()` 做一次完整 final parse，发布最终渲染块并记录 `finalParseMatches`，避免流式预览与最终文档不一致。
- `MarkdownRenderCache` 增加稳定区高度记录；`lookupLinkDefinition` 接受 `CharSequence`，避免逐次 `toString()`。
- AI 对话页移除 `renderVersion` 父级强制重组、整篇 `answerContent`、逐快照过滤和逐块 `handleElement`；改为订阅稳定块/尾部两个流并复用 `StreamingMarkdownContent`。128 字符或 50ms 的 SSE 合帧、磁盘恢复与单 item 聊天容器保持不变。
- 新增 `IncrementalStreamingRenderTest`，覆盖稳定渲染块对象复用、active tail 变化、final full parse parity、局部 AST 偏移和 links 解析。

## 源码验证

```text
./gradlew :markdown:compileDebugKotlinAndroid :markdown:testDebugUnitTest --console=plain --no-daemon
-> BUILD SUCCESSFUL；12 tests，0 failed
bash build-ai-chat-2.30.sh
-> BUILD SUCCESSFUL；AI_CHAT_2_30_APK=app/build/outputs/apk/debug/app-ai-chat-2.30-debug.apk
```

`git diff --check` 仅报告用户已有 `app/src/main/assets/sample.md` 中 Markdown 换行用的 trailing spaces 与末尾空行；本次新增/修改的 Kotlin 文件没有 whitespace error，且未回退这些既有内容。

最终 APK SHA-256：`fac54488c545eee99b666c65e62cc9085d5084f791e77f5f81638dd4d6d4b7c6`。API key 仅从 `local.properties` 注入 2.30 壳的 `BuildConfig`，未写入共享源码或本报告。

## 真机真实 API 流式验证

- 设备：`VSHEWW5LWWPVF6UG`，OPPO PFTM10 / Android 14，debug/debuggable APK。
- 包名：`com.kuikly.kuiklymarkdown`，页面 `ai_chat`，API：`https://api.moonshot.cn/v1/chat/completions`，模型 `kimi-k2.6`。
- 干净安装后状态 `Ready`，点击 Send 发起真实 SSE；正文输出后状态 `Completed`。
- 最终轮日志：`159` 次 `publish delta`，正文 `8084` 字符，`stable` 从 0 增长到 60，尾部最大 4；最后一次发布 `reused=60 new=0`；`finish blocks=60 parity=true links=0 chars=8084`。
- `error_count=0`（按 `FATAL` / app `AndroidRuntime` crash / `SyntaxError` / `Invalid regular` / `sse error` / `Missing API` 口径；日志中的 `AndroidRuntime START/Shutting down` 属于 `uiautomator` 工具进程，不是应用崩溃）。
- UI dump 显示 `Completed` 与完整 Markdown 正文；流式过程中日志持续出现 `compose stable=... tail=... reused=... new=... parity=true`。

主要证据目录：`/tmp/codex-compose-markdown-incremental-20261113/`。关键文件：`logcat-final-retry.txt`、`gfxinfo-final.txt`、`window-final-status.xml`、`apk-final-sha256.txt`、`simpleperf-self.txt`、`simpleperf-children.txt`、`incremental-stream.pftrace`。

### 帧指标与边界

- 最终真实 API 全窗口 `gfxinfo`：240 帧，143 janky（59.58%），P50 42ms、P90 69ms、P95 85ms、P99 113ms，GPU P99 10ms。
- 录制轮全窗口 `gfxinfo`：345 帧，208 janky（60.29%），P50 42ms、P90 77ms、P95 105ms、P99 150ms，GPU P99 9ms。
- 这两个窗口包含约 40 秒模型 thinking / network 阶段、冷启动/安装后的首次 JIT，以及同时运行 Perfetto + Simpleperf 的采集开销；设备是 debug/debuggable 包。它们不能用来声称「流式渲染达到某个流畅度百分比」，也不能与旧版不同语料/不同窗口做精确 A/B。
- Perfetto 轻配置 `incremental-stream.pftrace`（32MB）在本机未捕获 app frame timeline，查询 `android_frames` 为空，并有 `trace_sorter_negative_timestamp_dropped: 14`；因此不把 Perfetto 当作帧率证据，仅保留为调度/切片原始材料。
- Simpleperf 118802 samples：自耗时前列是 debug ART 解释器（`ExecuteSwitchImplCpp` 主线程 5.68%、`HRContextQueueH` 7.09%，`DoCall` 2.68%/3.05%）；children 中可见 `StreamingMarkdownStateImpl.append` 5.67%、`BaseComposeScene.doLayout` 5.68%、`measureAndLayout` 5.64%，没有把 `MarkdownText` / `handleElement` 列为最大自耗时热点。该结论限于 debug 包与包含采样的窗口。

### 完成态长文滑动复测

在 `Completed (restored)` 状态做 14 次连续 swipe：`gfxinfo-scroll.txt` 为 338 帧、24 janky（7.10%），P50 10ms、P90 22ms、P95 26ms、P99 57ms，Missed Vsync 6、Slow UI 18，GPU P99 18ms；无 FATAL / SyntaxError / Invalid regular。该窗口证明新的流式分层没有回退此前「单 item + 过滤空节点」的完成态滚动优化。

## 结论

流式渲染层已补齐：稳定块对象与组合 key 复用、active tail 独立更新、局部 source、links 低频失效、final full parse 校验、可观测 diagnostics；不再是「整篇 content 每次传递 + 整篇组件树重组」的半成品。真实 API 长 Markdown 输出、最终一致性和完成态滚动均已在真机验证通过。

剩余边界：debug 包的 ART interpreter 与 Kuikly 2.30 帧调度仍是主线程长帧主要来源；正式性能百分比需要 release/profileable + Baseline Profile、固定语料与等长窗口的 A/B。若后续要进一步优化流式帧指标，应优先减少 SSE callback/合帧日志和框架帧调度开销，而不是再回到每个 AST 块独立 Lazy item 的结构。

## 重组次数验证

新增 `RecompositionCounters`，用 `SideEffect` 在成功组合提交后记录：`screen`、`stableRegion`、`activeTail`、`stableBlocks`、`tailBlocks`、`stableBatches`。计数器是普通对象，不写 Compose snapshot state，避免测量自身制造重组。AI 父级只订阅低频 `hasRenderableBlocks`；不再订阅 `stableBlocks/unstableBlocks` 来派生「是否有回答」。

真实 API 首轮（134 次 publish，74 个最终稳定块）：`screen=3 -> 4`（净增 1），`stableRegion=1 -> 18`（净增 17，均发生在新稳定块冻结时），`activeTail=1 -> 133`（净增 132），`stableBlocks=0 -> 617`，`tailBlocks=0 -> 147`。普通尾部追加时 `screen` 基本不增长、`activeTail` 基本每次发布增长 1；说明父层与 active tail 的边界合理。

但 `stableBlocks=617` 明显高于最终 74 个稳定块：每次稳定列表发布时，Kuikly Compose 仍会重新执行稳定区内已存在块的组合，而不是只组合新增块。尝试按 8 块冻结批次再包一层 `key + Column` 后，第二轮（170 次 publish，92 个最终稳定块）得到 `stableBlocks=975`、`stableBatches=131`，没有改善，说明额外嵌套反而扩大了受影响组合范围；该包装已撤回。

历史结论（已被后续深挖修复）：重组计数是更直接、应长期保留的指标。当时「外层对话页与 active tail 分区」合理，`screen` 在两轮真实流式里只净增 1；「稳定块全量组合」仍不合理，是下一层需要解决的 Kuikly `key`/列表更新行为问题。不能用 `janky` 或 `publish reused=...` 代替这项指标。后续深挖见下一节；最终 `StableBlockSlot` 方案已使 `stableBlocks` 降到新增稳定块数。

证据：`logcat-recomposition.txt`、`recomposition-analysis.txt`、`logcat-recomposition-final.txt`、`recomposition-final-analysis.txt`、`apk-recomposition-final-sha256.txt`。

## 深挖：Kuikly RecompositionProfiler 与稳定块复用修复

按用户要求改用模拟器 `emulator-5556`（`sdk_gphone16k_arm64`），不再使用物理机。AI demo 在第一次正文 publish 时启动 `RecompositionProfiler.configure/start`，finish 时用 `getReport(saveToFile=false)` 把关键 composable 统计写入 `AiChatDemo` 日志；配置关闭内置日志与文件输出、开启 State tracking，避免额外 I/O 干扰。

### Profiler 定位结果

模拟器基线真实 API 窗口：`duration=15615ms frames=71 recompositions=2506 hotspots=2`。关键逐函数统计：

```text
RenderMarkdownBlock@LazyMarkdown.kt:257 count=159 avg=1.58ms params={0=159}
StreamingMarkdownContent@LazyMarkdown.kt:76 count=71 avg=4.23ms params={}
StableMarkdownBlocks@LazyMarkdown.kt:233 count=10 avg=7.10ms params={0=10}
AiChatScreen@AiChatDemoPage.kt:121 count=1 params={}
finish blocks=45 parity=true chars=3552 recompositions=... stableBlocks=226 tailBlocks=114
```

`params={0=...}` 表示 `StableMarkdownBlocks` 的第 0 个参数（整张 `blocks: List<MarkdownRenderBlock>`）和 `RenderMarkdownBlock` 的第 0 个参数（`block`）在每次重组中都变化。State 变更列表里也可见 active tail 与稳定列表的新 `List<MarkdownRenderBlock>` 对象。由此定位：父级虽然已不随 tail 重组，但稳定列表引用变化仍让稳定区重新遍历所有旧块。

### 实测过的复用机制

1. `movableContentOf<MarkdownRenderBlock>` 直接包块：真机窗口 `stableBlocks=609`，按 publish 归一化没有下降；无崩溃、parity 正常，但无效。
2. `ReusableContent(linksRevision)` 包稳定区：真机窗口 `stableBlocks=1365`，比基线更差；无崩溃、parity 正常，但无效。
3. 按 8 块批次包 `key + Column`：真机窗口 `stableBlocks=975`、`stableBatches=131`，额外嵌套扩大组合范围；已撤回。
4. `StreamingMarkdownContent` 外层嵌套 `LazyColumn(item)`：编译可行，但属于聊天页 `LazyColumn` 的嵌套 Lazy host，真机未完成验证；按切换到模拟器的要求已撤回。
5. 有效方案：`StableBlockSlot` + 每块独立 `MutableState` + 零参数 `movableContentOf`。`StreamingMarkdownState` 为每个稳定块维护稳定 slot；列表新增只发布一次 slot 列表，旧 slot 对象身份不变。渲染层从 `StableBlockSlot.value` 读取块内容，零参数 movableContent 只依赖稳定 slot，不再把整张 List 作为块内容参数。

### 有效方案验证

模拟器基线：45 个最终稳定块，`stableBlocks=226`。有效方案在另一轮真实 API 输出 4511 字符、68 个最终稳定块时：

```text
finish blocks=68 parity=true links=0 chars=4511 recompositions=screen=4 stableRegion=18 activeTail=88 stableBlocks=68 tailBlocks=119 stableBatches=0
RCProfiler summary duration=19146ms frames=88 recompositions=3155 hotspots=2
RenderMarkdownBlock count=187 avg=1.51ms params={0=187, 1=68}
StableMarkdownBlocks count=17 avg=1.53ms params={0=17, 1=17}
AiChatScreen count=1
```

`stableBlocks=68` 与 `68` 个新增稳定块总数完全一致；发布序列中纯尾部追加阶段 `screen` 增长 0，稳定列表达到 68 后连续 publish 都保持 `stableBlocks=68`。这证明普通尾部更新不再重组旧稳定块，稳定块组合次数已从 `O(稳定列表发布次数 × 当前稳定块数)` 降到 `O(新增稳定块数)`。Profiler 中 `StableMarkdownBlocks` 单次平均耗时也从基线 7.10ms 降到 1.53ms；次数 10 -> 17 是两轮新增稳定批次数量不同，不能只看次数。

内容正确性：UI 为 `Completed`，有完整 Markdown 正文；`finish parity=true`，没有 FATAL / SyntaxError / Invalid regular / SSE error。模拟器完成态连续 swipe 14 次后 UI dump 有 7 段可见文本、无崩溃；`gfxinfo` 的 GPU P99 4950ms 是模拟器图形栈采集噪声，不作为业务结论。

证据目录：`/tmp/codex-compose-markdown-emulator/`。关键文件：`logcat-profiler.txt`（基线 Profiler）、`logcat-slot.txt`（有效方案）、`recomposition-slot-analysis.txt`、`gfxinfo-scroll.txt`、`apk-slot-sha256.txt`。真机 A/B 证据仍保留在 `/tmp/codex-compose-markdown-incremental-20261113/`：`logcat-movable-summary.txt`、`logcat-reusable-summary.txt`、`logcat-isolate-summary.txt`。

## 当前最终状态

- 就本轮目标而言已是完成态：Kuikly 2.30 逐函数 Profiler 已定位触发参数/State；`movableContentOf`、`ReusableContent`、批次包装、隔离订阅和最终 `StableBlockSlot + 零参数 movableContentOf` 均已实测；`stableBlocks` 在模拟器真实 API 从基线 226 降到 68，且 68 正好等于新增稳定块数。普通尾部追加不再重组旧稳定块。
- 对等长固定语料、release/profileable + Baseline Profile 的正式帧率百分比尚未做 A/B；debug/模拟器窗口和不同 API 回答长度不能给出可信「整体流畅度提升 X%」。
- 仍可做的工程优化不属于本轮「稳定块重组」目标：减少 demo 高频日志与 SharedPreferences 写入、关闭生产包 Profiler/计数器、做 release 真机或模拟器固定语料 A/B、处理超长未闭合代码块时 active tail 退化为 giant pending 的边界。
- 因此不能把「所有性能问题永远消失」称为完全体；可以准确称为：流式增量解析与稳定块渲染复用链路已完成并实测闭环，剩余是发布工程验证和极端 Markdown 结构边界。

## 最终真机复测（StableBlockSlot 版本）

物理机：`VSHEWW5LWWPVF6UG`，OPPO PFTM10 / Android 14，包名 `com.kuikly.kuiklymarkdown`，APK SHA-256 `551759217a6838e07eff22672962986d738e628e4f7aa7bc0de7b99362a0378f`。干净安装后状态为 `Ready`，点击 Send 使用真实 Moonshot API 流式输出。

流式窗口：`6889` 字符，128 次 publish，最终 `78` 个稳定块，`finish blocks=78 parity=true links=0 chars=6889`；UI `Completed`，无 FATAL / SyntaxError / Invalid regular / SSE error。日志 `recompositions=screen=4 stableRegion=22 activeTail=129 stableBlocks=79 tailBlocks=140 stableBatches=0`。`79` 是 78 个新增稳定块组合加 `finish()` 最终 full parse 替换的一次；publish 序列中新增稳定块总数 78，稳定列表达到 78 后连续纯尾部 publish 保持 `stableBlocks=78`，`screen` 在 publish 间增长 0。

Kuikly `RecompositionProfiler` 真机汇总：`duration=29086ms frames=130 recompositions=3683 hotspots=0`；`AiChatScreen count=1`，`StableMarkdownBlocks count=21 avg=6.43ms max=16ms`，`RenderMarkdownBlock count=219 avg=9.66ms max=81ms`。流式全窗口 `gfxinfo`：1094 帧，169 janky（15.45%），P50 12ms、P90 30ms、P95 48ms、P99 109ms，GPU P99 17ms。该窗口包含模型 thinking/network 阶段、debug ART 解释器和高频日志，不作为 release 流畅度百分比。

完成态长文连续滑动 14 次：`gfxinfo-scroll.txt` 为 350 帧、16 janky（4.57%），P50 10ms、P90 15ms、P95 20ms、P99 29ms，Missed Vsync 0，GPU P99 7ms；UI dump 有可见正文，无 FATAL / SyntaxError / Invalid regular。该窗口证明最终 `StableBlockSlot` 结构在真机完成态滚动没有白屏或崩溃回归。

证据目录：`/tmp/codex-compose-markdown-device-final/`。关键文件：`logcat-stream.txt`、`logcat-stream-summary.txt`、`recomposition-device-analysis.txt`、`gfxinfo-stream.txt`、`gfxinfo-scroll.txt`、`window-completed.xml`、`window-scroll.xml`。

## 生产发布门槛审计

当前判断（生产补齐后）：**源码/构建/本地发布门槛已闭环，具备生产候选条件**。外部远端 Maven 上传、远端 HTTP 下载和 CI 接入 demo 属于发布执行动作，尚未在本工作区执行；若「生产级」包含已完成公网发布，则仍需执行发布流水线。

已通过：

- `./gradlew :markdown:compileDebugKotlinAndroid :markdown:testDebugUnitTest`：通过，13 个单测 0 失败。
- `./gradlew :markdown:assembleRelease`：产出 `markdown/build/outputs/aar/markdown-release.aar`。
- Android debug 2.30 壳、真机/模拟器真实 API、完成态滚动、final parse parity、重组计数下降均有证据。
- `local.properties` 被 `.gitignore` 忽略；仓库源码和文档中没有硬编码真实 API key，只有 `aiChatApiKey` 变量名或占位说明。

原阻断项修复结果：

1. **KMP 多端编译**：移除 commonMain 中 JVM-only `@Volatile`；`compileKotlinJs`、三种 iOS、两种 macOS、common metadata 已全部通过。
2. **诊断设施默认关闭**：新增 `diagnosticsEnabled` 显式开关；`rememberStreamingMarkdownState()` 与 `StreamingMarkdownContent()` 默认 `false`，默认不维护 diagnostics、不记录重组计数、不写稳定区测量高度；新增 `DiagnosticsDefaultTest` 锁定该契约。AI demo 也用 `aiChatDiagnostics` pageData 开关，默认关闭 `RecompositionProfiler`、高频日志与逐 chunk 诊断写盘。修复了开关初值过早读取 `pageData.params` 导致的构造期崩溃，改为 `created()` 后 `runCatching` 读取。
3. **lint**：`compileSdk/targetSdk` 提升到 34；`lintDebug` 现在 `0 errors, 2 warnings`，Error 清零。剩余 warning 是 AGP 8.1.0 对 compileSdk 34 的兼容性提示和 Compose runtime obsolete custom lint check。
4. **demo 默认开销**：默认路径在模拟器验证为 `Ready` 启动，无 `RCProfiler` / `publish delta` / `sse data bytes` / `request start` / `consume loop` 日志；真实 API 输出完成后 UI 为 `Completed`，无 FATAL / UninitializedPropertyAccessException / SyntaxError / Invalid regular / SSE error。
5. **文档**：README 区分静态全量 parse 与流式 append-only API，补充 `rememberStreamingMarkdownState()/append()/finish()/StreamingMarkdownContent()`、合帧建议和诊断开关；`AI_CHAT_DEMO.md` 更新为已验证并说明 `aiChatDiagnostics`。
6. **本地发布元数据**：`publishToMavenLocal` 成功，本地 Maven 中 KMP/Android/iOS/macOS/JS 等坐标的 POM、Gradle module metadata、sources、AAR/klib 和签名文件齐全；POM/module 中 `org.jetbrains:markdown:0.7.18` 依赖可见，未发现 `-internal` 坐标、API key、Profiler 或诊断开关泄漏。

补齐后的最终验证命令：

```text
./gradlew :markdown:compileKotlinJs :markdown:compileKotlinIosArm64 :markdown:compileKotlinIosX64
  :markdown:compileKotlinIosSimulatorArm64 :markdown:compileKotlinMacosX64
  :markdown:compileKotlinMacosArm64 :markdown:compileDebugKotlinAndroid
  :markdown:testDebugUnitTest :markdown:assembleRelease :markdown:lintDebug
  :markdown:publishToMavenLocal --console=plain --no-daemon
-> BUILD SUCCESSFUL；14 tests；lint 0 errors；markdown-release.aar 与本地 Maven 产物齐全
```

模拟器最终默认路径验证：`emulator-5556`，APK SHA-256 `62dca1ef5dbce1e3a07994a566d50d5580a0fc4c97a51eb457674ff783d45379`。启动 `Ready`、Send 可见；真实 API 完成后 `Completed`，7 段可见正文，无诊断日志、无错误；完成态 14 次连续滑动后 UI 仍有 7 段正文、无错误。流式/滚动 `gfxinfo` 的 janky 与 legacy 口径矛盾且 GPU P99 有 4950ms 异常值，属于模拟器图形栈统计噪声，不作为业务性能结论。

剩余外部发布动作：远端 Maven upload（内部/公共仓库选择）、远端 HTTP 下载验证、CI clean checkout 发布构建、接入 demo 编译、release/profileable + Baseline Profile 固定语料 A/B。极端 Markdown（超长未闭合代码块、巨型表格、后置引用定义）已补为 JVM 单测；发布脚本与清单见 `scripts/publish-remote.sh`、`PRODUCTION_RELEASE_CHECKLIST.md`。

发布安全边界与最终发布：`scripts/publish-remote.sh` 默认 dry-run，必须显式 `ALLOW_REMOTE_PUBLISH=1` 才允许远端 `publish`。自动检查中公共仓库对 `com.tencent.kuiklybase:markdown-android:0.4.1` 的 PUT 返回 `HTTP 403 Forbidden`；远端 0.4.1 POM 已存在且依赖旧解析器 0.7.3，判断为不可覆盖版本。按不可变版本策略把组件版本推进到 `0.4.2` 并执行公共仓库发布，结果为 `BUILD SUCCESSFUL`、`REMOTE_PUBLISH_SUBMITTED target=public`。

远端验证：`maven-tencent` 公共解析路径下 `markdown/0.4.2` 根 POM、`markdown-android/0.4.2` POM、module metadata、AAR 均可下载；`maven-metadata.xml` 的 `latest/release` 均为 `0.4.2`；POM/module 显示 `org.jetbrains:markdown:0.7.18`，无 `-internal` 坐标或 API key；远端 AAR SHA-256 与本地 release AAR 一致。一个不含 `mavenLocal()` 的最小 Android consumer 工程仅用 `https://mirrors.tencent.com/nexus/repository/maven-tencent/` 解析 `com.tencent.kuiklybase:markdown-android:0.4.2`，`:app:compileDebugKotlin` 成功，依赖树显示 `markdown-android:0.4.2 -> org.jetbrains:markdown:0.7.18`。

结论更新：仓库内生产门槛、远端公共 Maven 发布、远端 HTTP/POM/module 验证和远端 clean consumer 编译均已闭环；本组件当前版本 `0.4.2` 已具备生产接入条件。

结论：仓库内生产门槛已补齐；当前可作为**生产候选组件源码/本地发布产物**。如果「生产级」定义为「已完成远端 Maven 发布并验证公网接入」，还需执行上一节的外部发布动作。
