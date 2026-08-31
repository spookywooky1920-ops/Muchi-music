import type { CapacitorConfig } from "@capacitor/cli";

/**
 * MUCHI — shared Liquid Glass music app.
 *
 * ONE web codebase (public/) is served by:
 *   - the Node server (web), and
 *   - the Capacitor WebView (Android + iOS) via webDir: "public".
 *
 * The web app stays untouched; native shells add background playback,
 * lock-screen media controls, notifications, sharing and status-bar
 * integration. No Google Sign-In is wired up yet (auth is designed to
 * be added later without a rewrite).
 */
const config: CapacitorConfig = {
  appId: "app.muchi.music",
  appName: "Muchi",
  webDir: "public",

  // Match the app's Liquid Glass dark canvas.
  backgroundColor: "#101413",

  android: {
    // The app only talks to https endpoints (live API + YouTube).
    allowMixedContent: false,
  },

  ios: {
    // Content should never sit under the home indicator / status bar;
    // the web CSS uses env(safe-area-inset-*) for spacing.
    contentInset: "always",
    backgroundColor: "#101413",
  },

  plugins: {
    SplashScreen: {
      launchShowDuration: 900,
      launchAutoHide: true,
      backgroundColor: "#101413",
      androidSplashResourceName: "splash",
      androidScaleType: "CENTER_CROP",
      showSpinner: false,
      splashFullScreen: true,
      splashImmersive: true,
    },
    StatusBar: {
      overlaysWebView: false,
      style: "LIGHT",
      backgroundColor: "#101413",
    },
  },
};

export default config;
