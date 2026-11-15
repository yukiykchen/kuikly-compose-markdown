# Compose DSL Markdown 生产级审查

日期：2026-11-13。对象：当前工作树及已发布 `0.4.2`。本轮只审查、增加隔离验证与本文档，没有修改组件实现，也没有重新发布 Maven。

> 以下原始审查记录保留作 0.4.2 证据。后续用户授权修复后，源码已推进到 0.4.3；修复状态、最新测试与剩余边界见文末“0.4.3 修复验证”。原始 0.4.2 远端包没有被覆盖。

## 结论

**当前不能无条件作为生产级通用 Markdown 组件交付。** 已实现真正的流式块级增量解析、稳定块对象复用及 active tail 分区；完整包装的 Android AI demo 在模拟器可正常完成真实 SSE 和长文滑动。但公开入口、流式会话隔离、引用链接及缓存失效存在问题，其中三个已通过隔离测试复现。编译、发布成功和普通 demo 通过不能覆盖这些问题。

优先处理 R1-R4，再修正接入说明并补齐极端输入、取消、完成阶段与跨端运行测试。不要通过退回全文解析或关闭链接功能来绕过正确性问题。

## Findings

### R1 / P1：推荐流式入口缺少必需的渲染环境

- [README.md](/Users/yukiykchen/project/kuikly-compose-markdown/README.md:150) 推荐直接调用 `StreamingMarkdownContent(state)`。
- [LazyMarkdown.kt](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/compose/LazyMarkdown.kt:81) 没有提供颜色、Typography、Padding、Dimens、ReferenceLinkHandler、ImageTransformer、Animations 等 CompositionLocal，默认块组件却会访问它们。
- [ComposeLocal.kt](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/compose/ComposeLocal.kt:50) 的缺省值是 `error("No local MarkdownColors")` 等异常，不是默认主题。
- AI demo 自己在 [AiChatDemoPage.kt](/Users/yukiykchen/project/kuikly-compose-markdown/demo/src/aiChat230Main/kotlin/com/tencent/kuiklybase/markdown/AiChatDemoPage.kt:424) 提供了完整环境，因而掩盖了公开示例的问题。
- 修复：提供包含默认样式及每个 state 专属链接处理器、缓存的完整公共流式入口；或明确把该函数定义为低层 success slot，示例必须使用完整包装。用外部最小接入页面验证标题、段落、列表、链接、图片等首次渲染。
- 证据级别：源码可直接确认调用路径；本轮模拟器验证的是完整 demo，不是这个裸入口，不声称已录到其崩溃。

### R2 / P1：reset 与后台 append/finish 不互斥，旧会话可以重新发布

- [StreamingMarkdownState.kt](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/model/StreamingMarkdownState.kt:153) 的 `append()` 在 Default dispatcher 和 mutex 下工作，而 [reset()](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/model/StreamingMarkdownState.kt:178) 同步修改同一批 source、parser、列表、缓存，不持有 mutex，也没有会话代次检查。
- 可复现交错：旧 append 完成解析、进入链接发布时暂时阻塞；调用 reset；释放旧 append；旧 AST 又出现在 snapshot，而 source 已为空。
- 隔离测试 `resetMustPreventOldAppendFromPublishingAgain` 失败：`old AST republished after reset`。
- 修复：将 append、finish、reset 交给同一个串行 owner；或统一互斥并在发布前校验 session generation。给 parser 接入实际 cancellation token，取消后不得发布旧任务结果。不要只给 reset 加一次清空或靠 UI 禁用按钮规避。
- 回归：取消并重新生成、Flow 更换、页面销毁、reset 与 append/finish 的受控交错，以及 source/AST/slots 属于同一会话的原子快照。

### R3 / P2：reset 没清除引用处理器，下一篇文档可引用旧回答链接

- 同一个 [reset()](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/model/StreamingMarkdownState.kt:178) 清空 `links` / `stableLinks`，但 `referenceLinkHandler` 保留原实例、原数据。
- [ReferenceLinkHandlerImpl](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/model/ReferenceLinkHandler.kt:19) 的 map 没有 clear/replace 契约。
- 实测：第一篇含 `[ref]: https://old.example.com`；reset 后第二篇只有 `[ref]`。第二篇 `state.links` 为空，但 `handler.find("[ref]")` 仍返回旧 URL。
- 隔离测试 `resetMustDiscardOldReferenceDefinitions` 失败：预期空字符串，实际 `https://old.example.com`。
- 修复：将定义表按文档会话管理；reset 必须替换/清空有效引用表，并明确自定义 handler 的生命周期。补重复 label、后置定义、多轮复用和大小写/空白归一化测试。

