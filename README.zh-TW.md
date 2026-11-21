[English](README.md) · [简体中文](README.zh-CN.md)

# FixAppOps

FixAppOps 是適用於 Android 15–17 的相容模組，支援 **App Ops 9.0.7**（Google Play 版 `9.0.7.r1708.57e6ad70.G`），可沿用已安裝的官方 App Ops 與原有的購買驗證。

| Android 版本 | 修正內容 |
| --- | --- |
| Android 15–17（API 35–37） | 修復剪貼簿與使用監控事件，為新操作提供在地化名稱、說明與分類。 |
| Android 15–17，另行啟用 Shizuku 連線 | 修復遠端服務讀取權限旗標、同步授權與撤銷，以及寫入使用紀錄所需的事件回呼。 |
| Android 16 和 17（API 36–37） | 修正系統更新後 App Ops 只列出目前使用者的問題，恢復使用者與工作設定檔的切換選單。 |
| Android 17（API 37） | 修正 App Ops 開啟已安裝應用程式清單時因呼叫 `getInstalledPackages` 而閃退的問題。 |

## 安裝

1. 保留官方 App Ops，另外安裝已簽署的 FixAppOps APK。[GitHub Releases](https://github.com/david082321/FixAppOps/releases) 提供自動打包版本，或從原始碼建置。
2. 在支援 **libxposed API 102** 的 LSPosed 管理器啟用 FixAppOps，並在作用域勾選 **App Ops**（`rikka.appops`）。
3. 從 Android 系統設定強制停止 App Ops，再重新開啟。有工作設定檔時，點選 App Ops 側邊選單頂端的箭頭即可切換。

## Shizuku 授權

**只授權官方 App Ops，不授權 FixAppOps，也能使用模組。** 在受影響的 Android 版本上，這樣就能修復已安裝應用程式清單和使用者／工作設定檔切換。從 1.3 起，新操作的翻譯也能正常顯示。在 App Ops 開啟對應的監視器後，就能看到剪貼簿浮窗和相機使用通知。

**1.3 只有兩項修復需要另外授權 FixAppOps。**

- **執行階段權限同步**讓你在 App Ops 按下「允許」或「拒絕」時，Android 系統中的對應權限也會隨之變更。
- **使用情況歷史紀錄**會將事件寫入官方 App Ops 的歷史紀錄，即時提示消失後仍可回頭查閱。

App Ops 9.0.7 把這兩項工作交給 Shizuku 啟動的獨立程序。該程序仍呼叫舊版 Android 介面。LSPosed 會在 App Ops 的應用程式程序內載入模組，而這兩項遠端修復由 FixAppOps 提供的另一個服務執行。Shizuku 依服務所屬套件核對授權，因此授予官方 App Ops 的權限只涵蓋它自己的服務。

若需要這兩項修復，先在 Shizuku 授權官方 App Ops，再開啟 FixAppOps 並同意它的授權要求。FixAppOps 會重新開啟 App Ops 完成連線。你可以隨時撤銷 FixAppOps 的授權，繼續使用應用程式清單、設定檔切換和即時監控。

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
