# 生产发布清单

当前仓库内版本：`0.4.3`。组件坐标：`com.tencent.kuiklybase:markdown`。

> 当前状态：0.4.2 已发布但审查发现正确性缺口；0.4.3 已作为修复版本发布到公共仓库。文末关于 0.4.2 的发布记录是历史证据；0.4.3 的实际发布证据见文末增量记录。

远端已存在 `0.4.1`，其公共 POM 依赖 `org.jetbrains:markdown:0.7.3`；当前源码依赖 `0.7.18` 且包含流式增量渲染，因此不覆盖 0.4.1，按不可变版本策略发布 0.4.2。

## 仓库内门禁

```bash
bash scripts/production-gates.sh
```

覆盖标准 KMP 多端编译、35 个 JVM/Android 单测、release AAR、lint 0 error、`publishToMavenLocal`、本地 POM/Gradle metadata 和凭据泄漏机械检查。JS 运行测试另外执行 `scripts/js-regression.sh`；模拟器当前路径见 `PRODUCTION_REVIEW.md`。

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
2. `maven-metadata.xml` 包含 `0.4.3` 或本次实际发布版本。
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

## 0.4.3 公共发布记录（2026-11-15）

- `ALLOW_REMOTE_PUBLISH=1 bash scripts/publish-remote.sh public` 执行成功，日志 `/tmp/markdown-043-public-publish-20261115.log`；发布目标 `https://mirrors.tencent.com/repository/maven/kuikly-open/`，解析仓库 `https://mirrors.tencent.com/nexus/repository/maven-tencent/`。
- 发布前 0.4.3 根 POM / Android POM / AAR 均为 HTTP 404；发布同步完成后，根 POM、Android POM/module/AAR、JS/iOS/macOS POM 均为 HTTP 200。`maven-metadata.xml` 的 `latest/release=0.4.3`。
- 远端 AAR SHA-256 `3e5ddd35d7bc5f6eb27e4177b1240f2bed9b50218397d5145c06e7d5c413892c`，与本地 `markdown/build/outputs/aar/markdown-release.aar` 一致。
- 根 module metadata 指向 Android、三个 iOS、JS、两个 macOS 目标；Android POM 依赖 Kuikly Compose 2.30.0-2.1.21、markdown 0.7.18、coroutines 1.10.1 和 Kotlin stdlib 2.1.21；未发现 `-internal` 坐标、API key 或本地路径。
- 远端-only consumer 工程禁用 `mavenLocal()`，使用 `--refresh-dependencies` 后 `:app:assembleDebug` 成功，日志 `/tmp/markdown-043-remote-consumer-20261115.log`；依赖树显示 `com.tencent.kuiklybase:markdown-android:0.4.3 -> org.jetbrains:markdown:0.7.18`。
- 上传前门禁通过：`PRODUCTION_GATES_OK version=0.4.3 poms=8 modules=8 tests=35 lint_errors=0`。

结论：**0.4.3 标准 KMP 坐标已完成公共发布与远端接入验证**。Android / Kuikly 2.30 运行级验证见 `PRODUCTION_REVIEW.md`；iOS/macOS 为编译产物验证，OHOS 不包含在本次发布范围。