### R4 / P2：清空富文本缓存不能使 composable remember 失效

- [cachedMarkdownAnnotatedString()](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/annotator/AnnotatedStringKtx.kt:150) 的 remember key 是 source、offset、style、settings 和 cache 实例，不包含链接版本、缓存版本或 AST 身份。
- 链接变化时 state 只是 `renderCache.clear()`。即使叶组件重新执行，remember 仍可直接返回旧 AnnotatedString，不再次调用 cache。
- [LazyMarkdownSuccess 的多 item 模式](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/compose/LazyMarkdown.kt:182) 只按 block.id 保持身份，不订阅 linksRevision；不能依靠稳定区域的 `key(id, linksRevision)` 修复这个入口。
- 使用真实 Compose runtime 的隔离测试确认：先渲染未定义的 `[ref]`，后补定义并清空 cache，再触发重组。直接重新构造得到 `ref`，组件仍输出 `[ref]`；测试确认发生了至少两次 composition，排除没有执行重组的假阳性。
- 修复：把引用版本/缓存 generation 和必要 AST 身份纳入富文本构造的可观察输入，让所有入口遵守相同失效契约。补晚到引用定义、同 source 不同 AST、final parse 修正及主题/自定义组件更新测试。

### R5 / P1：README 发布仓库与 OHOS 坐标无法按说明接入

- [README.md](/Users/yukiykchen/project/kuikly-compose-markdown/README.md:49) 配置 `maven-public`，而本次实际可下载 `0.4.2` 的路径是 `maven-tencent`。
- HTTP 实测：`maven-public/.../markdown/0.4.2/markdown-0.4.2.pom` 为 404；相同路径的 `maven-tencent` 为 200。
- [README.md](/Users/yukiykchen/project/kuikly-compose-markdown/README.md:62) 声明 `0.4.2-ohos`；该根 POM 在以上两个仓库均为 404。
- [markdown/build.ohos.gradle.kts](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/build.ohos.gradle.kts:13) 仍为 `1.0.3-SNAPSHOT`，标准发布 build 不含 OHOS target；生产门禁也没有执行 OHOS 编译/发布验证。
- 修复：按真实可下载地址更新接入说明；明确标准 KMP 与 OHOS 的版本/目标区别，未发布验证的坐标不能标为可用。必须用文档原样的远端-only consumer 验证，不用 mavenLocal 兜底。

### R6 / P2：长文流式更新仍复制完整前缀，巨大 active tail 也未设边界

- [publishRenderBlocks()](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/model/StreamingMarkdownState.kt:236) 的 lazy `document = source.toString()`，在存在 unstableTail 的普通 append 中仍会被求值；即使渲染块最终只截取局部 source，先前完整前缀已复制。
- 固定 chunk 长度 C 时，这条全文复制路径的累计字符工作量仍近似 `O(N²/C)`；这是源码复杂度，不是本轮测出的耗时百分比，也不是说稳定 AST 被重新解析。
- 上游 `0.7.18` StreamingMarkdownFile 会对整个 unstableTail 重新解析；未闭合大代码块/巨型表格/大段落可让 active tail 持续变大。当前创建的是 NonCancellable parser，组件没有输入/块大小预算或超长块渲染策略。
- 修复：直接基于 CharSequence 的局部范围构造 render source，避免普通 append 的整篇 toString；对大尾部采用有边界的发布与渲染策略，同时维持最终完整解析正确性。用固定语料、多输入规模、解析/组合/布局耗时和取消延迟验证，不仅检查 parse 是否成功。

## 其他确定的 API/观测问题

