# Local Relay Android

Standalone Android app that runs a local reverse-proxy on the phone/emulator and forwards traffic to your remote Happy server.

This is independent from the Happy Android app.

## What it does

- Starts a local HTTP server (default `127.0.0.1:3005`)
- Forwards HTTP requests to your configured target URL
- Proxies WebSocket upgrades as well (for `/v1/updates` and similar realtime paths)
- Runs as a foreground service so it stays alive while app is backgrounded

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

## Notes

- `Bind all interfaces` is off by default for safety.
- If you enable `Bind all interfaces`, the relay listens on `0.0.0.0`.
- Cleartext HTTP is enabled (`usesCleartextTraffic=true`) to support local HTTP targets.

## Repository name

Project/repo name is fixed as: `local-relay-android`.
