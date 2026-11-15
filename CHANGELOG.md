### [0.4.3] 2026-11-15

1. 流式会话 reset/取消隔离、引用表清理及富文本缓存可观察失效。
2. 稳定块局部 source；finish 深层 AST 一致的稳定 slot 不再替换；历史快照渲染独立于 live state。
3. 默认公共 API 可直接渲染；components/回调合法更新生效；长代码完整行分块。
4. 流式合帧不丢字，上游异常可观察，文档与待解析尾部有显式资源预算。
5. 标准构建对齐 Kotlin 2.1.21 / Kuikly 2.30；增加 JVM、JS、模拟器、发布元数据验证。
6. 已发布到公共 Maven，远端 AAR 与本地 release 产物哈希一致；0.4.2 不覆盖。标准版本不提供未经验证的 OHOS 坐标。

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