- [LazyMarkdownSuccess(snapshot)](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/compose/LazyMarkdown.kt:60) 只对参数做永真的 size 检查，实际完全忽略 snapshot，渲染 live state。应实现快照契约，或删除/弃用该误导重载。
- [StreamingMarkdownBlocks](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/compose/LazyMarkdown.kt:152) 用无 key 的 `remember { components }` 固定第一次组件配置；后续合法的 components 更新不会生效。
- [finish 的 slot 替换](/Users/yukiykchen/project/kuikly-compose-markdown/markdown/src/commonMain/kotlin/com/tencent/kuiklybase/markdown/model/StreamingMarkdownState.kt:281) 对所有 final block 创建新对象并写入 neverEqualPolicy slot。探针中此前稳定 120 个节点，finish 替换了全部 120 个。保持 final parse 是正确的，但可在深层 AST/内容相同的块上保留 slot value，减少完成瞬间的失效。
- demo 紧接 finish 就读取重组计数、结束 Profiler，没有等待最终 composition/layout 提交；原报告中的完成阶段计数不能证明 finish 只更新一个块。要把测量终点设在最终 frame 提交后。
- 默认诊断关闭并没有关闭 [SseEventParser 的日志](/Users/yukiykchen/project/kuikly-compose-markdown/demo/src/aiChat230Main/kotlin/com/tencent/kuiklybase/markdown/SseEventParser.kt:45)：本轮真实输出仍有 77 条 parser 日志。该问题属于 demo，不能外推为库默认日志开销。
- demo 每次发送都清空上一条回答，API body 只发送 system + 最新 user；本轮成功不是多轮历史 Markdown 保留/性能的证明。

## 本轮验证

### 构建与测试

- `bash scripts/production-gates.sh`：成功，0.4.2、多端编译、release AAR、lint 0 error 和本地发布检查通过。
- 仓库原有 Android/JVM 单测：17 个全部通过；审查探针不加入仓库 source set，只通过临时 init script 注入。
- 四个隔离探针：三个正确性断言失败；一个 finish 替换范围探针通过。结果保存于 `/tmp/markdown-production-review-20261113/probe-results-final.xml`。
- 复现命令：`./gradlew -I /tmp/markdown-production-review-20261113/review.init.gradle :markdown:testDebugUnitTest --tests '*ProductionReviewProbeTest*' --console=plain --no-daemon`。init 仅注入临时测试路径并允许 Android Trace stub 默认返回，不改变生产代码。
- 现有极端语料测试比较顶层 type/offset，部分断言将相同文本完整解析两次；没有帧时间、内联 AST 深度一致性、链接行为、取消、真实 UI 或大尾部资源预算断言。不能当作这些场景已达到生产性能的证据。

### 模拟器真实 API

- 仅使用 `emulator-5556`，`sdk_gphone16k_arm64`，Android 17；没有连接或操作物理机。
- 重新执行 `bash build-ai-chat-2.30.sh` 并安装 debug APK；SHA-256 为 `62dca1ef5dbce1e3a07994a566d50d5580a0fc4c97a51eb457674ff783d45379`。
- 清空专用 demo 数据后，启动 Ready，使用本地注入的真实 Moonshot key，模型 `kimi-k2.6`，SSE 共解析 1842 条 data 对象、6771 个正文字符，最终 Completed，UI 有 Markdown 标题和正文。
- 14 次连续滑动，检查过程截图及最终 UI；未观察到应用 FATAL、ANR、SyntaxError、Invalid regular 或 SSE error。截图不是逐帧录屏，不能证明任意输入/速度永远不白屏。
- 流式整窗口 gfxinfo：370 帧，新版 janky 226/370=61.08%，legacy 4/370=1.08%，P95 19ms、P99 31ms。
- 完成态 14 次滑动：375 帧，新版 janky 124/375=33.07%，legacy 0/375，P95 19ms、P99 20ms，Slow UI thread=0。
- 两套 janky 口径明显不同，且是单轮模拟器 debug 包；不挑选较好口径，不声称 release 达标或性能改善百分比。
- 完成态截图、日志、帧统计、内存快照均在 `/tmp/markdown-production-review-20261113/`。一次 PSS 121060KB 只能作快照，不证明泄漏或多轮内存稳定。

### Perfetto 边界

