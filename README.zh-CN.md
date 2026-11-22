[English](README.md) · [繁體中文](README.zh-TW.md)

# FixAppOps

FixAppOps 是一个 LSPosed 兼容模块，让官方 **App Ops 9.0.7**（Google Play 版 `9.0.7.r1708.57e6ad70.G`）能在 Android 14–17 上运行，并沿用已安装的 APK 和原有的购买验证。[更新日志（英文）](CHANGELOG.md)

| Android 版本 | 修复内容 |
| --- | --- |
| Android 14（API 34） | 有对应翻译时，用本地化名称替换操作代号。原版的应用列表和工作资料切换可以照常使用。 |
| Android 14 季度更新 | 在采用 QPR2 权限接口的系统上，修复权限标志和运行时权限同步。采用 QPR3 接口的系统还会修复实时监控和使用历史的事件回调。模块会识别设备实际提供的接口。 |
| Android 15–17（API 35–37） | 修复剪贴板与使用监控事件，为新操作提供本地化名称、说明和分类。 |
| Android 15–17，另行启用 Shizuku 连接 | 修复远程服务读取权限标志、同步授予和撤销权限，以及记录使用历史所需的事件回调。 |
| Android 16 和 17（API 36–37） | 修复系统更新后 App Ops 只能列出当前用户的问题，恢复用户和工作资料的切换菜单。 |
| Android 17（API 37） | 修复 App Ops 打开已安装应用列表时因调用 `getInstalledPackages` 而闪退的问题。 |

## 安装

1. 保留官方 App Ops，另外安装已签名的 FixAppOps APK。[GitHub Releases](https://github.com/david082321/FixAppOps/releases) 提供自动打包版本，也可以从源码构建。
2. 在支持 **libxposed API 102** 的 LSPosed 管理器中启用 FixAppOps，并在作用域中勾选 **App Ops**（`rikka.appops`）。
3. 在 Android 系统设置中强行停止 App Ops，然后重新打开。如果设备有工作资料，点按 App Ops 侧栏菜单顶部的箭头即可切换。

## Shizuku 授权

**只授权官方 App Ops，不授权 FixAppOps，也能使用模块。** 在受影响的 Android 版本上，这样就能修复已安装应用列表以及用户和工作资料切换。在 Android 14–17 上，有对应翻译的操作也会显示本地化名称。在 App Ops 中开启相应的监视器后，就能看到剪贴板浮窗和相机使用通知。

**只有以下两项修复可能需要单独授权 FixAppOps，具体取决于系统是否更改了对应的远程接口。** 在早期的 Android 14 版本中，官方 App Ops 仍能处理这两项功能。标准 Android 14 QPR2 的权限同步修复需要单独授权，到了 QPR3，使用历史修复也需要。

- **运行时权限同步**会在你通过 App Ops 选择“允许”或“拒绝”时，同时更改 Android 系统中对应的权限。
- **使用情况历史记录**会将事件写入官方 App Ops 的历史记录，实时提醒消失后仍可查看。

App Ops 9.0.7 把这两项工作交给 Shizuku 启动的独立进程。该进程仍调用旧版 Android 接口。LSPosed 在 App Ops 的应用进程内加载模块，而这两项远程修复由 FixAppOps 提供的另一个服务执行。Shizuku 会核对服务所属的软件包，因此授予官方 App Ops 的权限只涵盖它自己的服务。

如需这两项修复，先在 Shizuku 中授权官方 App Ops，再打开 FixAppOps 并同意它的授权请求。FixAppOps 会重新打开 App Ops 完成连接。你可以随时撤销 FixAppOps 的授权，继续使用应用列表、资料切换和实时监控。

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
