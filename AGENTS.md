## 技术要求
- 使用 MVVM 架构
- ViewModel 使用 vm 变量名
- 不需要单独创建页面 State，直接写在 VM 里
- VM 里优先直接使用 state（而不是 StateFlow + .collectAsState() 的形式）
- 不需要 state.value 的设计，直接 value 即可
- 整体符合 MD3 设计
- 代码可复用率高，提取公共 Composable、函数、Modifier 常量等
- 优先 import + 使用简单类名，即代码中 `import androidx.compose.material3.Text; Text {}` 优于 `androidx.compose.material3.Text {}`
- 确保你的代码优雅简洁、UI效果丝滑流畅，富有美感
- Modifier parameter should be the first optional parameter
- 控制单文件的长度，模块内可复用的组件放到 components 目录下，一组/一个为一个文件，项目可复用的组件放在
  core 模块下

## 依赖
- 使用 VersionCatalogs，以 作者名-库名 命名。比如 androidx 的库应该是 androidx-xxx，如果它有子库，才是 androidx-xxx-yyy，引用时 `libs.androidx.xxx.yyy`

## 持久化
项目使用 DataSaverUtils 全局变量 .readData 和 .saveData，一般使用其高层封装 `DataSaverState`，用法：

```kotlin
// ViewModel 或 object 中

var checked: Boolean by mutableDataSaverStateOf(DataSaverUtils, "AUTO_LANGUAGE_CHECKED", false)
onClick = { checked = true } // 自动保存并触发 UI 更新

// 也支持自定义类型
var targetLanguage: Language by mutableDataSaverStateOf(DataSaverUtils, "ASR_TARGET_LANG", Language.ENGLISH)
// 需要在 com.funny.submaker.core.prefs.SubMakerPrefsInit 注册
DataSaverConverter.registerTypeConverters<Language>( // enum
    save = { it.name },
    restore = { Language.valueOf(it) }
)
DataSaverConverter.registerTypeConverters<EditablePrompt>( // serializable class
    save = { it.toJson() },  // com.funny.submaker.core.utils.JsonX.toJson()
    restore = { it.fromJson<EditablePrompt>() }
)

// Composable 中
var showHelpDialog by rememberDataSaverState(
    key = "show_model_manager_help",
    initialValue = true
)
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

## 全局信息

> 下述：.core 代表 com.funny.submaker.core

- 日志 `Log.d(TAG) { "message: $variable" }`
- Toast `toast("message", [type = ToastType.Error])`
- .core.utils.nowMs() 获取当前时间戳（毫秒）
- .core.utils.runOnUI { }，安卓在主线程运行

## 编译

- :moduleName:compileDebugKotlinAndroid