# Local Relay Android

Standalone Android app that runs a local reverse-proxy on the phone/emulator and forwards traffic to your remote Happy server.

This is independent from the Happy Android app.

## What it does

- Starts a local HTTP server (default `127.0.0.1:3005`)
- Forwards HTTP requests to your configured target URL
- Proxies WebSocket upgrades as well (for `/v1/updates` and similar realtime paths)
- Runs as a foreground service so it stays alive while app is backgrounded
- Checks for APK updates on launch and supports manual update checks

## Typical usage

1. Open app
2. Set target URL (example: `http://118.196.100.121:3005`)
3. Set local port (example: `3005`)
4. Tap **Start**
5. Point your local client to `http://127.0.0.1:3005`

## Build

```bash
./gradlew assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## In-app update metadata

The app checks `BuildConfig.UPDATE_INFO_URL`, currently:

`http://118.196.100.121:18080/releases/local-relay-android/android/stable/latest.json`

Expected JSON format:

```json
{
  "app_id": "local-relay-android",
  "platform": "android",
  "channel": "stable",
  "version_code": 5,
  "version_name": "0.3.2",
  "file_name": "local-relay-android-0.3.2.apk",
  "file_size_bytes": 0,
  "sha256": "REPLACE_WITH_REAL_SHA256",
  "updated_at": "2026-02-25T03:30:00.000Z",
  "download_url": "http://118.196.100.121:18080/releases/local-relay-android/android/stable/0.3.2/local-relay-android-0.3.2.apk",
  "release_notes": "local relay release 0.3.2"
}
```

## Notes

- `Bind all interfaces` is off by default for safety.
- If you enable `Bind all interfaces`, the relay listens on `0.0.0.0`.
- Cleartext HTTP is enabled (`usesCleartextTraffic=true`) to support local HTTP targets.

## Publish to release center

Use the helper script to publish APK + canonical `latest.json`:

`/Users/peak/Desktop/codex/local-relay-android/scripts/publish-android-release-center.sh`

Example:

```bash
VERSION_NAME=0.3.2 VERSION_CODE=5 ./scripts/publish-android-release-center.sh
```

## Repository name

Project/repo name is fixed as: `local-relay-android`.
