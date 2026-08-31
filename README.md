# Muchi 1.2.1

Free Material You music player. YouTube plays in Google’s official IFrame player. Independent artists stream from Audius. Live radio uses Radio Browser.

Muchi is not affiliated with YouTube, Google, Spotify, or Audius.

## Platforms

| Platform | How |
|---|---|
| **Web** | `npm start` → http://localhost:3000 (unchanged, full Liquid Glass UI) |
| **Android** | Capacitor shell — `npx cap sync android`, open in Android Studio, or run the `android-capacitor.yml` workflow (APK + AAB) |
| **iOS** | Capacitor shell — `npx cap sync ios`, open `ios/App/App.xcodeproj` in Xcode, or run the `ios-capacitor.yml` workflow |

One shared web codebase (`public/`) powers all three. Native shells add
background playback, lock-screen media controls, media notifications,
status-bar styling, native sharing and offline-download notifications —
the same Liquid Glass design everywhere. See **MOBILE.md** for build,
signing and distribution instructions.

> `android-legacy/` holds the previous fully-native Kotlin/Compose app for
> reference; the Capacitor app in `android/` is the current mobile client.
