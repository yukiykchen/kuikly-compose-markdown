# AI Chat SSE Demo (Kuikly 2.30)

这个 demo 用 Kuikly 2.30 的 `NetworkModule.httpStreamRequest()` 调用 OpenAI-compatible SSE 接口，并把 `choices[0].delta.content` 增量喂给本仓库的 `StreamingMarkdownState`，用于观察 AI 长 Markdown 回答在流式输出与滚动时的表现。

源码位置：

- [AiChatDemoPage.kt](demo/src/aiChat230Main/kotlin/com/tencent/kuiklybase/markdown/AiChatDemoPage.kt)：`@Page("ai_chat")` 对话页。
- [SseEventParser.kt](demo/src/aiChat230Main/kotlin/com/tencent/kuiklybase/markdown/SseEventParser.kt)：SSE 分片拼接、`data:` 行解析、OpenAI delta 提取。
- [build-ai-chat-2.30.sh](build-ai-chat-2.30.sh)：把当前 Markdown 库源码和 AI 对话页挂到 Kotlin 2.1.21 / Kuikly 2.30 兼容工程壳，生成 debug APK。
- [app/src/aiChat230](app/src/aiChat230)：2.30 构建使用的启动 Activity 与网络权限 manifest。

## 配置

在仓库根目录的 `local.properties`（已由 `.gitignore` 忽略）加入：

```properties
aiChatApiKey=<Moonshot API key>
```

默认 API 地址与模型写在构建脚本里：`https://api.moonshot.cn/v1/chat/completions`、`kimi-k2.6`。Key 只进入本机 BuildConfig 和 pageData，不写进 Kotlin 源码。

## 构建

```bash
./build-ai-chat-2.30.sh
```

脚本会在 `/tmp/kuikly-compose-markdown-ai-chat-230-shell` 生成隔离构建副本，使用 `Kuikly 2.30.0-2.1.21`、`Kotlin 2.1.21`、`KSP 2.1.21-2.0.1`，最后把 APK 复制到：

```text
app/build/outputs/apk/debug/app-ai-chat-2.30-debug.apk
```

当前标准库构建已对齐 Kotlin 2.1.21 / Kuikly 2.30。AI 对话源码位于独立 `aiChat230Main` source set，脚本生成专用测试壳。

## 模拟器运行

```bash
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-ai-chat-2.30-debug.apk
adb -s emulator-5556 shell am start -n com.kuikly.kuiklymarkdown/.KuiklyRenderActivity
```

APK 的包名沿用 2.30 工程壳的 `com.kuikly.kuiklymarkdown`，直接启动页为 `ai_chat`。页面底部有输入框与 Send 按钮；默认提示词会要求模型输出包含标题、列表、代码块、表格的较长 Markdown 回答，便于观察流式增量渲染和滚动流畅度。

## 横竖屏切换

Activity 处理常见 `configChanges`，没有锁定竖屏。完成时通过 `SharedPreferencesModule` 保存最近一次回答，重建后恢复为 `Completed (restored)`；诊断关闭时不逐 chunk 写盘，因此流式中被杀进程不承诺恢复尚未完成的文本。当前 demo 在同一页面保留各轮 Markdown 快照并发送真实聊天上下文；持久化恢复目前只恢复最近一次回答，不承诺完整历史或网络连接恢复。

流式时按钮为 Stop，关闭底层 SSE 连接并完成已收到的部分文本；离开页面同样关闭连接。普通发布间隔为 50ms，巨大未闭合块尾部在 16K/64K 字符后使用 100/200ms 间隔。请求超时为 180 秒，网络/DNS 错误保留 Error 状态并恢复 Send。

## 验证与诊断开关

- 当前只用模拟器验证；历史真机结果不作为当前候选版本的验证证据。
- 页面按 `aiChatDiagnostics` pageData 参数决定是否启用诊断。默认 `false`：关闭 `RecompositionProfiler`、高频 KLog、逐 chunk 诊断日志和诊断态重组计数；仍保留页面内容恢复。
- 性能调查时向 pageData 传 `aiChatDiagnostics=true`，即可复现 `publish delta`、`RCProfiler`、`finish parity` 和重组计数日志。生产接入不要在 pageData 中开启该参数。
- 原始 0.4.2 审查及当前修复状态见 [PRODUCTION_REVIEW.md](PRODUCTION_REVIEW.md)。

可重复的验证命令：

```bash
python3 scripts/emulator-smoke.py --serial emulator-5556 \
  --apk app/build/outputs/apk/debug/app-ai-chat-2.30-debug.apk \
  --output /tmp/markdown-emulator-validation --api-rounds 2
```

脚本只接受模拟器，验证五种公共入口，并可选使用本地注入的真实 API key 跑两轮对话。服务超时或 DNS 失败会让测试失败，不会被当作成功样本。诊断数据、截图和日志会保存到指定目录。

验证实际发布产物而不是源码副本：

```bash
bash scripts/production-gates.sh
AI_CHAT_VARIANT=release AI_CHAT_LIBRARY_VERSION=0.4.3 bash build-ai-chat-2.30.sh
python3 scripts/emulator-smoke.py --serial emulator-5556 \
  --apk app/build/outputs/apk/release/app-ai-chat-2.30-release.apk \
  --output /tmp/markdown-release-validation --api-rounds 2 --lifecycle
```

`AI_CHAT_LIBRARY_VERSION` 从 Maven 获取组件；`0.4.3` 已发布到公共仓库，远端-only 接入已验证。没有设置时仍使用源码副本便于开发。测试壳不携带旧 DSL parser/LaTeX 模块，避免重复类干扰。

本地 API key 只用于这个测试 demo，虽不进入 Git，但会存在于测试 APK 的 BuildConfig。不要把该 APK 或客户端内嵌 key 作为生产 AI 服务；生产应通过自己的后端鉴权与代理调用模型。