- 正确录制范围为完成态 14 次滑动：`scroll.pftrace`，18 秒；录制与手势在同一脚本中串行启动，使用官方 record_android_trace helper。
- `streaming.pftrace` 的 UI 探测因 streaming 期间 uiautomator 无法获取 idle，实际上到 Completed 后才启动；该文件不能作为流式解析/渲染证据。
- scroll trace 实际有 410 条 frame timeline 记录，其中 app 143 条：140 条 `Buffer Stuffing`（severity None）、1 条 `Non Animating`、2 条 `Prediction Error`（Full/Partial 各一）。其余是 SurfaceFlinger；不能把系统与 app 混为同一个分母。该录制窗未见 app 的 `App Deadline Missed`，不将 Buffer Stuffing 直接解释为 Markdown 解析 CPU 卡顿，也不外推到 release 或物理设备。
- app 切片中 Choreographer#doFrame 1074 次，最大 8.53ms、平均 0.70ms；Drawing 144 次，最大 19.45ms；eglSwapBuffersWithDamageKHR 最大 17.82ms，waitForBufferRelease 最大 15.57ms。可见调度/绘制等待，但没有 Compose 重组或 Markdown 解析函数采样，不能据此给 Markdown 热点占比。
- 验证 SQL 在 `/tmp/markdown-production-review-20261113/scroll-queries.sql`，结果在 `trace-summary-full.txt`；使用 slices.with_context 过滤 app process，没有混入系统/UIAutomator 的切片。

## 发布前应补齐的验证

1. 外部最小接入按 README 原样编译、启动，覆盖静态与流式公共 API。
2. R2/R3/R4 回归探针进入正式测试；不靠删断言或弱化契约转绿。
3. 深层 AST/可见文本/链接目标的随机 chunk 与 final parse 一致性，涵盖 CRLF、Unicode、晚到定义、表格、列表、代码块。
4. 取消/重生成、网络失败、SSE 提前结束、页面销毁、主题/宽度改变与恢复。
5. 保留每条历史 Markdown 的真实多轮 demo，记录内存与完成态/流式交互，而不是每轮清空旧回答。
6. 模拟器上 release/profileable 固定输入、等长输出/滑动窗口、重复采样；记录尾部大小、parse 时间、最终 frame 后重组数、布局耗时和分母。
7. JS 与 OHOS 运行级回归，尤其历史 JS Unicode regex 异常。当前多端 compile 不能代替动态 JS/OHOS runtime 测试。

总体判断：优化方向有效且已经有可用演示，但本轮已复现的正确性问题和公开接入缺口需要修复后再评估生产交付，不能继续沿用此前“门禁绿即生产就绪”的结论。

## 0.4.3 修复验证

本节为后续修复的当前记录；原始 0.4.2 结论仍为不建议新生产接入。0.4.3 已于 2026-11-15 完成公共发布，发布与远端接入证据见 `PRODUCTION_RELEASE_CHECKLIST.md` 的“0.4.3 公共发布记录”。

| 审查项 | 修复 | 当前证据 |
| --- | --- | --- |
| R1 公共入口 | 默认主题完整可用，公共流式入口提供文档独立 handler/cache/components；静态 API 也有默认主题 | 模拟器 live/lazy/wrapped/snapshot 五入口测试通过；外部 consumer 实际调用 public composable 编译打包通过 |
| R2 会话竞态 | reset 切换独立 Session；排队任务捕获原会话；解析期间及提交前检查会话和 Job；新会话不等待旧 parser | `StreamingSessionRegressionTest` 可控阻塞 lexer + reset 回归通过；旧 worker 被取消且不能覆盖新会话 |
| R3 引用污染 | handler 必须有 clear 契约，默认 map 带锁；定义按会话隔离；规范化大小写/空白，首个定义优先 | 多轮复用、重复定义、大小写/tab 和 URL entities/brackets 回归通过 |
| R4 富文本失效 | cache generation 为可观察 snapshot state；remember key 包含节点身份和 generation；所有入口共享契约 | 真实 Compose runtime 测试不用父级 state 写入，补充定义后富文本正确变化 |
| R5 发布说明 | 改为实际解析仓库，移除虚假的 0.4.2-ohos 坐标，明确 standard 与旧 OHOS 发行版区别 | 0.4.3 根 POM / Android POM/module/AAR 及平台 POM 均 HTTP 200，`latest/release=0.4.3`，远端-only consumer 编译通过 |
| R6 完整前缀复制/大尾部 | 局部 CharSequence 截取；Flow 有限通道合帧且不丢字；demo 大尾部动态间隔；文档/尾部显式预算；长代码按完整行分块 | 禁止整个 CharSequence.toString 的探针通过；随机 chunk、丢字、上游错误、取消、预算、代码行保真等测试通过 |
| finish 全块失效 | final full parse 保留；深层 AST 与局部内容相同的块保留原 slot value | 稳定 slot identity 测试通过；模拟器 fixture 最终提交之后 `stableCompositions=242 finalBlocks=242` |
| snapshot 重载/配置更新 | captured snapshot 自带局部 source 和引用定义；不再读 live state；去掉冻结初次 components 的 remember | 重置后历史 snapshot 文档仍显示 Captured 而不是 Replacement；主题/回调的必要输入纳入 remember |
| demo 多轮/生命周期 | 每条回答独立 snapshot，API body 带历史；Stop/dispose 关闭 SSE handle；缺 key/网络错误不再进入死循环；pending buffer 加锁；默认日志关闭 | 两轮真实 API 成功记录保留；新版最终包还需对应运行记录，见下方更新 |

