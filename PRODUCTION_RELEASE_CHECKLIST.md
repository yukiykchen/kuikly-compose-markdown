# 生产发布清单

当前仓库内版本：`0.4.4`（OHOS KBA 为 `0.4.4-ohos`）。组件坐标：`com.tencent.kuiklybase:markdown`。

> 当前状态：0.4.2 / 0.4.3 / 0.4.3-ohos 是已发布历史版本；`0.4.4` / `0.4.4-ohos` 已完成公共发布与远端-only consumer 验证。文末历史记录保留，不要混淆不同版本证据。

远端已存在 `0.4.1`，其公共 POM 依赖 `org.jetbrains:markdown:0.7.3`；当前源码依赖 `0.7.18` 且包含流式增量渲染，因此不覆盖 0.4.1，按不可变版本策略发布 0.4.2。

## 仓库内门禁

```bash
bash scripts/production-gates.sh
```

覆盖标准 KMP 多端编译、52 个 JVM/Android 单测、release AAR、lint 0 error、`publishToMavenLocal`、本地 POM/Gradle metadata 和凭据泄漏机械检查。JS 运行测试另外执行 `scripts/js-regression.sh`；模拟器当前路径见 `PRODUCTION_REVIEW.md`。

## 远端发布

目标仓库必须由发布者明确选择，脚本默认只做 dry-run，不会上传；必须同时设置 `ALLOW_REMOTE_PUBLISH=1` 才会执行 `publish`：

```bash
# 公共/外部
ALLOW_REMOTE_PUBLISH=1 scripts/publish-remote.sh public

# 内部
ALLOW_REMOTE_PUBLISH=1 scripts/publish-remote.sh internal
```

公共发布使用 `KUIKLY_OPEN_MAVEN_USERNAME` / `KUIKLY_OPEN_MAVEN_PASSWORD`，上传到 `https://mirrors.tencent.com/repository/maven/kuikly-open/`；内部发布使用 `KUIKLY_INTERNAL_MAVEN_USERNAME` / `KUIKLY_INTERNAL_MAVEN_PASSWORD`，上传到 `https://mirrors.tencent.com/repository/maven/kuikly/`。脚本只检查环境变量是否存在，不打印、落盘或写入仓库凭据。

远端上传后还要验证：

1. 根坐标、Android release、JS/iOS/macOS 目标的 POM 与 module metadata HTTP 200。
2. `maven-metadata.xml` 包含 `0.4.4` 或本次实际发布版本。
3. 公共 POM/module 不含 `-internal` 坐标，不含本地路径、API key 或诊断开关。
4. 接入方用远端坐标 clean build 能解析 `org.jetbrains:markdown:0.7.18`。
5. CI clean checkout 复跑 `scripts/production-gates.sh`。

## 发布后性能回归

- release/profileable 固定语料、等长窗口 A/B；Baseline Profile 未生成，不可声明已覆盖。
- 极端 Markdown 压力：超长未闭合代码块、巨型表格、后置引用定义（已纳入 JVM 单测）。
- 多轮对话、流式中断、横竖屏/进程恢复、网络错误与 SSE 提前关闭。

## 当前边界

本清单已把仓库内可验证的生产准备动作固化。`scripts/publish-remote.sh` 需要显式 public/internal 目标、环境变量凭据和 `ALLOW_REMOTE_PUBLISH=1`；没有这三项时，不应声称远端 Maven 发布完成。

2026-11-13 自动检查中，公共仓库凭据存在但目标版本 `0.4.1` PUT 返回 `HTTP 403 Forbidden`；远端 0.4.1 POM 已存在且依赖旧解析器 0.7.3，因此该 403 与版本不可覆盖一致。按不可变版本策略改为发布 `0.4.2`，并已显式确认执行公共仓库发布。

完成状态（历史版本）：0.4.2 已发布到公共仓库；远端根 POM、Android POM/module/AAR 和 maven-metadata HTTP 200，远端 AAR 与当时本地 release AAR SHA-256 一致。远端-only consumer 工程禁用 `mavenLocal()` 后 `:app:compileDebugKotlin` 成功，依赖树为 `markdown-android:0.4.2 -> org.jetbrains:markdown:0.7.18`。因此当时的发布与接入验证已闭环。

## 0.4.4 公共发布记录（2026-11-16）

- 标准 KMP：`ALLOW_REMOTE_PUBLISH=1 bash scripts/publish-remote.sh public` 成功，日志 `/tmp/markdown-044-public-publish.log`；上传前 `PRODUCTION_GATES_OK version=0.4.4 ... tests=52 lint_errors=0`。
- OHOS KBA：`bash scripts/publish_ohos.sh` 成功，日志 `/tmp/markdown-044-ohos-publish.log`，KBA `compileKotlinOhosArm64`、根/ohos 发布任务均成功。
- 解析仓库 HTTP 200：根 `markdown/0.4.4` POM/module、`markdown-android/0.4.4` AAR、根 `markdown/0.4.4-ohos` POM/module、`markdown-ohosarm64/0.4.4-ohos` klib。`maven-metadata.xml` 包含 `0.4.4` / `0.4.4-ohos`，`latest/release=0.4.4-ohos`。
- 远端标准 AAR SHA-256 `dd9942392827324b85dda28ad0227842590866a4627770d0c769dd717bf02a42`，与本地 `markdown/build/outputs/aar/markdown-release.aar` 一致；远端 OHOS klib SHA-256 `7b1c0128f4916bfc03bd75b33f2bf160d0ab1abcba1a08e31aa9c984e1aea91e`，与本地 `markdown/build/classes/kotlin/ohosArm64/main/klib/markdown.klib` 一致。
- 根 module metadata 版本 `0.4.4`，29 个标准 KMP variants；OHOS module metadata 版本 `0.4.4-ohos`，5 个 variants 且包含 `ohosArm64ApiElements-published`。两份 module 未发现 `-internal` 坐标、API key 或本地路径。
- 严格远端-only consumer：复制 2.30 AI Chat shell 并删除 `mavenLocal()`，使用 `AI_CHAT_LIBRARY_VERSION=0.4.4` 与 `--refresh-dependencies` 后 `:androidApp:assembleRelease` 成功，日志 `/tmp/markdown-044-strict-android.log`；依赖树为 `com.tencent.kuiklybase:markdown-android:0.4.4 -> org.jetbrains:markdown:0.7.18`。Android-only consumer 必须依赖 `markdown-android`，不能把 KMP 根坐标 `markdown` 当 Android AAR 用。

