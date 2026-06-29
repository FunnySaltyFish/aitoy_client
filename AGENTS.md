## 读取顺序
- 如果未读过父目录的 `AGENTS.md`，先读取一下。

## 技术要求
- 使用 MVVM 架构
- ViewModel 使用 vm 变量名
- 不需要单独创建页面 State，直接写在 VM 里
- VM 里优先直接使用 state（而不是 StateFlow + .collectAsState() 的形式）
- 不需要 state.value 的设计，直接 value 即可
- 整体页面风格温馨、暧昧，有流畅的使用体验和优秀的交互设计
- 代码可复用率高，提取公共 Composable、函数、Modifier 常量等
- 优先 import + 使用简单类名，即代码中 `import androidx.compose.material3.Text; Text {}` 优于 `androidx.compose.material3.Text {}`
- 确保你的代码优雅简洁、UI效果丝滑流畅，富有美感
- Modifier parameter should be the first optional parameter
- 控制单文件的长度，模块内可复用的组件放到 components 目录下，一组/一个为一个文件，项目可复用的组件放在
  core 模块下

## 依赖
- 使用 VersionCatalogs，以 作者名-库名 命名。比如 androidx 的库应该是 androidx-xxx，如果它有子库，才是 androidx-xxx-yyy，引用时 `libs.androidx.xxx.yyy`

## AI / 对话 / 工具调用

- 如果需求明确要求使用 AI SDK，必须优先使用该 SDK，不要手写 OpenAI-compatible HTTP、SSE、function call 聚合或 JSON schema 拼接来替代。
- 引入 SDK 前必须验证 Android/KMP 约束，尤其是 minSdk。若依赖如果要求高于当前 minSdk，不要用 `tools:overrideLibrary` 强行绕过，应告知用户，由用户抉择。
- 应用内工具只负责把 SDK 已解析出的类型化参数转成业务调用；工具执行结果要使用面向用户的短文案，不暴露协议、JSON、回调、MCP 等实现细节。

## 协程与网络

- 新增网络接口统一使用 Retrofit + Service 接口；接口调用使用 `apiRequest { service.func(...) }` 等统一封装。
- JSON 序列化统一使用 KotlinX Serialization 和 `JsonX`；不要新增 `org.json.JSONObject` / `JSONArray` 做业务请求或配置序列化。
- 新增异步逻辑全部使用协程和 `viewModelScope`；不要新增 `Handler`、回调式 OkHttp 请求或手写线程切换。

## 持久化

项目统一使用 `DataSaverUtils` + `dataSaverState` 体系：

- 用户相关数据（登录态/偏好/最近记录）统一使用 `userDataSaverState(key, initialValue)`（`core.prefs`
  ），优先 `by` 委托。
- `userDataSaverState` 会按当前 owner（`uid:*` / `device:*`）自动切换独立存储；登录后对设备态数据无感迁移；登出后与账号数据隔离。
- 非用户作用域的全局配置（如 `serverBaseUrl`）使用 `mutableDataSaverStateOf(DataSaverUtils, ...)` 或
  `rememberDataSaverState(...)`。

```kotlin
var checked: Boolean by mutableDataSaverStateOf(DataSaverUtils, "AUTO_LANGUAGE_CHECKED", false)
onClick = { checked = true } // 自动保存并触发 UI 更新

// 自定义类型需先注册转换器（在 AiToyPrefsInit）
DataSaverConverter.registerTypeConverters<Language>( // enum
    save = { it.name },
    restore = { Language.valueOf(it) }
)
```

## 缓存

使用 CacheManager 统一管理缓存目录，按需创建子目录：

```kotlin
expect object CacheManager {
  val cacheDir: File
  val fileDir: File
}

fun CacheManager.cacheSubDir(name: String) = cacheDir.resolve(name).ensureDirectory()
fun CacheManager.fileSubDir(name: String) = fileDir.resolve(name).ensureDirectory()
```

## 网络层约定

- 网络请求统一使用 Retrofit + Service 接口
- 按业务拆分 Service（如 auth / user / entitlement / pay / asr），避免一个超大 API 文件
- 接口调用优先使用统一请求封装（如 `apiRequest { service.func(...) }`），常见请求应保持一行完成
- 简单参数优先 `@FormUrlEncoded + @Field`，避免为少量字段创建大量请求体类
- GET 与 POST 按语义区分：读取类接口优先 GET（可缓存），状态变更类接口使用 POST
- JSON 序列化统一走全局 `JsonX`，避免重复创建多个 Json 实例
- Token 注入、缓存策略放在全局 OkHttp 拦截器，业务层不重复拼接鉴权头
- GET 缓存策略通过接口注解声明（如 `@GetCache(...)`），避免在拦截器中维护路径分支
- 超时策略通过接口注解声明（如 `@DynamicTimeout(...)`），按接口粒度控制读写超时

## Trace 与诊断日志

- 端上本地日志和远端 Trace 是两条路径：`appendLog` 只负责本地 UI 展示；线上需要排查的事件使用 `AiToyTraceEvent` 交给 `AiToyTraceUploader.recordBle(event)`。
- 新增 BLE、Relay 或协议排查日志时，默认不要上传。只有真正需要线上诊断的事件才在源头标记上传策略：
  - `Always`：错误、连接请求、GATT 连接、服务发现、协议候选、协议确认、协议就绪、广播协议就绪、手动模板匹配、Relay 收到指令、断线/失败。
  - `SessionOnce`：同一 App session 内只需要知道发生过一次的状态，例如 Relay 已在线。
  - `RateLimited`：控制摘要、广播摘要等可能高频但仍有运营诊断价值的事件，必须提供稳定 `key` 和合理 `intervalMs`。
  - `Drop`：默认策略，适用于写入回调、Notify 原始字节、状态同步 payload、ACK、keepalive、重复扫描结果等高频流水线日志。
- 不要在 `AiToyTraceUploader` 里用业务文案做字符串过滤。上传器只负责通用策略执行、排队、flush 和 probe；具体是否上传必须由产生日志的源头决定。
- 需要临时排查某个协议的低层字节时，在对应源头临时加结构化 Trace 并设置限频或 session 周期策略；排查结束后保留有长期价值的关键事件，移除噪音上传。

## 全局信息

> 下述：.core 代表 com.funny.aitoy.core

- 日志 `Log.d(TAG) { "message: $variable" }`
- Toast `toast("message", [type = ToastType.Error])` 或简便方法 `toastError("message")`
- .core.utils.nowMs() 获取当前时间戳（毫秒）
- .core.utils.runOnUI { }，安卓在主线程运行

## 编译

- :moduleName:compileDebugKotlinAndroid
