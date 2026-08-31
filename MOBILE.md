# Muchi Mobile — Capacitor builds (Android + iOS)

Muchi is **one web codebase** (`public/`) wrapped by **Capacitor** native shells:

```
                MUCHI
                   |
        Shared web codebase (public/)
                   |
      +------------+------------+
      |                         |
   Android (android/)         iOS (ios/)
  native shell               native shell
```

- The **web app** (Node server, `npm start`) is untouched and keeps working.
- The mobile apps load the **same Liquid Glass UI** in a WebView with real native
  capabilities: background audio, lock-screen media controls, media
  notification, hardware-back handling, status-bar styling, native sharing
  and offline-download notifications.
- There is **no Google Sign-In** yet. Auth was deliberately left out; it can
  be added later without a rewrite (nothing in the shell depends on it).

> Note: `android-legacy/` contains the previous fully-native Kotlin/Compose
> app (v1.3.0) — kept for reference. The Capacitor app in `android/` is the
> current mobile client.

---

## Requirements

| Tool | Version |
|---|---|
| Node.js | 20+ (22 recommended) |
| Android | JDK 21, Android SDK (compileSdk 36) |
| iOS | macOS + Xcode 16+ |

## Install & sync

```bash
npm install          # installs Capacitor + plugins
npx cap sync         # copies public/ into both native projects
```

`cap sync` must be re-run after **any** change under `public/`.

## Run

```bash
# Web (unchanged)
npm start

# Android
npx cap sync android
npx cap open android        # Android Studio → Run

# iOS
npx cap sync ios
npx cap open ios            # Xcode → Run (requires macOS)
```

## What the native shells add

| Capability | How |
|---|---|
| Background playback (Android + iOS) | WebView audio keeps playing in background; Android manifest has `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `WAKE_LOCK`; iOS `Info.plist` has `UIBackgroundModes: audio` |
| Lock-screen / notification / Control Center controls | `capacitor-music-controls-plugin` — media notification with play/pause/prev/next + scrub on Android, Now Playing on iOS. Events are forwarded to the app's own `togglePlay()/next()/prev()/seekTo()` — one player, no duplicates |
| Media button / headset events | Plugin events (Android headset plug/unplug → pause) |
| Hardware back button | `@capacitor/app` → closes modal/queue/video first, then app view history, then minimizes |
| Status bar | `@capacitor/status-bar` — light icons on the `#101413` canvas |
| Splash + icon | Generated from the existing Muchi logo; adaptive icon on Android, full AppIcon set on iOS |
| Native share | Track menu gains a "Share" entry (native only) via `@capacitor/share` |
| Offline-download notifications | When a download finishes while the app is backgrounded, `@capacitor/local-notifications` shows "Saved for offline". Permission is requested at that moment, never on launch |
| API origin | Native builds always talk to `https://muchi-music.onrender.com` (the live API); web keeps its same-origin / `MUCHI_API_BASE` behaviour |
| Google Sign-In | Server-side OAuth (server.js). Web: httpOnly cookie session. Native: system browser + `muchi://` deep-link return, Bearer token in localStorage. YouTube likes/playlists appear in Library. Env: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `MUCHI_SESSION_SECRET` (secret only on the server). Sessions are mirrored to `.muchi-sessions.json` (server-side only, gitignored) so sign-in survives Render's free-tier sleep/wake and redeploys. |

## Android release build

Signing material is **never committed**. Release builds read environment
variables:

```
MUCHI_KEYSTORE_FILE      path to the .jks/.p12/.keystore
MUCHI_KEYSTORE_PASSWORD  keystore + key password
MUCHI_KEYSTORE_ALIAS     key alias (default: "muchi")
```

```bash
cd android
./gradlew assembleRelease    # signed APK
./gradlew bundleRelease      # signed AAB (Google Play)
```

Without the env vars the release build **falls back to the debug key**
(loud warning) so you can still test — never publish that artifact.

### Google Play

1. Keep `applicationId app.muchi.music` stable.
2. Upload the **AAB** from `android/app/build/outputs/bundle/release/`.
3. Use Play App Signing (Google manages the upload key); the release
   keystore above is your upload key source.

## iOS release build

1. `npx cap sync ios`
2. Open `ios/App/App.xcodeproj` in Xcode.
3. Set your team under *Signing & Capabilities* (bundle id `app.muchi.music`).
4. `Product → Archive` → Distribute to App Store Connect.

Background audio is already configured (`UIBackgroundModes: audio`), so the
app keeps playing when locked / backgrounded.

## GitHub Actions

| Workflow | What it produces |
|---|---|
| `android-capacitor.yml` | `assembleRelease` APK + `bundleRelease` AAB, uploaded as artifacts. Needs secrets `MUCHI_KEYSTORE_B64`, `MUCHI_KEYSTORE_PASSWORD` (+ optional `MUCHI_KEYSTORE_ALIAS`) for a **signed** release; without them it builds debug-signed with a warning |
| `ios-capacitor.yml` | Builds an unsigned `.xcarchive` on a macOS runner (works without Apple credentials). Add Apple signing secrets and flip `CODE_SIGNING_ALLOWED` to sign |

## Testing checklist (per release)

- [ ] Web still works: `npm start` → home/search/playback/settings
- [ ] Android: install, launch, search, play, **lock screen → audio continues**,
      media notification controls, prev/next, resume, API connectivity
- [ ] iOS: launch, play, **lock screen → audio continues**, Now Playing /
      Control Center controls, interruptions, resume
- [ ] Offline downloads complete in background → notification appears
- [ ] No Google Sign-In flows present anywhere

### v83.1 (2026-08-31) — Android compile fix

The v82/v83 Android build failed in CI at `:app:compileReleaseJavaWithJavac`
(10 errors in `MuchiAudioPlugin.java` / `MuchiAudioService.java`). Root cause:
the native audio code used Media3 APIs from older versions, but the project
resolves **Media3 1.5.1**, where:

- `MediaSession.ConnectionResult.accept(boolean)` does not exist → use
  `ConnectionResult.AcceptedResultBuilder(session).setSessionActivity(...)`.
- `MediaSession.Callback` is an **interface** (not an abstract class) and the
  `onPlay/onPause/onSkipToNext/...` methods are gone → media button actions
  arrive via `onPlayerCommandRequest(session, controller, playerCommand)`.
- `DefaultMediaNotificationProvider.Builder.setSkipButtonsEnabled()` does not
  exist → the default provider already shows Previous / Play-Pause / Next.
- `PluginCall.getDouble(name, default)` requires a `Double` default, not `long`.

Fixed in v83.1 (commit after v83); no frontend/cache changes (still
`muchi-shell-v83`).
