# Continue Muchi

GitHub: https://github.com/Kaibshshdheueejw/Muchi-music-New
Deploy: Manual Deploy latest from that repo. Do not bump APP_VERSION (stay 1.2.1). SW is **v66**.

User deletes GitHub `public/` then reuploads unzipped `public/` + root `server.js`. Keep `android/` + `.github/`. Drag-drop skips `.github` — upload workflow via Add file path `.github/workflows/android-apk.yml`.

**Signing:** keystore is NOT in the repo anymore (rotated 2026-08). APK workflow needs GitHub secrets `MUCHI_KEYSTORE_B64` + `MUCHI_KEYSTORE_PASSWORD` or it fails early with instructions. New keystore + password + base64 live in `muchi-keystore-backup/` (outside repo, next to the repo root). Old `muchi.keystore` still in git history — history rewrite would be needed to scrub it. If the workflow's release step ever 422s on tag `v1.3.1`, it now auto-deletes the old release/tag first; if it still fails, delete release+tag manually.

**Optional env `MUCHI_API_BASE`:** when set (e.g. `MUCHI_API_BASE=https://muchi-music.onrender.com`), server.js injects `window.MUCHI_API_BASE` into served HTML and the frontend fetches `/api/*` from that origin instead of same-origin (also plays radio streams directly, no `/api/stream` proxy). Unset = default same-origin behavior. Used by the live preview; harmless in production if unset.

**LIVE Render URL = `https://muchi-music.onrender.com`** (the user's permanent deployment, confirmed live 2026-08-31). The old `https://muchi-music-ngd5.onrender.com` deployment is retired (health returns Not Found) — do not point the preview or the Android `API_BASE` at it.

## This zip (`Muchi-FULL-BACKUP-hq.zip`)
- Home genre shelves (Today's Top Hits / Pop / Hip-Hop / R&B / Rock / Dance & Electronic / Indie): **See all / title** opens catalog and fetches `/api/shelf?full=1` (up to 80). Preview songs show immediately; rest load in. Catalog row Play works. Hardware Back from catalog returns to previous view.
- Stream default **high**: YouTube official player `hd1080` / range hd720–highres; Audius still 320; radio prefers ≥192 kbps. No extraction.
- Queue, ⋮ sheets, option modals: **glass blur** even on phone (cards stay cheap after heat kill).
- Native: `openCatalog` + `/api/shelf?full=1`, CatalogScreen wired in MuchiRoot.

## Still required (user)
- Real notification player, continue when leaving app, continue when locked.
- Play Protect sideload warning is not fixed by Sign-In/keystore.
- Don’t extract YT/Spotify. Don’t README Deploy.
