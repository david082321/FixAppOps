[繁體中文](README.zh-TW.md) · [简体中文](README.zh-CN.md)

# FixAppOps

FixAppOps is an LSPosed compatibility module for the official **App Ops 9.0.7** on Android 14–17 (Google Play build `9.0.7.r1708.57e6ad70.G`). You can keep the installed APK and its existing purchase verification. [Changelog](CHANGELOG.md)

| Android version | What it fixes |
| --- | --- |
| Android 14 (API 34) | Replaces internal operation names with translated labels when available. The original app list and work profile switcher continue to work. |
| Android 14 quarterly updates | On systems with the QPR2 permission interface, adapts permission flags and runtime permission sync. On systems with the QPR3 interfaces, also restores live and history callbacks. The module detects the interfaces on the device. |
| Android 15–17 (API 35–37) | Restores clipboard and active-operation callbacks, and supplies translated names, descriptions and categories for newer operations. |
| Android 15–17 with the optional Shizuku connection | Adapts permission flags, runtime grant and revoke calls, and the callbacks used to record history in the App Ops remote service. |
| Android 16 and 17 (API 36–37) | Restores the user and work profile switcher when App Ops can only see the current user after a system update. |
| Android 17 (API 37) | Fixes the `getInstalledPackages` crash when App Ops opens the list of installed apps. |

## Install

1. Keep the official App Ops installed and install the signed FixAppOps APK. Download an automated build from [GitHub Releases](https://github.com/david082321/FixAppOps/releases) or build from source.
2. In an LSPosed manager that supports **libxposed API 102**, enable FixAppOps and select **App Ops** (`rikka.appops`) as its scope.
3. Force-stop App Ops from Android settings and reopen it. With a work profile present, tap the arrow at the top of the App Ops navigation drawer to switch profiles.

## Shizuku access

**You can leave FixAppOps unauthorized in Shizuku. In Shizuku mode, keep the official App Ops app authorized.** The module restores the installed apps list and profile switching on affected Android versions. Operation labels use the available translations on Android 14–17. Enable the corresponding monitors in App Ops to see live clipboard alerts and camera usage notifications.

**Only two fixes need a separate Shizuku grant for FixAppOps, and only on systems where the corresponding remote call changed.** On early Android 14 builds, the official App Ops app could still handle both operations. On standard Android 14 QPR2, the separate grant is needed for the permission sync fix. On QPR3, it also enables the usage history fix.

- **Runtime permission sync** applies a grant or revocation made in App Ops to the Android permission itself.
- **Usage history** saves events to the official App Ops history so you can review them after a live alert disappears.

App Ops 9.0.7 performs both tasks in a separate process started by Shizuku. That process still calls older Android interfaces. LSPosed loads the module into the App Ops user interface and monitoring processes. FixAppOps supplies its own service to fix the two operations that run remotely. Shizuku checks which package owns that service, so the official app’s grant covers its own service only.

To enable these two fixes, first authorize the official App Ops in Shizuku, then open FixAppOps and approve its access request. FixAppOps reopens App Ops to connect. You can revoke the FixAppOps grant at any time and continue using the app list, profile switching and live monitoring.

## Build from source

Install JDK 17, Gradle 9.5.0, and Android SDK Platform 37. Run the following command from the repository root.

```sh
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Build a signed APK locally

On Windows, use PowerShell 7.3 or later, JDK 17 or later (tested with JDK 21), Android SDK Platform 37, and Build Tools 37.0.0. Set `JAVA_HOME` and `ANDROID_HOME`. For a personal build, use your own PKCS#12 keystore and a one-line password file. Use the same password for the keystore and its signing key. Keep both files outside the repository.

```powershell
pwsh ./build.ps1 -KeyStore 'C:\keys\FixAppOps\appops-local.p12' -PasswordFile 'C:\keys\FixAppOps\storepass.txt'
```

The signed module is written to `build/AppOps-Android16-17-LSPosed.apk`. The script verifies the project’s release certificate by default. When signing a personal build, pass `-ExpectedSignerSha256` with your certificate’s SHA-256 fingerprint as 64 hexadecimal characters.
