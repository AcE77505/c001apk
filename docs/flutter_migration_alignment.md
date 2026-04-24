# Flutter -> 原生迁移功能对齐清单

> 基于当前原生仓库代码（分支 `chore/flutter-migration-audit`）核对。

## 重点登录能力

| Flutter 功能点 | 原生现状 | 对齐结果 | 说明 |
|---|---|---|---|
| 登录页右上角“网页登录”入口 | 已补齐 | ✅ | `LoginActivity` 顶部菜单新增 `网页登录`，打开 `WebViewActivity` 并传 `isLogin=true`。 |
| WebView 登录态回填（命中主页读取 uid/username/token） | 已补齐 | ✅ | `WebViewActivity` 在 `onPageFinished` 监听页面完成，命中主页后读取 Cookie，写入 `PrefManager` 并结束页面。 |
| 双登录方式（账号密码 / Token） | 已补齐 | ✅ | 消息页未登录点击后弹出登录方式选择对话框，可选“账号密码登录”或“Token登录（Cookie模式）”。 |
| 登录页 Cookie 直登 | 已补齐 | ✅ | 登录页新增 Cookie 输入框；`LoginViewModel.loginWithCookie()` 解析 uid/username/token 并回填登录状态。 |

## 迁移实现备注

- 当前“Token登录”按 Flutter cookie 模式对齐，要求输入完整 Cookie 串并包含 `uid/username/token`。
- WebView 回填逻辑在登录页场景启用（`isLogin=true`），避免影响通用网页浏览。
- 后续建议补一轮 UI 自动化/集成测试，覆盖：
  1. Web 登录成功后返回消息页并显示登录态。
  2. Cookie 缺字段时给出错误提示。

## Flutter 依赖到原生替代映射（初版）

| Flutter 依赖 | 原生对应 |
|---|---|
| `flutter_inappwebview` | Android `WebView` + `WebViewClient` + `CookieManager` |
| `dio` | `OkHttp` / `Retrofit`（现有网络层） |
| `get` | `ViewModel` + `LiveData` + Jetpack Navigation/Activity 跳转 |
| `hive` | `SharedPreferences`（`PrefManager`）+ Room（本地数据） |
