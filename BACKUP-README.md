# Muchi — Project Backup & Restore Guide

This is a complete snapshot of the Muchi music player project (web app + Android
app). It is ready to upload to GitHub and connect to Render.

## What's in this backup

| Path | What it is |
|---|---|
| `server.js` | Node.js web server (zero npm dependencies — only Node built-ins) |
| `public/` | The web app: `index.html`, `app.js`, `styles.css`, `sw.js` (service worker), icons, manifest |
| `render.yaml` | Render blueprint (web service, `node server.js`, health `/api/health`) |
| `Procfile` / `start.sh` / `Dockerfile` | Alternative ways to start the server |
| `package.json` | App metadata + `npm start` → `node server.js` |
| `android/` | Android app source (Kotlin + Compose), Gradle wrapper 8.11.1 |
| `.github/workflows/android-apk.yml` | GitHub Actions: builds a signed APK on tag push |
| `android-apk.yml` | Legacy copy of the workflow at repo root (kept for reference) |
| `MuchiPlayer.kt` | Shared player logic reference used by the Android app |
| `ANDROID.md`, `UPLOAD-STEPS.md`, `README.md` | Build & upload documentation |

## Restore steps — GitHub

1. Go to https://github.com/new and create a new repository (name: `Muchi-music`).
2. Upload the contents of this zip into it (web upload, or in a terminal:
   `git init && git add . && git commit -m "restore" && git branch -M main &&
   git remote add origin https://github.com/<YOU>/Muchi-music.git && git push -u origin main`).
3. That's it — the repo is ready. No build step is required on Render.

## Restore steps — Render (free plan)

1. In Render dashboard: **New → Web Service → connect your GitHub repo**.
2. Render reads `render.yaml` automatically (Blueprint) — or configure manually:
   - **Runtime:** Node
   - **Build command:** `true` (nothing to build — zero npm dependencies)
   - **Start command:** `node server.js`
   - **Health check path:** `/api/health`
3. Optional environment variable:
   - `MUCHI_API_BASE` — set to `https://muchi-music.onrender.com` only if you
     want this new deployment to proxy another Muchi instance. If left empty the
     server talks to itself (recommended for a fresh deployment).
4. Deploy. The app listens on port 3000 (Render injects `PORT` automatically).

## Android APK builds (GitHub Actions)

The workflow signs the APK with secrets. After restoring, add these secrets in
**repo → Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `MUCHI_KEYSTORE_B64` | base64 of your `.jks` keystore file |
| `MUCHI_KEYSTORE_PASSWORD` | keystore password |
| `GOOGLE_WEB_CLIENT_ID` | Google OAuth web client ID (if Google sign-in is used) |

See `ANDROID.md` → Signing for full instructions. Push a tag (`git tag v1.2.1 && git push --tags`) to trigger the APK build.

## Important notes

- The web app talks directly to public Piped API instances and to this server's
  own `/api/*` endpoints — no third-party API keys required.
- The live production URL of the deployment is
  `https://muchi-music.onrender.com`.
- Full git history is on the GitHub repo
  (`Kaibshshdheueejw/Muchi-music`); this backup is a verified snapshot of the
  current code.
