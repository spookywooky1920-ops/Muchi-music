# Upload this preview to a new GitHub repo + Render

Use the zips in this folder. Do not drag a `.github` folder (GitHub’s web upload skips it).

## Files

| File | What it is |
|---|---|
| `Muchi-web-preview.zip` | Site + server (upload this first) |
| `android-apk.yml` | GitHub Action for the APK (add by path later) |
| `Muchi-native-1.3.1.zip` | Android source (upload later, after the site works) |

Keep `public/` as a **folder**. Do not flatten it.

---

## A. New GitHub repo

1. GitHub → **New repository**.
2. Name it (example: `Muchi-music`).
3. **Public**.
4. Do **not** add a README / .gitignore / license on create (empty repo).
5. Create.

## B. Upload the web app

1. Unzip `Muchi-web-preview.zip` on your computer.
2. On the new repo: **Add file → Upload files**.
3. Drop these so they sit at the **repo root**:
   - `public/` (folder)
   - `server.js`
   - `package.json`
   - `Dockerfile`
   - `Procfile`
   - `render.yaml`
   - `start.sh`
   - `.dockerignore`
   - `README.md`
   - `ANDROID.md`
4. Commit.

You should see `public/index.html` at `https://github.com/YOU/REPO/blob/main/public/index.html`.

## C. Workflow (only when you want an APK)

GitHub drag-and-drop **cannot** create `.github`.

1. Repo → **Add file → Create new file**.
2. Name it exactly: `.github/workflows/android-apk.yml`
3. Open `android-apk.yml` from this preview, put that content in, commit.

Do this **after** you also upload `android/` from `Muchi-native-1.3.1.zip`.

---

## D. Wire Render (this is why it said “no access”)

Render can only see repos you **grant**. A brand-new repo is not allowed until you tick it.

1. [Render Dashboard](https://dashboard.render.com) → sign in with GitHub.
2. **Account Settings → GitHub** (or New Web Service → Configure GitHub App).
3. **Grant access** to the **new** repo (or “All repositories”).
4. Save.

Then:

5. Render → **New → Web Service**.
6. Pick the **new** repo (not a fork, not the old Muchi-music unless you want that one).
7. Do **not** press a README “Deploy to Render” button.
8. Settings:
   - Runtime: **Node**
   - Build command: `true`
   - Start command: `node server.js`
   - Instance: Free
   - Health check path: `/api/health`
9. Environment:
   - `NODE_ENV` = `production`
   - `MUCHI_GITHUB` = `https://github.com/YOU/NEW-REPO`
10. Create Web Service. Wait until it is Live.
11. Open `https://YOUR-SERVICE.onrender.com/api/health` — should show `"ok": true`.

If it still says it cannot access the repo: GitHub → Settings → Applications → **Render** → Configure → include the new repo.

**Manual Deploy** later: Render service → **Manual Deploy → Deploy latest commit**.

---

## E. Android later

1. Unzip `Muchi-native-1.3.1.zip`.
2. Upload the `android/` folder to the same repo (keep it named `android`).
3. Add the workflow as in section C.
4. **Before the first run**, add two repo secrets (Settings → Secrets and variables → Actions):
   - `MUCHI_KEYSTORE_B64` — base64 of the signing keystore (see ANDROID.md → Signing)
   - `MUCHI_KEYSTORE_PASSWORD` — the keystore password
   The workflow will not run without them (it stops early with a clear message).
5. Actions → **Build Muchi APK** → Run workflow.
6. Install the APK from the run artifact or Releases.

Re-running the workflow is safe: it deletes the previous `v1.3.1` release/tag before publishing again.

The APK talks to the API base set in `public/app.js` (native default: `https://muchi-music.onrender.com`).

---

## Do not

- Flatten `public/` into the repo root.
- Upload `.github` as a dragged folder.
- Click **Deploy to Render** on the README.
- Point Render at a fork you did not mean to use.
