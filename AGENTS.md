## 读取顺序

- 如果未读过父目录 `AGENTS.md`，先读父目录规则。
- 涉及 BLE 协议、设备识别、控制无响应时，先读根目录 `docs/protocol_support.md`，再读对应 `docs/protocols/*.md`。
- 涉及会员、额度、付费体验时，先读根目录 `docs/pricing_and_usage_analysis.md`；需要复算 Trace 使用量时看 `server/scripts/analyze_trace_usage.py`。

## 模块边界

- `composeApp` 是 Android App 主模块，业务页面、VM、Relay、BLE 协议优先放在 `commonMain`。
- `androidMain` 只放 Android 必需实现：`MainActivity`、`AiToyApplication`、前台服务、系统 Intent、安装更新、Crash/Trace 上传、Android BLE GATT/广播、Android SDK 接入。
- `core` 放项目通用能力：导航、日志、Toast、缓存、偏好存储、平台工具和可复用 UI/工具。
- `network` 放 Retrofit、OkHttp、API Service、请求封装和接口模型。
- `database` 放 Room/SQLite、DAO、Repository 和表同步相关能力。

## 关键路径

- App 入口与主界面：`composeApp/src/commonMain/kotlin/com/funny/aitoy/App.kt`
- 首页支持面板：`composeApp/src/commonMain/kotlin/com/funny/aitoy/HomeSupportPanels.kt`
- 账号页：`composeApp/src/commonMain/kotlin/com/funny/aitoy/AccountScreen.kt`
- 桥接业务 VM：`composeApp/src/commonMain/kotlin/com/funny/aitoy/BridgeViewModel.kt`
- 页面与设备模型：`composeApp/src/commonMain/kotlin/com/funny/aitoy/model/`
- 按设备聚合的运行态：`composeApp/src/commonMain/kotlin/com/funny/aitoy/runtime/DeviceRuntimeStore.kt`
- 平台能力 expect：`composeApp/src/commonMain/kotlin/com/funny/aitoy/BridgePlatform.kt`
- 平台能力 Android actual：`composeApp/src/androidMain/kotlin/com/funny/aitoy/BridgePlatform.android.kt`
- Android Activity：`composeApp/src/androidMain/kotlin/com/funny/aitoy/MainActivity.kt`
- Android Application：`composeApp/src/androidMain/kotlin/com/funny/aitoy/AiToyApplication.kt`
- 前台服务：`composeApp/src/androidMain/kotlin/com/funny/aitoy/AiToyForegroundService.kt`
- 更新安装：`composeApp/src/androidMain/kotlin/com/funny/aitoy/update/AppUpdateInstaller.kt`

## BLE 与协议

- BLE 抽象：`composeApp/src/commonMain/kotlin/com/funny/aitoy/ble/BleController.kt`
- BLE 数据模型：`composeApp/src/commonMain/kotlin/com/funny/aitoy/ble/BleModels.kt`
- Android BLE 工厂：`composeApp/src/androidMain/kotlin/com/funny/aitoy/ble/BleControllerFactory.android.kt`
- Android GATT 控制：`composeApp/src/androidMain/kotlin/com/funny/aitoy/ble/AndroidBleController.kt`
- Android 广播控制：`composeApp/src/androidMain/kotlin/com/funny/aitoy/ble/AndroidBleAdvertiser.kt`
- 内置 GATT 协议索引：`composeApp/src/commonMain/kotlin/com/funny/aitoy/ble/DeviceProtocols.kt`
- 广播协议索引：`composeApp/src/commonMain/kotlin/com/funny/aitoy/ble/BroadcastProtocols.kt`
- 分协议实现：`composeApp/src/commonMain/kotlin/com/funny/aitoy/ble/protocols/`
- Buttplug 注册表与协议计划：`composeApp/src/commonMain/kotlin/com/funny/aitoy/buttplug/`
- Android 外部 Buttplug 客户端：`composeApp/src/androidMain/kotlin/com/funny/aitoy/buttplug/AndroidExternalButtplugClient.kt`

新增或修复协议时：

- 不要用中文商品名做主要 matcher，优先使用底层设备名、Service UUID、Characteristic UUID、广播 Service Data、product code 或官方/反编译证据。
- 不要覆盖用户反馈已证明可用的历史路径；同品牌同名但底层不同的设备优先新增确定性分流。
- 修协议必须同步根目录协议文档：总索引 `docs/protocol_support.md` 和对应 `docs/protocols/*.md`。
- 线上排查先用窄时间窗 Trace 证明实际命中的协议、UUID、写入路径和反馈事件，再改 matcher 或控制语义。

## Relay、Trace 与诊断

- Relay 客户端：`composeApp/src/commonMain/kotlin/com/funny/aitoy/relay/RelayClient.kt`
- Relay 序列脚本解析：`composeApp/src/commonMain/kotlin/com/funny/aitoy/relay/RelaySequenceScript.kt`
- Trace 事件模型：`composeApp/src/commonMain/kotlin/com/funny/aitoy/diagnostics/AiToyTraceEvent.kt`
- Android Trace 上传：`composeApp/src/androidMain/kotlin/com/funny/aitoy/diagnostics/AiToyTraceUploader.kt`
- Android 崩溃上报：`composeApp/src/androidMain/kotlin/com/funny/aitoy/diagnostics/AiToyCrashReporter.kt`

