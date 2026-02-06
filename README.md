# SubMaker KMP

目标平台：Android + Desktop(JVM)

## 运行

- Android：`./gradlew :composeApp:assembleDebug`
- Desktop：`./gradlew :composeApp:run`

## 当前进度（MVP）

- `ASR` 页已接入：导入媒体文件（Android SAF / Desktop FileKit）→ 生成示例时间轴 → 导出 `SRT/VTT`
- `core` 提供：KMP `Context/Launcher/Uri` 设施 + `SrtWriter/VttWriter`
- ✅ 已接入服务端账号/试用/付费骨架：
  - 邮箱 + 密码登录/注册
  - 忘记账号（邮箱验证码找回 username）
  - 忘记密码（邮箱验证码重置密码）
  - 未登录试用（设备维度）+ 登录后自动同步到账号记录
  - 付费（MVP：创建订单 + mock_checkout + 轮询订单状态）

## 服务端（SubMaker/server）

先启动服务端再体验账号与试用：

```bash
cd ../server
pip install -r requirements.txt
python -m py_compile app.py
python app.py
```

可选配置：

- SMTP（不配置时验证码仅输出到服务端日志）
  - `SUBMAKER_SMTP_HOST` / `SUBMAKER_SMTP_PORT` / `SUBMAKER_SMTP_USER` / `SUBMAKER_SMTP_PASSWORD` / `SUBMAKER_SMTP_FROM`
- Mock 支付（开发自测）
  - `SUBMAKER_MOCK_PAY_SECRET=your_secret`

## 客户端说明

- 客户端可在“账号与权益”页里修改 `Base URL`/`API Prefix`（Android 真机请用局域网 IP，不要用 127.0.0.1）。