构建对齐为 Kotlin 2.1.21 / Kuikly Compose 2.30 / AGP 8.6.1 / Gradle 8.7。对齐前 JS 测试出现 JsMainDispatcher ClassCastException，证明仅 compile 不足；对齐后 Node 20 的真实 jsNodeTest 成功。公开的 Compose 和 coroutine 类型通过 api 依赖传递，不再依赖 unsupported native/JS compileOnly。不承诺旧 1.9.22/Kuikly 2.12 ABI 兼容。

最新门禁 `/tmp/markdown-043-certified-gates.log`：`PRODUCTION_GATES_OK version=0.4.3 poms=8 modules=8 tests=35 lint_errors=0`。JS 脚本可复现运行结果在 `/tmp/markdown-043-certified-js.log`。CI workflow 已添加，但本地通过不等于 GitHub CI 已执行。

追加收集器代次测试，确认旧 Flow 在外部 reset 后不能向新 Session 注入文本；稳定 AST 列表在纯尾部更新时保留，快照 render list 采用不可变前缀的只读拼接视图，避免逐 delta 复制稳定块列表。新增快照保真测试锁定后续块冻结不会修改此前捕获的快照。

网络验证边界：模拟器曾出现真实 request timeout 和 DNS 失败，测试均正确报失败而不是计为成功。为隔离模拟器 DNS，临时对 `api.moonshot.cn:443` 建立了只允许该目的地址的 CONNECT 通道；TLS 仍由 Android 与真实 Moonshot 端到端握手，没有伪造响应或解密 key。历史修复包两轮完成时间 53.43s/53.70s，记录在 `/tmp/markdown-043-final-tls-api/`。验证结束必须还原模拟器代理并关闭临时服务。

资源契约：默认文档最多 1,000,000 字符、待重解析尾部最多 262,144 字符；明确异常保留最后发布画面，宿主可调整限额。不能声称无限长、无限嵌套、大单行代码或任何设备永远不卡。上游 parser 仍重解析整个 active tail；合帧、预算和渲染分块并不把它变成 token 级 O(N) parser。

仍需分别报告：最终 AAR 接入的 release APK、公用入口及大代码 A/B、真实 API/Stop/旋转/恢复验证；远端发布已于 2026-11-15 闭环。不能继续引用旧 debug 性能数作为当前 release 的生产指标。

### 最终本地交付

**判断：标准 Android / Kuikly 2.30 组件在声明的资源预算与 API 契约内，已达到生产接入门槛。** 原始 0.4.2 仍不建议新接入；修复产物 0.4.3 已发布到公共 Maven，未覆盖旧包。这个判断不等于所有平台/UI/宿主、所有输入或任何设备无限规模保证，也不把测试 demo 当作生产 AI 服务。

