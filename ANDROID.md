# Muchi Android (native 1.3.0)

This is a **real Android app** (Kotlin + Jetpack Compose), target **Android 16 / API 36**. It is not a WebView of the website. The website on Render stays for browsers.

## Install

https://github.com/Kaibshshdheueejw/Muchi-music/releases/latest/download/Muchi.apk

First time: uninstall the old debug / WebView APK, then install this signed build.

## Signing (keystore is NOT in the repo)

The release APK is signed with a keystore supplied through GitHub Actions **secrets**, so no signing key material lives in the repository. Before running the **Build Muchi APK** workflow, add two repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `MUCHI_KEYSTORE_B64` | base64 of `muchi-release.p12` (one line, no wrapping) |
| `MUCHI_KEYSTORE_PASSWORD` | keystore / key password |

The workflow restores the keystore from `MUCHI_KEYSTORE_B64`, builds, then deletes the previous `v1.3.1` release/tag before publishing (so re-runs don't fail with "release already exists"). If the secrets are missing the build stops early with a clear message.

**Key change note:** the signing key was rotated in 2026-08 — anyone with an older build installed must **uninstall it first** before installing a new build (Android rejects signature changes).

Local builds:
- `assembleDebug` / Android Studio Run → no keystore needed (standard debug key).
- `assembleRelease` → export `MUCHI_KEYSTORE_FILE` (path to the .p12) and `MUCHI_KEYSTORE_PASSWORD`.

## Google Sign-In

1. Google Cloud Console → APIs & Services → Credentials  
2. Create **OAuth client ID** → **Web application**  
3. Add GitHub secret `GOOGLE_WEB_CLIENT_ID` = that client ID  
4. Also create an **Android** OAuth client: package `app.muchi.music`  
   SHA-1: `87:09:D1:9B:64:04:53:59:ED:71:3A:0D:5F:5B:C2:95:E8:7A:2D:E5`  
   SHA-256: `73:D9:D3:29:BF:06:5F:1B:DA:85:4A:87:E0:18:57:74:61:B3:EF:AF:CE:A0:C8:41:B0:5F:C2:5B:B1:F3:1E:F8`  
5. Rebuild the APK

YouTube songs play in **Google’s official player** (no ripping). Audius and radio play in ExoPlayer with lock-screen controls.
