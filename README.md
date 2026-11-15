# 星鉴助手（Ricky Assistant）

一款本地优先的 Android 个人助理应用：把**短信、通话、邮件、记账、游戏排行**集中在一个简约的 Compose 界面里。所有个人数据默认只保存在本机，AI 与邮件等网络能力均支持独立代理。

- 语言 / 框架：Kotlin + Jetpack Compose（Material 3），MVVM + Clean Architecture
- 最低版本：Android 11（API 30），目标 API 37
- 构建：AGP 9.5.1（内置 Kotlin 2.2.10）+ Gradle 9.6 + KSP
- 存储：Room 2.7.2（本地库）+ EncryptedSharedPreferences（密钥与账号）

## 功能模块

| 模块 | 说明 |
| --- | --- |
| 首页 | 短信/通话/邮件/账目/游戏进度汇总，图表按日/周/月统计 |
| 通讯 | 顶部切换 **短信 / 通话 / 邮件** 三个面板；短信与通话读取系统记录，可本地搜索；邮件通过 IMAP 拉取（支持代理） |
| 记账 | 自动记账：通知监听 + 支付短信 + 无障碍识别支付结果页，三通道入库并去重，带来源标签；支持分类、CSV 导出 |
| 游戏 | TapTap 榜单（热门 / 新品 / 预约），查看游戏详情与截图，支持一键下载安装包（可配置 TapTap 账号使用官方直链） |
| 设置 | 主题与自定义背景图、自动记账开关、AI 助手入口、TapTap 账号、代理配置、权限管理、清空本地数据 |

## 构建

前置条件：JDK 17、Android SDK（API 37）、可用的 Gradle 网络环境。

```bash
# Windows PowerShell
$env:JAVA_HOME="<你的 JDK 17 路径>"
.\gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

产物位置：`app/build/outputs/apk/debug/app-debug.apk`

仓库中**不包含** `local.properties`、签名文件与任何构建产物；如需本地构建，请自行创建 `local.properties` 并写入 `sdk.dir=<Android SDK 路径>`。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 邮件 IMAP、AI 请求、游戏榜单 |
| `READ_SMS` / `RECEIVE_SMS` | 读取与接收短信（用于通讯与支付短信记账） |
| `READ_CALL_LOG` | 读取通话记录 |
| `POST_NOTIFICATIONS` | 下载完成等状态提示 |
| `REQUEST_INSTALL_PACKAGES` | 游戏安装包下载完成后拉起系统安装器 |
| 通知监听服务 / 无障碍服务 | 自动记账的可选通道，需在系统设置中手动开启 |

所有读取的数据仅写入本机数据库，不会自动上传；AI 对话内容只在用户主动发送时提交给所配置的服务商。

## 游戏模块说明

- 榜单与详情来自 TapTap 公开 Web API，无需登录。
- **TapTap 账号（可选）**：在 `设置 → TapTap 账号` 中通过应用内 WebView 登录，登录后用 `CookieManager` 读取 Cookie 并加密保存（也可手动粘贴 Cookie）。下载时优先带该 Cookie 请求 TapTap 官方 CDN 直链，未登录或直链不可用时自动回退到 APKPure 免鉴权直链。
- 安装包在**应用内**下载（带进度、可取消），随后由 `PackageInstaller` 拉起系统安装页；若分发的是 `.xapk` 分包，会自动解压出 base 与 split APK，按多 APK 会话安装；全程不跳转浏览器。
- 账号 Cookie 仅保存在本机加密存储中，不写入日志、不上传服务器；退出账号或清空本地数据即失效。

## 版本与发布

- 版本号维护在 `app/build.gradle.kts` 的 `versionCode` / `versionName`。
- 每个版本：更新版本号 → 提交（`chore: bump version to x.y.z`）→ 打标签 `vX.Y.Z` 并推送 → 在 GitHub Release 中附带 `app-release.apk`（APK 不进代码仓库）。

## 许可证

本项目未声明开源许可证，默认保留所有权利。如需授权他人使用，请先补充 `LICENSE` 文件。