- 最终 AAR SHA-256：`3e5ddd35d7bc5f6eb27e4177b1240f2bed9b50218397d5145c06e7d5c413892c`，与本地 Maven `markdown-android:0.4.3` 一致。
- 最终测试 APK SHA-256：`714ea8e05fc69fc61d463c05c31ff04026ad6e9cece84b1853debdc850d0d974`，文件为 `app/build/outputs/apk/release/app-ai-chat-2.30-release.apk`。已安装包没有 DEBUGGABLE 标志；该 demo 用测试签名、未混淆，不冒充线上业务包。
- 独立最小接入工程实际调用 public composable，debug assemble 成功；release 开启 `isMinifyEnabled=true`、保留探针入口后 `:app:minifyReleaseWithR8` 和 `:app:assembleRelease` 成功，记录在 `/tmp/markdown-043-r8-consumer.log`。这是 R8 编译/打包证明，不是假装混淆宿主的完整运行验证。
- APK 实际通过 Maven 坐标依赖最终 AAR，`ComposeMarkdown/src/commonMain` 没有库源码副本。测试壳原有 DSL/LaTeX/parser fork 已从依赖图移除；出现过的 duplicate ExperimentalApi / LaTeX 绑定构建失败是测试壳依赖混入，不能忽略或当成功包。
- 五种入口在 release 包通过：直接 StreamingMarkdownContent、逐块 LazyMarkdownSuccess、完整 Markdown 包装、独立 snapshot、稳定块复用。入口完整验证在 `/tmp/markdown-043-release-final-smoke/`；最新 AAR 补验稳定复用与大代码在 `/tmp/markdown-043-delivery-runtime/`。
- 最新 AAR 稳定 fixture：`stableCompositions=242 finalBlocks=242`，记录在 `reuse.log`。
- 最新 AAR 真实 Moonshot/Kimi 两轮：6,221 / 9,180 字符，126 / 178 次正文 publish，97 / 93 个最终块，均 `parity=true`；成功窗口耗时 46.89s / 62.96s 包括模型 thinking 与网络，不是 parser CPU 时间。
- 第一轮稳定块组合计数 97，第二轮累计 190，因此第二轮净增 `190-97=93`，恰好等于新增最终块数；计数跨同一 state 的 reset 累计，不能误读为第二轮额外组合 190 次。日志中的纯尾部阶段保持旧稳定块计数不变。
- 新 APK 的两轮对话、Stop、横转竖、force-stop 后最近回答恢复通过；`results.json` 有 lifecycle passed，app 日志未见 FATAL EXCEPTION、Invalid regular、SyntaxError 或 SSE error。流式日志证明是实际多段 data/publish，不是全文一次显示。
- 默认诊断关闭的独立 release 验证两轮 78.17s / 75.73s；没有高频 profiler/publish/parser 日志。诊断测试另开参数，不能混入默认路径性能结论。
- 大代码 A/B：同一 AAR、同一 2,500 行代码、512 字符/50ms 流式输入，仅切换 code chunk size；baseline 流式 140 帧 P95/P99=21/22ms，分块 140 帧=21/23ms；完成态滑动 baseline 146 帧 P95/P99=20/23ms，分块 145 帧=20/34ms。两者新版流式 janky 均 71/140，legacy 均 0；这是一对模拟器样本，**没有证明分块提升帧率**，不宣称性能提升百分比。分块的确定收益是单个 Text 的多行输入粒度有界，单条超长行仍不强拆；新完成态 P99 不优于基线，不能隐藏该数据。
- 所有运行验证只使用 `emulator-5556`。为排除模拟器 DNS，真实 API 使用临时目标限定的 CONNECT TLS 通道；未 mock 响应或解密 key。验证后已还原 http_proxy、移除 adb reverse、关闭服务。

范围边界：iOS/macOS 为构建通过，JS 为 parser/state Node 运行测试通过；没有宣称 iOS/macOS 界面实测或 OHOS 动态运行通过。GitHub CI 已提供脚本但尚未远端执行。Baseline Profile、无限规模文档、完整历史的进程恢复不在当前已验证承诺内；生产宿主仍须执行自身 UI/设备/安全回归。

复现：

```bash
bash scripts/production-gates.sh
bash scripts/js-regression.sh # Node 20/22 + Yarn 1.x，或显式设置两项 binary 环境变量
AI_CHAT_VARIANT=release AI_CHAT_LIBRARY_VERSION=0.4.3 bash build-ai-chat-2.30.sh
python3 scripts/emulator-smoke.py --serial emulator-5556 \
  --apk app/build/outputs/apk/release/app-ai-chat-2.30-release.apk \
  --output /tmp/markdown-release-validation --api-rounds 2 --lifecycle
```
