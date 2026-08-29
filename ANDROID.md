# Muchi Android (native 1.3.0)

This is a **real Android app** (Kotlin + Jetpack Compose), target **Android 16 / API 36**. It is not a WebView of the website. The website on Render stays for browsers.

## Install

https://github.com/Kaibshshdheueejw/Muchi-music/releases/latest/download/Muchi.apk

First time: uninstall the old debug / WebView APK, then install this signed build.

## Google Sign-In

1. Google Cloud Console → APIs & Services → Credentials  
2. Create **OAuth client ID** → **Web application**  
3. Add GitHub secret `GOOGLE_WEB_CLIENT_ID` = that client ID  
4. Also create an **Android** OAuth client: package `app.muchi.music`  
   SHA-1: `87:09:D1:9B:64:04:53:59:ED:71:3A:0D:5F:5B:C2:95:E8:7A:2D:E5`  
   SHA-256: `73:D9:D3:29:BF:06:5F:1B:DA:85:4A:87:E0:18:57:74:61:B3:EF:AF:CE:A0:C8:41:B0:5F:C2:5B:B1:F3:1E:F8`  
5. Rebuild the APK

YouTube songs play in **Google’s official player** (no ripping). Audius and radio play in ExoPlayer with lock-screen controls.
