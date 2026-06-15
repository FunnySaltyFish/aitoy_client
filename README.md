# AI Toy Bridge Android

当前目标平台为 Android，应用 ID 为 `com.funny.aitoy`，Debug 包为
`com.funny.aitoy.debug`。

## 运行

- 编译：`./gradlew :composeApp:assembleDebug --console=plain --quiet`
- 安装：`adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`
- 日志：`adb logcat -s AiToyBle:D AiToyRelay:D '*:S'`

## 已实现

- BLE 权限、扫描、连接和 GATT 服务发现
- 手动 Service/Write/Notify UUID 与十六进制指令模板
- `{mode}`、`{intensity}` 模板替换
- With Response / Without Response 写入
- 本地低强度测试、立即停止和最长 15 秒自动停止
- 长期 User Token、Relay WebSocket、设备状态上报
- `set` / `stop` / `stop_all` 命令执行结果回传
- 屏幕日志与 `AiToyBle` / `AiToyRelay` Logcat 详细日志
- 复用原项目的 KV、Cache、网络动态拦截器与数据库同步基础

## 实测设备

`ANKNI YWTD` 已完成扫描、连接和 GATT 发现：

- Service：`0000dddd-0000-1000-8000-00805f9b34fb`
- Write：`0000ddd1-0000-1000-8000-00805f9b34fb`
- Notify：`0000ddd2-0000-1000-8000-00805f9b34fb`

设备能建立 GATT 连接。实际动作指令仍需结合设备反馈确认，App 已预置
`NEW_TASK.md` 中的测试模板并保留完整字节日志。

最低强度启动指令与停止指令均已收到 GATT `status=0` 写入回调。

`DSJM` 已完成连接与自动特征发现：

- Service：`0000fffe-0000-1000-8000-00805f9b34fb`
- Write：`0000fe02-0000-1000-8000-00805f9b34fb`

两条测试指令同样收到 `status=0`。第三个高信号未命名设备使用随机地址，
扫描结果明确为 `connectable=false`，当前只能广播，无法建立 GATT 连接。
