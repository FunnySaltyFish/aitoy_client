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

项目统一使用 `DataSaverUtils` + `dataSaverState` 体系：

- 用户相关数据（登录态/偏好/最近记录）统一使用 `userDataSaverState(key, initialValue)`（`core.prefs`
  ），优先 `by` 委托。
- `userDataSaverState` 会按当前 owner（`uid:*` / `device:*`）自动切换独立存储；登录后对设备态数据无感迁移；登出后与账号数据隔离。
- 非用户作用域的全局配置（如 `serverBaseUrl`）使用 `mutableDataSaverStateOf(DataSaverUtils, ...)` 或
  `rememberDataSaverState(...)`。

```kotlin
var checked: Boolean by mutableDataSaverStateOf(DataSaverUtils, "AUTO_LANGUAGE_CHECKED", false)
onClick = { checked = true } // 自动保存并触发 UI 更新

// 自定义类型需先注册转换器（在 SubMakerPrefsInit）
DataSaverConverter.registerTypeConverters<Language>( // enum
    save = { it.name },
    restore = { Language.valueOf(it) }
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
