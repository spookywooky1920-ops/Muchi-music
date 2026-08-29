# Continue Muchi

GitHub: https://github.com/Kaibshshdheueejw/Muchi-music-New
Deploy: Manual Deploy latest from that repo. Do not bump APP_VERSION (stay 1.2.1). SW is **v66**.

User deletes GitHub `public/` then reuploads unzipped `public/` + root `server.js`. Keep `android/` + `.github/`. Drag-drop skips `.github` — upload workflow via Add file path `.github/workflows/android-apk.yml`.

## This zip (`Muchi-FULL-BACKUP-hq.zip`)
- Home genre shelves (Today's Top Hits / Pop / Hip-Hop / R&B / Rock / Dance & Electronic / Indie): **See all / title** opens catalog and fetches `/api/shelf?full=1` (up to 80). Preview songs show immediately; rest load in. Catalog row Play works. Hardware Back from catalog returns to previous view.
- Stream default **high**: YouTube official player `hd1080` / range hd720–highres; Audius still 320; radio prefers ≥192 kbps. No extraction.
- Queue, ⋮ sheets, option modals: **glass blur** even on phone (cards stay cheap after heat kill).
- Native: `openCatalog` + `/api/shelf?full=1`, CatalogScreen wired in MuchiRoot.

## Still required (user)
- Real notification player, continue when leaving app, continue when locked.
- Play Protect sideload warning is not fixed by Sign-In/keystore.
- Don’t extract YT/Spotify. Don’t README Deploy.
