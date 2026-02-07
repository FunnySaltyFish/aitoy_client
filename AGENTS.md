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
    save = { json.toJson(it) }, 
    restore = { json.fromJson(it) }
)

// Composable 中
var showHelpDialog by rememberDataSaverState(
    key = "show_model_manager_help",
    initialValue = true
)
```
