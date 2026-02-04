# SubMaker KMP

目标平台：Android + Desktop(JVM)

## 运行

- Android：`./gradlew :composeApp:assembleDebug`
- Desktop：`./gradlew :composeApp:run`

## 当前进度（MVP）

- `ASR` 页已接入：导入媒体文件（Android SAF / Desktop FileKit）→ 生成示例时间轴 → 导出 `SRT/VTT`
- `core` 提供：KMP `Context/Launcher/Uri` 设施 + `SrtWriter/VttWriter`
