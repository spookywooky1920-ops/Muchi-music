# Muchi 1.2.1

Free Material You music player. YouTube plays in Google’s official IFrame player. Independent artists stream from Audius. Live radio uses Radio Browser.

Muchi is not affiliated with YouTube, Google, Spotify, or Audius.

The web preview normally uses the Muchi API routes. If a host blocks server-side outbound requests, `public/direct-api.js` falls back to public catalog APIs in the browser, so music discovery does not depend on GitHub. YouTube search results still open in the official YouTube player; Audius and radio streams play directly.