本地 UI 日志和远端 Trace 是两条路径：

- `appendLog` 只用于端上展示。
- 线上排查必须在源头创建 `AiToyTraceEvent`，再交给上传器。
- 不要在 `AiToyTraceUploader` 里靠业务文案字符串过滤；上传策略必须由产生日志的源头决定。
- 默认不要上传高频流水线日志，如原始 Notify、写入回调、状态同步 payload、ACK、keepalive、重复扫描结果。
- 需要上传高频摘要时必须使用稳定 key 和限频策略。
- Relay 在线状态以服务端 ACK / `/api/status` / ws 诊断为准，不要只相信 App 本地“已在线”展示。

## 会员与额度

- 付费方案和 Trace 使用量口径见根目录 `docs/pricing_and_usage_analysis.md`。
- App 用户侧展示“AI 控制时长”，不要展示“指令数”“Trace”“duration”“Relay”等内部概念。
- 连接、扫描、待机、本地手动控制、停止控制不应消耗额度。
- `stop()` 是安全能力，永远不能因为额度不足而被拦截。
- 免费版也必须保留完整体验闭环：连接设备、少量 AI 控制、随时停止。
- 会员权益应围绕更长 AI 控制、多设备、新协议提前体验、优先排查等用户可理解价值。

## UI 与文案

- UI 使用温暖的深色风格，面向成熟商业产品，不写测试感、工程感文案。
- 面向用户的文案只写用户能理解的状态、动作和结果，不暴露协议、UUID、JSON、MCP、回调、Trace 等实现细节。
- 调试信息只放到日志、诊断页或开发入口，不进入普通用户主流程。
- Compose 页面遵循 MVVM；ViewModel 变量名使用 `vm`。
- 页面状态优先收敛到 VM，避免在页面里另起一套业务状态。
- 可复用 Composable、Modifier、颜色和小组件要抽出，避免单文件持续膨胀。
- `Modifier` 参数作为第一个可选参数。

## 导航与状态

- 导航统一使用 `core/src/commonMain/kotlin/com/funny/aitoy/core/navigation/Navigator.kt`。
- 新页面接入 `rememberNavigator` / `NavigatorProvider`，不要在页面中自建 tab/router 状态。
- 持久化优先使用 `DataSaverUtils` + `dataSaverState` 体系。
- 用户相关数据使用 `userDataSaverState(key, initialValue)`，让登录态和设备态按 owner 自动隔离。
- 全局配置使用 `mutableDataSaverStateOf(DataSaverUtils, ...)` 或 `rememberDataSaverState(...)`。
- 新类型持久化前在 `AiToyPrefsInit` 注册转换器。

## 网络与异步

- 新增网络接口统一使用 Retrofit + Service 接口。
- 接口调用使用 `apiRequest { service.func(...) }` 等统一封装。
- JSON 序列化统一使用 KotlinX Serialization 和 `JsonX`。
- 不要新增 `org.json.JSONObject` / `JSONArray` 做业务请求或配置序列化。
- 新增异步逻辑使用协程和 `viewModelScope`；不要新增 `Handler`、回调式 OkHttp 请求或手写线程切换。
- Token 注入、缓存策略、超时策略放在统一网络层，业务层不要重复拼接鉴权头。

## 依赖

- 依赖版本在 `gradle/libs.versions.toml`。
- 使用 VersionCatalogs，命名按 `作者名-库名`，例如 `androidx-xxx`、`androidx-xxx-yyy`。
- 用户明确要求 SDK、框架或库时优先采用；如果与 minSdk、KMP 目标、许可证、体积或运行时风险冲突，先验证再说明阻塞。
- 引入 AI SDK 时优先使用 SDK 的类型化能力，不要手写 OpenAI-compatible HTTP、SSE、function call 聚合或 JSON schema 拼接替代。

## 常用工具

- 日志：`Log.d(TAG) { "message: $variable" }`
- Toast：`toast("message")`、`toastError("message")`
- 当前时间戳：`nowMs()`
- 主线程执行：`runOnUI { }`
- 缓存目录：`CacheManager.cacheDir`、`CacheManager.fileDir`

## 编译与验证

- 在 `KMP` 子仓库执行客户端检查，不要用根仓库状态判断 KMP 改动。
- 快速编译：`./gradlew :composeApp:compileDebugKotlinAndroid --console=plain --no-daemon`
- 调试包：`./gradlew :composeApp:assembleDebug --console=plain --quiet`
- 发布包：`./gradlew :composeApp:assembleRelease --console=plain`
- 修改 `gradle/libs.versions.toml`、`build.gradle.kts` 或跨模块 API 后，至少跑一次 `:composeApp:compileDebugKotlinAndroid`。
