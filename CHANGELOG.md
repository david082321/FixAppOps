# Changelog

## 1.4

- Added Android 14 (API 34) support. Operations such as Bluetooth connections and exact alarms now display translated labels when available.
- Fixed runtime permission sync on Android 14 quarterly updates. The module recognizes the numeric device ID used by QPR2 and the persistent ID used by QPR3. It checks grants and revocations against Android and keeps the original permission calls on early Android 14.
- Restored clipboard access and camera start/stop events on builds with QPR3-style callbacks. Live alerts work with only the official App Ops app authorized in Shizuku. FixAppOps needs its own grant to deliver those callbacks to the usage history service.
- Fixed the optional Shizuku connection on Android 14 so App Ops can verify the calling app during setup and reconnect after a force-stop.

## 1.3 (2026-11-21)

- Restored live clipboard alerts and active-operation notifications on Android 15–17. These fixes work with only the official App Ops app authorized in Shizuku.
- Added localized names, descriptions and categories for newer operations, including heart rate, skin temperature, local network access and promoted notifications.
- Added an optional Shizuku service for runtime permission sync and usage history. It applies grants and revocations to Android, reads back the result, and restores the callbacks that App Ops uses to save history. Only these two fixes require a separate grant for FixAppOps.
- Fixed the optional service connection so App Ops can reconnect after a force-stop.

[Download v1.3](https://github.com/david082321/FixAppOps/releases/tag/v1.3)

## 1.2 (2026-11-21)

- Added support for Android 16 (API 36).
- Restored the user and work profile switcher on Android 16 QPR1 and later Android 16 builds, as well as Android 17. App Ops can again list apps and manage their permissions separately for each profile.

[Download v1.2](https://github.com/david082321/FixAppOps/releases/tag/v1.2)

## 1.1

- Translated the module name and description into all 26 locales supported by App Ops 9.0.7, with English as the fallback.

## 1.0

- Fixed a crash in App Ops 9.0.7 when opening the installed apps list on Android 17 (API 37). The fix ships as an LSPosed module so users can keep the officially signed App Ops app.