结论：**0.4.4 标准 KMP 与 0.4.4-ohos KBA 产物已完成公共发布、HTTP/哈希/metadata 校验和远端-only Android consumer clean build 验证**。按用户最新范围，本阶段 Android 生产级闭环已完成；iOS/macOS/JS/OHOS 运行级截图作为后续扩展边界，不阻塞 Android 验收。

## 0.4.3 公共发布记录（2026-11-15）

- `ALLOW_REMOTE_PUBLISH=1 bash scripts/publish-remote.sh public` 执行成功，日志 `/tmp/markdown-043-public-publish-20261115.log`；发布目标 `https://mirrors.tencent.com/repository/maven/kuikly-open/`，解析仓库 `https://mirrors.tencent.com/nexus/repository/maven-tencent/`。
- 发布前 0.4.3 根 POM / Android POM / AAR 均为 HTTP 404；发布同步完成后，根 POM、Android POM/module/AAR、JS/iOS/macOS POM 均为 HTTP 200。`maven-metadata.xml` 的 `latest/release=0.4.3`。
- 远端 AAR SHA-256 `3e5ddd35d7bc5f6eb27e4177b1240f2bed9b50218397d5145c06e7d5c413892c`，与本地 `markdown/build/outputs/aar/markdown-release.aar` 一致。
- 根 module metadata 指向 Android、三个 iOS、JS、两个 macOS 目标；Android POM 依赖 Kuikly Compose 2.30.0-2.1.21、markdown 0.7.18、coroutines 1.10.1 和 Kotlin stdlib 2.1.21；未发现 `-internal` 坐标、API key 或本地路径。
- 远端-only consumer 工程禁用 `mavenLocal()`，使用 `--refresh-dependencies` 后 `:app:assembleDebug` 成功，日志 `/tmp/markdown-043-remote-consumer-20261115.log`；依赖树显示 `com.tencent.kuiklybase:markdown-android:0.4.3 -> org.jetbrains:markdown:0.7.18`。
- 上传前门禁通过：`PRODUCTION_GATES_OK version=0.4.3 poms=8 modules=8 tests=35 lint_errors=0`。

结论：**0.4.3 标准 KMP 坐标已完成公共发布与远端接入验证**。Android / Kuikly 2.30 运行级验证见 `PRODUCTION_REVIEW.md`；iOS/macOS 为编译产物验证。OHOS 另有 `0.4.3-ohos` KBA 发布，见下一节。

## 0.4.3-ohos KBA 发布记录（2026-11-16）

- 使用 KBA Kotlin `2.0.21-KBA-010`、OHOS 专用 Kuikly `2.12.0-2.0.21-ohos`、`markdown-parser:0.7.4-ohos` 和 OHOS 特化 coroutines/atomicfu，实际编译 common metadata、`ohos_arm64`、iOS、JS、Android；编译日志 `/tmp/markdown-043-ohos-component-gates.log`。
- `0.4.3-ohos` 已发布到公共仓库，上传日志 `/tmp/markdown-043-ohos-publish-final.log`；根 POM/module、Android POM、`markdown-ohosarm64` POM/module/klib 均 HTTP 200。
- 根 Gradle module metadata 包含 `ohos_arm64` variant，并指向 `markdown-ohosarm64/0.4.3-ohos/markdown-ohosarm64-0.4.3-ohos.module`；native targets 为 `ios_arm64`、`ios_simulator_arm64`、`ios_x64`、`ohos_arm64`。
- `0.4.3-ohos` 不含 macOS variant；macOS 继续使用标准 `0.4.3`。两个坐标不要混淆或放在同一依赖图。
- 由于 KBA parser 是 0.7.4 fork，没有 `0.7.18` 的稳定边界 API，OHOS 版通过兼容层把 active tail 保持到 `finish()` 再做完整 parse。最终解析一致性由同一 final parse 保证，但流式期间稳定块冻结与标准版不完全等价；尚无 HarmonyOS 真机 UI 运行验证。
- `maven-metadata.xml` 的 `latest/release` 当前为 `0.4.3-ohos`，版本列表包含 `0.4.3` 和 `0.4.3-ohos`。

结论：**KBA 编译的 OHOS klib 已作为 `0.4.3-ohos` 发布**；后续仍需 OHOS consumer 编译和 Harmony 真机 UI 回归，才能把 OHOS 标为运行级生产验证。
