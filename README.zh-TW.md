[English](README.md) · [简体中文](README.zh-CN.md)

# FixAppOps

FixAppOps 1.2 是適用於 Android 16 和 17 的 LSPosed 模組，支援 **App Ops 9.0.7**（Google Play 版 `9.0.7.r1708.57e6ad70.G`）。模組在官方 App Ops 的程序內執行，可沿用已安裝的 App Ops 和原有的購買驗證流程。

| Android 版本 | 修正內容 |
| --- | --- |
| Android 16 和 17（API 36–37） | 修正系統更新後 App Ops 只列出目前使用者的問題，恢復使用者與工作設定檔的切換選單。 |
| Android 17（API 37） | 修正 App Ops 開啟已安裝應用程式清單時因呼叫 `getInstalledPackages` 而閃退的問題。 |

## 安裝

1. 保留官方 App Ops，另外安裝已簽署的模組 APK。可以直接從本儲存庫下載 1.2 版，後續發佈的版本可在 GitHub 發佈頁面的 **Assets** 下載。[下載 FixAppOps 1.2](dist/FixAppOps-1.2.apk) · [GitHub Releases](https://github.com/david082321/FixAppOps/releases)
2. 在支援 **libxposed API 102** 的 LSPosed 管理器啟用 FixAppOps，並在作用域勾選 **App Ops**（`rikka.appops`）。
3. 從 Android 系統設定強制停止 App Ops，再重新開啟。有工作設定檔時，點選 App Ops 側邊選單頂端的箭頭即可切換。

## 從原始碼建置

安裝 JDK 17、Gradle 9.5.0 和 Android SDK Platform 37，然後在專案根目錄執行以下指令。

```sh
gradle :app:assembleDebug
```

產生的偵錯版 APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。

### 在本機建置簽署版 APK

Windows 需要 PowerShell 7.3 以上、JDK 17 以上（已使用 JDK 21 測試）、Android SDK Platform 37 和 Build Tools 37.0.0。請設定 `JAVA_HOME` 和 `ANDROID_HOME`。自行建置時可使用個人的 PKCS#12 金鑰庫，並將密碼存成單行文字檔。金鑰庫與簽署金鑰使用相同密碼，兩個檔案都存放於專案目錄之外。

```powershell
pwsh ./build.ps1 -KeyStore 'C:\keys\FixAppOps\appops-local.p12' -PasswordFile 'C:\keys\FixAppOps\storepass.txt'
```

簽署後的模組位於 `build/AppOps-Android16-17-LSPosed.apk`。腳本預設會核對專案的發行憑證。使用個人金鑰時，請以 `-ExpectedSignerSha256` 指定個人憑證的 SHA-256 指紋，格式為 64 個十六進位字元。

## 發佈 GitHub Release

到 **Settings → Secrets and variables → Actions → New repository secret** 新增下列兩個密鑰。請將範例路徑改為正式簽署檔案的完整路徑。每執行一條指令，就將剪貼簿內容貼入對應的密鑰，再處理下一個。

| 密鑰名稱 | 填入的值 | 複製到剪貼簿的 PowerShell 指令 |
| --- | --- | --- |
| `FIXAPPOPS_KEYSTORE_BASE64` | `appops-local.p12` 檔案內容的 Base64 編碼 | `Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\keys\FixAppOps\appops-local.p12')))` |
| `FIXAPPOPS_KEYSTORE_PASSWORD` | `storepass.txt` 中的單行密碼 | `Set-Clipboard -Value (Get-Content -LiteralPath 'C:\keys\FixAppOps\storepass.txt' -TotalCount 1)` |

每次發佈新版，先在 `app/build.gradle.kts` 設定 `versionName` 並遞增 `versionCode`。提交變更並推送至 `main` 後，可用以下任一方式發佈。

- **推送版本標籤** — 推送與版本相符的標籤。例如依序執行 `git tag v1.2` 和 `git push origin v1.2`。
- **手動執行** — 前往 **Actions → Release → Run workflow**，選取 `main`。工作流程會依 Gradle 版本建立標籤。

GitHub Actions 會執行與本機相同的 `build.ps1`，用專案的正式簽署憑證核對 APK，再將簽署版 APK 附加到 GitHub Release。工作流程使用 GitHub 內建權杖。

## 驗證範圍

已在 Android 16 QPR2 和 Android 17 模擬器上使用實際的 Android 系統服務進行測試。個人與工作設定檔可以分別列出各自的應用程式，針對工作設定檔內的應用程式變更一項操作權限後，個人設定檔中同一個應用程式仍維持原設定。測試由工具呼叫模組的回呼函式，LSPosed 在實機載入模組的情況仍待驗證。
