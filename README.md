[繁體中文](README.zh-TW.md) · [简体中文](README.zh-CN.md)

# FixAppOps

FixAppOps 1.2 is an LSPosed module for **App Ops 9.0.7** (Google Play build `9.0.7.r1708.57e6ad70.G`) on Android 16 and 17. It runs inside the official App Ops app, so you can keep your existing installation and purchase verification.

| Android version | What it fixes |
| --- | --- |
| Android 16 and 17 (API 36–37) | Restores the user and work profile switcher when App Ops can only see the current user after a system update. |
| Android 17 (API 37) | Fixes the `getInstalledPackages` crash when App Ops opens the list of installed apps. |

## Install

1. Keep the official App Ops installed. Install the signed module APK. Version 1.2 is available directly in this repository. Published updates appear under **Assets** on GitHub. [Download FixAppOps 1.2](dist/FixAppOps-1.2.apk) · [GitHub Releases](https://github.com/david082321/FixAppOps/releases)
2. In an LSPosed manager that supports **libxposed API 102**, enable FixAppOps and select **App Ops** (`rikka.appops`) as its scope.
3. Force-stop App Ops from Android settings and reopen it. With a work profile present, tap the arrow at the top of the App Ops navigation drawer to switch profiles.

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

## Publish a GitHub Release

In **Settings → Secrets and variables → Actions → New repository secret**, add these two secrets. Replace the example paths with the full paths to the release signing files. Run one command, paste its clipboard value into the corresponding secret, then repeat for the other.

| Secret | Value | PowerShell command to copy it |
| --- | --- | --- |
| `FIXAPPOPS_KEYSTORE_BASE64` | Base64-encoded bytes of `appops-local.p12` | `Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\keys\FixAppOps\appops-local.p12')))` |
| `FIXAPPOPS_KEYSTORE_PASSWORD` | Single-line password from `storepass.txt` | `Set-Clipboard -Value (Get-Content -LiteralPath 'C:\keys\FixAppOps\storepass.txt' -TotalCount 1)` |

Set `versionName` and increment `versionCode` in `app/build.gradle.kts` for each new release. Commit and push that change to `main`, then publish using either method.

- **Version tag** — push the matching tag, for example `git tag v1.2` followed by `git push origin v1.2`.
- **Manual run** — choose **Actions → Release → Run workflow** on `main`. The workflow uses the Gradle version to create the tag.

The workflow runs the same `build.ps1` used locally, checks the APK against the project’s release signing certificate, and publishes the signed APK as a release asset. GitHub supplies the workflow token.

## Verification

Tests on Android 16 QPR2 and Android 17 emulators used real Android system services. They confirmed that personal and work profiles return separate app lists and that changing a work app’s operation leaves the same app in the personal profile unaffected. A test harness invoked the module callbacks. LSPosed injection on a physical device remains unverified.
