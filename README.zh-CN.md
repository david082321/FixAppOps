[English](README.md) · [繁體中文](README.zh-TW.md)

# FixAppOps

FixAppOps 1.2 是适用于 Android 16 和 17 的 LSPosed 模块，支持 **App Ops 9.0.7**（Google Play 版 `9.0.7.r1708.57e6ad70.G`）。模块在官方 App Ops 的进程内运行，可以继续使用已安装的 App Ops 和原有的购买验证流程。

| Android 版本 | 修复内容 |
| --- | --- |
| Android 16 和 17（API 36–37） | 修复系统更新后 App Ops 只能列出当前用户的问题，恢复用户和工作资料的切换菜单。 |
| Android 17（API 37） | 修复 App Ops 打开已安装应用列表时因调用 `getInstalledPackages` 而闪退的问题。 |

## 安装

1. 保留官方 App Ops，另外安装已签名的模块 APK。可以直接从本仓库下载 1.2 版，以后发布的版本可在 GitHub 发布页面的 **Assets** 下下载。[下载 FixAppOps 1.2](dist/FixAppOps-1.2.apk) · [GitHub Releases](https://github.com/david082321/FixAppOps/releases)
2. 在支持 **libxposed API 102** 的 LSPosed 管理器中启用 FixAppOps，并在作用域中勾选 **App Ops**（`rikka.appops`）。
3. 在 Android 系统设置中强行停止 App Ops，然后重新打开。如果设备有工作资料，点按 App Ops 侧栏菜单顶部的箭头即可切换。

## 从源码构建

安装 JDK 17、Gradle 9.5.0 和 Android SDK Platform 37，然后在项目根目录运行以下命令。

```sh
gradle :app:assembleDebug
```

生成的调试版 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 在本地构建签名版 APK

Windows 需要 PowerShell 7.3 或更高版本、JDK 17 或更高版本（已使用 JDK 21 测试）、Android SDK Platform 37 和 Build Tools 37.0.0。请设置 `JAVA_HOME` 和 `ANDROID_HOME`。自行构建时可使用个人的 PKCS#12 密钥库，并将密码保存为单行文本文件。密钥库与签名密钥使用相同的密码，两个文件都存放在项目目录之外。

```powershell
pwsh ./build.ps1 -KeyStore 'C:\keys\FixAppOps\appops-local.p12' -PasswordFile 'C:\keys\FixAppOps\storepass.txt'
```

签名后的模块位于 `build/AppOps-Android16-17-LSPosed.apk`。脚本默认校验项目的发布证书。使用个人密钥时，请通过 `-ExpectedSignerSha256` 指定个人证书的 SHA-256 指纹，格式为 64 个十六进制字符。

## 发布 GitHub Release

前往 **Settings → Secrets and variables → Actions → New repository secret**，添加下面两个仓库机密。请将示例路径改为正式签名文件的完整路径。每运行一条命令，就将剪贴板内容粘贴到对应的机密中，再处理下一个。

| 机密名称 | 填入的值 | 复制到剪贴板的 PowerShell 命令 |
| --- | --- | --- |
| `FIXAPPOPS_KEYSTORE_BASE64` | `appops-local.p12` 文件内容的 Base64 编码 | `Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\keys\FixAppOps\appops-local.p12')))` |
| `FIXAPPOPS_KEYSTORE_PASSWORD` | `storepass.txt` 中的单行密码 | `Set-Clipboard -Value (Get-Content -LiteralPath 'C:\keys\FixAppOps\storepass.txt' -TotalCount 1)` |

每次发布新版本，先在 `app/build.gradle.kts` 中设置 `versionName` 并递增 `versionCode`。提交更改并推送到 `main` 后，可以选择以下任一方式发布。

- **推送版本标签** — 推送与版本对应的标签。例如依次运行 `git tag v1.2` 和 `git push origin v1.2`。
- **手动运行** — 前往 **Actions → Release → Run workflow**，选择 `main`。工作流会按 Gradle 版本创建标签。

GitHub Actions 会运行与本地相同的 `build.ps1`，用项目的正式签名证书验证 APK，再将签名版 APK 上传为 GitHub Release 附件。工作流使用 GitHub 提供的内置令牌。

## 验证范围

已在 Android 16 QPR2 和 Android 17 模拟器上使用实际的 Android 系统服务进行测试。个人资料和工作资料可以分别列出各自的应用，修改工作资料中某个应用的操作权限后，个人资料中的同一应用仍保留原设置。测试通过工具调用了模块的回调函数，LSPosed 在真机上加载模块的情况仍待验证。
