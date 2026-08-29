# Muchi 1.2.1

Free Material You music player. YouTube plays in Google’s official IFrame player. Independent artists stream from Audius. Live radio uses Radio Browser.

Muchi is not affiliated with YouTube, Google, Spotify, or Audius.

Local:

```sh
node server.js
```

Open http://localhost:3000

## GitHub

After you create the new repo, put its URL in Render as `MUCHI_GITHUB` (Settings → Environment). In the app, **Settings → About** uses that for What’s new / feedback.

## Render

Do **not** use a README “Deploy to Render” button (that can fork a different service).

Connect this repo in the Render dashboard: New → Web Service → this GitHub repo → Manual Deploy.

- Build: none (`true`)
- Start: `node server.js`
- Health: `/api/health`

## Android (later)

Upload the `android/` folder, then add `.github/workflows/android-apk.yml` via **Add file** (GitHub drag-and-drop skips `.github`). Actions → **Build Muchi APK**.
