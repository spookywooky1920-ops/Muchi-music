"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const { URL } = require("url");

const PORT = Number(process.env.PORT || 3000);
const HOST = "0.0.0.0";
const PUBLIC = path.join(__dirname, "public");
const APP_NAME = "Muchi";
const APP_VERSION = "1.2.1";

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".webp": "image/webp",
  ".woff2": "font/woff2",
  ".webmanifest": "application/manifest+json",
};

const cache = new Map();
const inflight = new Map();
function cached(key, ttlMs, fn) {
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < ttlMs) return Promise.resolve(hit.value);
  if (inflight.has(key)) return inflight.get(key);
  const p = fn()
    .then((value) => {
      cache.set(key, { at: Date.now(), value });
      inflight.delete(key);
      return value;
    })
    .catch((err) => {
      inflight.delete(key);
      throw err;
    });
  inflight.set(key, p);
  return p;
}

async function fetchJSON(url, opts = {}, timeoutMs = 14000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      ...opts,
      signal: ctrl.signal,
      headers: {
        Accept: "application/json",
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        ...(opts.headers || {}),
      },
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`HTTP ${res.status} ${url} ${text.slice(0, 120)}`);
    }
    return await res.json();
  } finally {
    clearTimeout(t);
  }
}

function send(res, status, body, headers = {}) {
  const data = typeof body === "string" || Buffer.isBuffer(body) ? body : JSON.stringify(body);
  res.writeHead(status, {
    "Cache-Control": "no-store",
    ...headers,
    "Content-Length": Buffer.byteLength(data),
  });
  res.end(data);
}

function sendJSON(res, status, obj) {
  send(res, status, JSON.stringify(obj), {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
  });
}

function parseDuration(text) {
  if (!text) return 0;
  if (typeof text === "number") return text;
  const parts = String(text)
    .trim()
    .split(":")
    .map((n) => parseInt(n, 10));
  if (parts.some((n) => Number.isNaN(n))) return 0;
  if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
  if (parts.length === 2) return parts[0] * 60 + parts[1];
  return parts[0] || 0;
}

function runsText(node) {
  if (!node) return "";
  if (typeof node === "string") return node;
  if (node.simpleText) return node.simpleText;
  if (Array.isArray(node.runs)) return node.runs.map((r) => r.text || "").join("");
  return "";
}

function extractVideoId(obj) {
  if (!obj || typeof obj !== "object") return "";
  if (obj.videoId) return obj.videoId;
  if (obj.playlistItemData && obj.playlistItemData.videoId) return obj.playlistItemData.videoId;
  const we =
    obj.watchEndpoint ||
    (obj.navigationEndpoint && obj.navigationEndpoint.watchEndpoint) ||
    (obj.playNavigationEndpoint && obj.playNavigationEndpoint.watchEndpoint);
  if (we && we.videoId) return we.videoId;
  return "";
}

function parseMusicItem(m) {
  const videoId =
    extractVideoId(m) ||
    extractVideoId(
      m.overlay &&
        m.overlay.musicItemThumbnailOverlayRenderer &&
        m.overlay.musicItemThumbnailOverlayRenderer.content &&
        m.overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer
    );
  if (!videoId) return null;

  const flex = m.flexColumns || [];
  const cols = flex.map((c) => {
    const t =
      c.musicResponsiveListItemFlexColumnRenderer &&
      c.musicResponsiveListItemFlexColumnRenderer.text;
    return {
      text: runsText(t),
      runs: (t && t.runs) || [],
    };
  });
  const title = (cols[0] && cols[0].text) || "Unknown";
  let artist = "";
  let album = "";
  let duration = 0;
  const TYPE = /^(song|video|album|ep|playlist|artist|single|official)$/i;
  if (cols[1]) {
    const parts = cols[1].text.split("•").map((s) => s.trim()).filter(Boolean);
    const meta = parts.filter((p) => !TYPE.test(p) && !/^\d+:\d+/.test(p) && !/plays$/i.test(p) && !/^\d+(\.\d+)?[kmb]?\s*plays$/i.test(p));
    artist = meta[0] || "";
    if (meta[1]) album = meta[1];
    const durPart = parts.find((p) => /^\d+:\d+/.test(p));
    if (durPart) duration = parseDuration(durPart);
  }
  const fixed = m.fixedColumns || [];
  for (const f of fixed) {
    const t =
      f.musicResponsiveListItemFixedColumnRenderer &&
      f.musicResponsiveListItemFixedColumnRenderer.text;
    const d = parseDuration(runsText(t));
    if (d) duration = d;
  }
  const thumbs =
    (m.thumbnail &&
      m.thumbnail.musicThumbnailRenderer &&
      m.thumbnail.musicThumbnailRenderer.thumbnail &&
      m.thumbnail.musicThumbnailRenderer.thumbnail.thumbnails) ||
    [];
  const artwork = thumbs.length ? thumbs[thumbs.length - 1].url : `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
  return {
    id: `yt:${videoId}`,
    source: "youtube",
    videoId,
    title,
    artist: artist || "YouTube",
    album,
    duration,
    artwork,
  };
}

function parseVideoRenderer(v) {
  if (!v || !v.videoId) return null;
  const title = runsText(v.title);
  const artist = runsText(v.ownerText || v.longBylineText || v.shortBylineText) || "YouTube";
  const duration = parseDuration(runsText(v.lengthText));
  const thumbs = (v.thumbnail && v.thumbnail.thumbnails) || [];
  return {
    id: `yt:${v.videoId}`,
    source: "youtube",
    videoId: v.videoId,
    title,
    artist,
    album: "",
    duration,
    artwork: thumbs.length ? thumbs[thumbs.length - 1].url : `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`,
  };
}

function isLikelyMusic(t, loose) {
  if (!t || !t.videoId) return false;
  const title = String(t.title || "");
  const artist = String(t.artist || "");
  const blob = `${title} ${artist}`.toLowerCase();
  if (/^(episode|podcast|clip|news|trailer)$/i.test(artist.trim())) return false;
  if (/\b(gameplay|walkthrough|playthrough|trailer|teaser|full movie|watch online|episode|season\s*\d|vlog|tutorial|how to|unboxing|reaction|highlights?|podcast|asmr|minecraft|fortnite|roblox|gta\s*5|documentary|full match|press conference|#shorts?)\b/i.test(blob)) return false;
  if (/\b(funny moments|best moments|try not to laugh|top 10|explained|imran khan|nato)\b/i.test(blob)) return false;
  if (loose) return true;
  const dur = Number(t.duration) || 0;
  if (dur && dur < 35) return false;
  if (dur && dur > 20 * 60 && !/\b(mix|album|playlist|compilation|concert|live|set|lofi|lo-fi)\b/i.test(blob)) return false;
  return true;
}

function walkCollect(node, out, seen, visiting = new Set(), opts = {}) {
  if (!node || typeof node !== "object") return;
  if (visiting.has(node)) return;
  visiting.add(node);
  const cap = Number(opts.limit) > 0 ? Number(opts.limit) : 40;
  if (out.length >= cap) return;
  if (Array.isArray(node)) {
    node.forEach((n) => walkCollect(n, out, seen, visiting, opts));
    return;
  }
  const keep = (item) => item && item.videoId && !seen.has(item.videoId) && isLikelyMusic(item, opts.loose);
  if (node.musicResponsiveListItemRenderer) {
    const item = parseMusicItem(node.musicResponsiveListItemRenderer);
    if (keep(item)) {
      seen.add(item.videoId);
      out.push(item);
    }
  }
  if (node.videoRenderer) {
    const item = parseVideoRenderer(node.videoRenderer);
    if (keep(item) && (!opts.musicOnly || opts.loose)) {
      seen.add(item.videoId);
      out.push(item);
    }
  }
  if (node.compactVideoRenderer) {
    const item = parseVideoRenderer(node.compactVideoRenderer);
    if (keep(item) && (!opts.musicOnly || opts.loose)) {
      seen.add(item.videoId);
      out.push(item);
    }
  }
  if (node.playlistVideoRenderer) {
    const v = node.playlistVideoRenderer;
    const item = parseVideoRenderer({
      videoId: v.videoId,
      title: v.title,
      shortBylineText: v.shortBylineText || v.ownerText,
      lengthText: v.lengthText,
      thumbnail: v.thumbnail,
    });
    if (keep(item)) {
      seen.add(item.videoId);
      out.push(item);
    }
  }
  if (node.playlistItemData && node.playlistItemData.videoId && node.flexColumns) {
    const item = parseMusicItem(node);
    if (keep(item)) {
      seen.add(item.videoId);
      out.push(item);
    }
  }
  for (const v of Object.values(node)) walkCollect(v, out, seen, visiting, opts);
}

function lastThumb(thumbs) {
  if (!thumbs || !thumbs.length) return "/cover-default.png";
  return thumbs[thumbs.length - 1].url || thumbs[0].url || "/cover-default.png";
}

function parseYtArtist(m) {
  if (!m) return null;
  const browse = (m.navigationEndpoint && m.navigationEndpoint.browseEndpoint) || {};
  const browseId = browse.browseId || "";
  const pageType =
    (browse.browseEndpointContextSupportedConfigs &&
      browse.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig &&
      browse.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType) ||
    "";
  const isArtist = pageType === "MUSIC_PAGE_TYPE_ARTIST" || /^UC/.test(browseId);
  if (!isArtist && !m.subscriberCountText) return null;
  const flex = m.flexColumns || [];
  const name =
    runsText(
      flex[0] &&
        flex[0].musicResponsiveListItemFlexColumnRenderer &&
        flex[0].musicResponsiveListItemFlexColumnRenderer.text
    ) ||
    runsText(m.title) ||
    "";
  if (!name) return null;
  const thumbs =
    (m.thumbnail &&
      m.thumbnail.musicThumbnailRenderer &&
      m.thumbnail.musicThumbnailRenderer.thumbnail &&
      m.thumbnail.musicThumbnailRenderer.thumbnail.thumbnails) ||
    (m.thumbnail && m.thumbnail.thumbnails) ||
    [];
  return {
    id: `artist:${browseId || name}`,
    kind: "artist",
    name,
    artwork: lastThumb(thumbs),
    source: "youtube",
    query: name,
  };
}

function parseYtPlaylist(node) {
  if (!node || typeof node !== "object") return null;
  const p = node.playlistRenderer || node;
  const m = node.musicTwoRowItemRenderer || node.musicResponsiveListItemRenderer || null;
  let playlistId = p.playlistId || "";
  const nav =
    p.navigationEndpoint ||
    (m && m.navigationEndpoint) ||
    {};
  if (!playlistId && nav.watchEndpoint && nav.watchEndpoint.playlistId) playlistId = nav.watchEndpoint.playlistId;
  if (!playlistId && nav.browseEndpoint && String(nav.browseEndpoint.browseId || "").startsWith("VL")) {
    playlistId = String(nav.browseEndpoint.browseId).slice(2);
  }
  if (!playlistId) return null;
  const title =
    runsText(p.title) ||
    runsText(m && m.title) ||
    (m &&
      m.flexColumns &&
      runsText(
        m.flexColumns[0] &&
          m.flexColumns[0].musicResponsiveListItemFlexColumnRenderer &&
          m.flexColumns[0].musicResponsiveListItemFlexColumnRenderer.text
      )) ||
    "Playlist";
  const thumbs =
    (p.thumbnails && p.thumbnails[0] && p.thumbnails[0].thumbnails) ||
    (p.thumbnail && p.thumbnail.thumbnails) ||
    (m &&
      m.thumbnailRenderer &&
      m.thumbnailRenderer.musicThumbnailRenderer &&
      m.thumbnailRenderer.musicThumbnailRenderer.thumbnail &&
      m.thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails) ||
    (m && m.thumbnail && m.thumbnail.musicThumbnailRenderer && m.thumbnail.musicThumbnailRenderer.thumbnail && m.thumbnail.musicThumbnailRenderer.thumbnail.thumbnails) ||
    [];
  return {
    id: `ytpl:${playlistId}`,
    kind: "playlist",
    playlistId,
    title,
    artist: runsText(p.shortBylineText || p.longBylineText) || "YouTube",
    artwork: lastThumb(thumbs),
    source: "youtube",
    videoCount: p.videoCount || 0,
  };
}

function walkCatalog(node, bag, visiting = new Set()) {
  if (!node || typeof node !== "object") return;
  if (visiting.has(node)) return;
  visiting.add(node);
  if ((bag.artists.length >= 16 && bag.playlists.length >= 16)) return;
  if (Array.isArray(node)) {
    node.forEach((n) => walkCatalog(n, bag, visiting));
    return;
  }
  if (node.channelRenderer) {
    const c = node.channelRenderer;
    const name = runsText(c.title);
    if (name && bag.artists.length < 16 && !bag.seenArt.has(name.toLowerCase())) {
      bag.seenArt.add(name.toLowerCase());
      bag.artists.push({
        id: `artist:${c.channelId || name}`,
        kind: "artist",
        name,
        artwork: lastThumb(c.thumbnail && c.thumbnail.thumbnails),
        source: "youtube",
        query: name,
      });
    }
  }
  if (node.playlistRenderer) {
    const pl = parseYtPlaylist(node);
    if (pl && bag.playlists.length < 16 && !bag.seenPl.has(pl.playlistId)) {
      bag.seenPl.add(pl.playlistId);
      bag.playlists.push(pl);
    }
  }
  if (node.musicTwoRowItemRenderer) {
    const pl = parseYtPlaylist({ musicTwoRowItemRenderer: node.musicTwoRowItemRenderer });
    if (pl && bag.playlists.length < 16 && !bag.seenPl.has(pl.playlistId)) {
      bag.seenPl.add(pl.playlistId);
      bag.playlists.push(pl);
    }
    const art = parseYtArtist(node.musicTwoRowItemRenderer);
    if (art && bag.artists.length < 16 && !bag.seenArt.has(art.name.toLowerCase())) {
      bag.seenArt.add(art.name.toLowerCase());
      bag.artists.push(art);
    }
  }
  if (node.musicResponsiveListItemRenderer) {
    const m = node.musicResponsiveListItemRenderer;
    const art = parseYtArtist(m);
    if (art && bag.artists.length < 16 && !bag.seenArt.has(art.name.toLowerCase())) {
      bag.seenArt.add(art.name.toLowerCase());
      bag.artists.push(art);
    }
    const pl = parseYtPlaylist({ musicResponsiveListItemRenderer: m });
    if (pl && bag.playlists.length < 16 && !bag.seenPl.has(pl.playlistId)) {
      bag.seenPl.add(pl.playlistId);
      bag.playlists.push(pl);
    }
  }
  for (const v of Object.values(node)) walkCatalog(v, bag, visiting);
}

function regionCode(raw) {
  const gl = String(raw || "IN").toUpperCase();
  return /^[A-Z]{2}$/.test(gl) ? gl : "IN";
}

const LOCAL_CHARTS = {
  IN: "bollywood hits official",
  US: "top 40 usa official audio",
  GB: "uk top 40 official audio",
  CA: "canada top hits official",
  AU: "australia top hits official",
  DE: "deutsche charts official",
  FR: "france top hits official",
  JP: "jpop hits official",
  KR: "kpop hits official audio",
  BR: "brazil top hits official",
  MX: "mexico hits official",
  NG: "afrobeats hits official",
  ZA: "south africa amapiano hits",
  AE: "arabic hits official",
  SA: "khaleeji hits official",
  PK: "pakistan hits official",
  BD: "bangla hits official",
  ID: "indonesia hits official",
  MY: "malaysia hits official",
  SG: "singapore hits official",
  PH: "opm hits official",
  TH: "thai hits official",
  VN: "vpop hits official",
  EG: "egypt hits official",
  IT: "italy hits official",
  ES: "spain hits official",
  TR: "turkce pop hits official",
};

const YT_SONGS_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA==";

function searchWalkOpts(extra, musicOnly) {
  return {
    musicOnly: musicOnly && !extra.loose,
    limit: extra.limit || 40,
    loose: !!extra.loose,
  };
}

async function youtubeMusicSearch(query, gl, timeoutMs = 6500, extra = {}) {
  const payload = {
    context: {
      client: {
        clientName: "WEB_REMIX",
        clientVersion: "1.20240814.01.00",
        hl: "en",
        gl: regionCode(gl),
      },
    },
    query,
  };
  if (extra.params) payload.params = extra.params;
  const data = await fetchJSON("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Origin: "https://music.youtube.com",
      Referer: "https://music.youtube.com/",
    },
    body: JSON.stringify(payload),
  }, timeoutMs);
  const out = [];
  walkCollect(data, out, new Set(), new Set(), searchWalkOpts(extra, true));
  const bag = { artists: [], playlists: [], seenPl: new Set(), seenArt: new Set() };
  walkCatalog(data, bag);
  return { tracks: out, artists: bag.artists, playlists: bag.playlists };
}

async function youtubeWebSearch(query, gl, timeoutMs = 6500, extra = {}) {
  const body = JSON.stringify({
    context: {
      client: {
        clientName: "WEB",
        clientVersion: "2.20240815.00.00",
        hl: "en",
        gl: regionCode(gl),
      },
    },
    query,
  });
  const data = await fetchJSON("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Origin: "https://www.youtube.com",
      Referer: "https://www.youtube.com/",
    },
    body,
  }, timeoutMs);
  const out = [];
  walkCollect(data, out, new Set(), new Set(), searchWalkOpts(extra, false));
  const bag = { artists: [], playlists: [], seenPl: new Set(), seenArt: new Set() };
  walkCatalog(data, bag);
  return { tracks: out, artists: bag.artists, playlists: bag.playlists };
}

async function pipedSearch(query) {
  const data = await fetchJSON(
    `https://api.piped.private.coffee/search?q=${encodeURIComponent(query)}&filter=all`
  );
  const items = data.items || data || [];
  return items
    .filter((it) => it.type === "stream" || it.url)
    .map((it) => {
      const videoId = (it.url || "").split("v=")[1] || (it.url || "").replace("/watch?v=", "").split("&")[0];
      if (!videoId) return null;
      return {
        id: `yt:${videoId}`,
        source: "youtube",
        videoId,
        title: it.title || "YouTube",
        artist: it.uploaderName || it.uploader || "YouTube",
        album: "",
        duration: it.duration || 0,
        artwork: (it.thumbnail || "").replace("proxy.piped.private.coffee/vi/", "i.ytimg.com/vi/") ||
          `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
      };
    })
    .filter(Boolean);
}

async function searchYouTube(query, gl, fast) {
  const seen = new Set();
  const out = [];
  const artists = [];
  const playlists = [];
  const seenArt = new Set();
  const seenPl = new Set();
  const add = (bundle) => {
    const rows = Array.isArray(bundle) ? bundle : (bundle && bundle.tracks) || [];
    for (const r of rows || []) {
      if (r && r.videoId && !seen.has(r.videoId)) {
        seen.add(r.videoId);
        out.push(r);
      }
    }
    for (const a of (bundle && bundle.artists) || []) {
      const k = String(a.name || "").toLowerCase();
      if (k && !seenArt.has(k)) {
        seenArt.add(k);
        artists.push(a);
      }
    }
    for (const p of (bundle && bundle.playlists) || []) {
      if (p.playlistId && !seenPl.has(p.playlistId)) {
        seenPl.add(p.playlistId);
        playlists.push(p);
      }
    }
  };
  const errors = [];
  const extra = { limit: fast ? 24 : 120, loose: !fast };
  const jobs = fast
    ? [youtubeMusicSearch(query, gl, 6000, extra)]
    : [
        youtubeMusicSearch(query, gl, 6500, { ...extra, params: YT_SONGS_PARAMS }),
        youtubeMusicSearch(query, gl, 6500, extra),
        youtubeWebSearch(query, gl, 6500, extra),
      ];
  const settled = await Promise.allSettled(jobs);
  for (const s of settled) {
    if (s.status === "fulfilled") add(s.value);
    else errors.push(String(s.reason && s.reason.message ? s.reason.message : s.reason));
  }
  if (!out.length) {
    try {
      add(await pipedSearch(query));
    } catch (e) {
      errors.push(String(e.message || e));
    }
  }
  if (!out.length) throw new Error(errors.join(" | ") || "YouTube search failed");
  const qn = String(query || "").toLowerCase().trim();
  const words = qn.split(/\s+/).filter((w) => w.length > 1);
  const score = (t) => {
    const title = String(t.title || "").toLowerCase();
    const artist = String(t.artist || "").toLowerCase();
    if (!qn) return 0;
    if (title === qn) return 200;
    if (title.includes(qn)) return 120;
    if (`${title} ${artist}`.includes(qn)) return 90;
    let s = 0;
    for (const w of words) {
      if (title.includes(w)) s += 18;
      if (artist.includes(w)) s += 10;
    }
    return s;
  };
  out.sort((a, b) => score(b) - score(a));
  const tracks = out.slice(0, fast ? 24 : 100);
  tracks.artists = artists.slice(0, 24);
  tracks.playlists = playlists.slice(0, 24);
  return tracks;
}

function mapAudiusTrack(t) {
  if (!t || !t.id) return null;
  const user = t.user || {};
  const art = t.artwork || {};
  return {
    id: `audius:${t.id}`,
    source: "audius",
    trackId: t.id,
    title: t.title || "Untitled",
    artist: user.name || user.handle || (t.permalink || "").split("/")[1] || "Independent artist",
    album: t.genre || "Audius",
    duration: t.duration || 0,
    artwork: art["480x480"] || art["1000x1000"] || art["150x150"] || "/cover-default.png",
    genre: t.genre || "",
    mood: t.mood || "",
    plays: t.play_count || 0,
    permalink: t.permalink || "",
    streamUrl: (t.stream && t.stream.url) || "",
  };
}

async function itunesSearch(query) {
  const q = encodeURIComponent(query);
  const [songsR, artistsR, albumsR] = await Promise.allSettled([
    fetchJSON(`https://itunes.apple.com/search?term=${q}&media=music&entity=song&limit=50`, {}, 6000),
    fetchJSON(`https://itunes.apple.com/search?term=${q}&media=music&entity=musicArtist&limit=20`, {}, 6000),
    fetchJSON(`https://itunes.apple.com/search?term=${q}&media=music&entity=album&limit=25`, {}, 6000),
  ]);
  const songs = [];
  const artists = [];
  const playlists = [];
  if (songsR.status === "fulfilled") {
    for (const t of (songsR.value && songsR.value.results) || []) {
      if (!t.trackId) continue;
      songs.push({
        id: `apple:${t.trackId}`,
        source: "apple",
        title: t.trackName || "Song",
        artist: t.artistName || "Artist",
        album: t.collectionName || "",
        duration: Math.round((t.trackTimeMillis || 0) / 1000),
        artwork: String(t.artworkUrl100 || "").replace("100x100bb", "400x400bb") || "/cover-default.png",
        playQuery: `${t.trackName || ""} ${t.artistName || ""} official audio`.trim(),
      });
    }
  }
  if (artistsR.status === "fulfilled") {
    for (const a of (artistsR.value && artistsR.value.results) || []) {
      if (!a.artistName) continue;
      artists.push({
        id: `artist:apple:${a.artistId || a.artistName}`,
        kind: "artist",
        name: a.artistName,
        artwork: a.artworkUrl100 || "/cover-default.png",
        source: "apple",
        query: a.artistName,
      });
    }
  }
  if (albumsR.status === "fulfilled") {
    for (const al of (albumsR.value && albumsR.value.results) || []) {
      if (!al.collectionId) continue;
      playlists.push({
        id: `album:${al.collectionId}`,
        kind: "playlist",
        title: al.collectionName || "Album",
        artist: al.artistName || "Apple Music",
        artwork: String(al.artworkUrl100 || "").replace("100x100bb", "400x400bb") || "/cover-default.png",
        source: "apple",
        query: `${al.collectionName || ""} ${al.artistName || ""}`.trim(),
      });
    }
  }
  return { songs, artists, playlists };
}

async function youtubePlaylistTracks(playlistId) {
  const id = String(playlistId || "").replace(/^VL/, "");
  if (!id) return [];
  async function browse(clientName, clientVersion, origin) {
    const data = await fetchJSON(`${origin}/youtubei/v1/browse?prettyPrint=false`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Origin: origin,
        Referer: `${origin}/`,
      },
      body: JSON.stringify({
        context: { client: { clientName, clientVersion, hl: "en" } },
        browseId: `VL${id}`,
      }),
    });
    const out = [];
    walkCollect(data, out, new Set(), new Set(), { limit: 120, loose: true });
    return out;
  }
  try {
    const web = await browse("WEB", "2.20240815.00.00", "https://www.youtube.com");
    if (web.length) return web.slice(0, 100);
  } catch {}
  try {
    const remix = await browse("WEB_REMIX", "1.20240814.01.00", "https://music.youtube.com");
    return remix.slice(0, 100);
  } catch {
    return [];
  }
}

async function audiusSearch(query) {
  const data = await fetchJSON(
    `https://api.audius.co/v1/tracks/search?query=${encodeURIComponent(query)}&app_name=${APP_NAME}&limit=50`,
    {},
    6000
  );
  return (data.data || []).map(mapAudiusTrack).filter(Boolean);
}

async function audiusTrending(genre) {
  const qs = new URLSearchParams({ app_name: APP_NAME, limit: "24" });
  if (genre) qs.set("genre", genre);
  const data = await fetchJSON(`https://api.audius.co/v1/tracks/trending?${qs}`);
  return (data.data || []).map(mapAudiusTrack).filter(Boolean);
}

async function audiusUnderground() {
  const data = await fetchJSON(
    `https://api.audius.co/v1/tracks/trending/underground?app_name=${APP_NAME}&limit=18`
  );
  return (data.data || []).map(mapAudiusTrack).filter(Boolean);
}

function codecMatch(raw, want) {
  const c = String(raw || "").toLowerCase();
  if (!want || want === "auto") return true;
  if (want === "mp3") return /mp3|mpeg/.test(c);
  if (want === "aac") return /aac/.test(c);
  if (want === "opus") return /opus|ogg|vorbis/.test(c);
  return true;
}

const RADIO_HOSTS = [
  "https://de1.api.radio-browser.info",
  "https://fi1.api.radio-browser.info",
  "https://at1.api.radio-browser.info",
  "https://nl1.api.radio-browser.info",
];

async function radioBrowser(path, extraHeaders = {}) {
  const attempts = RADIO_HOSTS.map((host) =>
    fetchJSON(`${host}${path}`, {
      headers: { "User-Agent": `${APP_NAME}/${APP_VERSION}`, ...extraHeaders },
    }, 3500)
  );
  try {
    return await Promise.any(attempts);
  } catch {
    throw new Error("radio directory failed");
  }
}

async function audiusStreamUrl(trackId) {
  const id = encodeURIComponent(String(trackId || "").replace(/[^\w-]/g, ""));
  if (!id) throw new Error("bad track");
  try {
    const data = await fetchJSON(`https://api.audius.co/v1/tracks/${id}?app_name=${APP_NAME}`);
    const t = (data && data.data) || {};
    if (t.stream && t.stream.url) return t.stream.url;
  } catch {}
  return `https://api.audius.co/v1/tracks/${id}/stream?app_name=${APP_NAME}`;
}

async function pipeUrl(req, res, src, accept) {
  if (!/^https?:\/\//i.test(src)) return sendJSON(res, 400, { error: "bad url" });
  const ctrl = new AbortController();
  const onClose = () => ctrl.abort();
  req.on("close", onClose);
  const timer = setTimeout(() => ctrl.abort(), 45000);
  try {
    const r = await fetch(src, {
      signal: ctrl.signal,
      redirect: "follow",
      headers: {
        "User-Agent": `${APP_NAME}/${APP_VERSION}`,
        Accept: accept || "*/*",
        "Icy-MetaData": "1",
      },
    });
    if (!r.ok || !r.body) return sendJSON(res, r.status || 502, { error: "stream failed" });
    const ct = r.headers.get("content-type") || "application/octet-stream";
    res.writeHead(200, {
      "Content-Type": ct.split(";")[0],
      "Cache-Control": "no-store",
      "Access-Control-Allow-Origin": "*",
    });
    const reader = r.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!res.write(Buffer.from(value))) await new Promise((ok) => res.once("drain", ok));
    }
    res.end();
  } catch (e) {
    if (!res.headersSent) sendJSON(res, 502, { error: e.message || "stream error" });
    else try { res.end(); } catch {}
  } finally {
    clearTimeout(timer);
    req.off("close", onClose);
  }
}

async function radioSearch(query, limit = 24, quality, codec) {
  const params = new URLSearchParams({
    limit: String(Math.max(limit * 3, 24)),
    hidebroken: "true",
    order: query ? "votes" : "clickcount",
    reverse: "true",
    lastcheckok: "true",
  });
  if (query) params.set("name", query);
  if (quality === "low") params.set("bitrateMax", "96");
  if (quality === "high") params.set("bitrateMin", "192");
  const data = await radioBrowser(`/json/stations/search?${params}`);
  let rows = (data || []).filter(
    (s) => s.url_resolved && Number(s.hls) !== 1 && !/\.m3u8(\?|$)/i.test(s.url_resolved)
  );
  if (quality === "low") rows = rows.filter((s) => !s.bitrate || Number(s.bitrate) <= 96);
  if (quality === "high") {
    rows.sort((a, b) => Number(b.bitrate || 0) - Number(a.bitrate || 0));
  }
  const want = String(codec || "auto").toLowerCase();
  if (want !== "auto") rows = rows.filter((s) => codecMatch(s.codec, want));
  return rows.slice(0, limit)
    .map((s) => ({
      id: `radio:${s.stationuuid}`,
      source: "radio",
      stationId: s.stationuuid,
      title: (s.name || "Radio").trim(),
      artist: [s.country, s.tags].filter(Boolean).join(" · ") || "Live radio",
      album: s.codec || "Radio",
      duration: 0,
      artwork: s.favicon || "/cover-default.png",
      streamUrl: s.url_resolved,
      homepage: s.homepage || "",
      bitrate: s.bitrate || 0,
      codec: s.codec || "",
    }));
}

async function audiusUserSearch(query) {
  const data = await fetchJSON(
    `https://api.audius.co/v1/users/search?query=${encodeURIComponent(query)}&app_name=${APP_NAME}&limit=8`
  );
  return (data.data || []).map((u) => ({
    id: u.id,
    handle: u.handle,
    name: u.name || u.handle,
    artwork: (u.profile_picture && (u.profile_picture["480x480"] || u.profile_picture["150x150"])) || "/cover-default.png",
    followerCount: u.follower_count || 0,
  }));
}

async function audiusUserTracks(userId) {
  const data = await fetchJSON(
    `https://api.audius.co/v1/users/${encodeURIComponent(userId)}/tracks?app_name=${APP_NAME}&limit=12`
  );
  return (data.data || []).map(mapAudiusTrack).filter(Boolean);
}

function tidyTitle(title) {
  return String(title || "")
    .replace(/\s*[\[(][^)\]]*(official|audio|video|lyric|visualizer|hd|4k|remaster|topic)[^)\]]*[)\]]/gi, "")
    .replace(/\s*[-–—]\s*(official|audio|lyrics?|video).*$/i, "")
    .replace(/\b(official audio|official video|lyrics? video|visualizer|audio only)\b/gi, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}
function tidyArtist(artist) {
  let s = String(artist || "").split("·")[0].split("|")[0].split(",")[0];
  s = s.replace(/\s*-\s*Topic$/i, "").replace(/VEVO/ig, "").trim();
  if (/^(youtube|various artists|unknown)$/i.test(s)) return "";
  return s;
}
function parseLyricsHit(hit) {
  if (!hit) return null;
  const synced = [];
  if (hit.syncedLyrics) {
    for (const line of String(hit.syncedLyrics).split("\n")) {
      const m = line.match(/\[(\d+):(\d+(?:\.\d+)?)\](.*)/);
      if (m) synced.push({ t: Number(m[1]) * 60 + Number(m[2]), text: m[3].trim() });
    }
  }
  const lyrics = hit.plainLyrics || "";
  if (!lyrics && !synced.length) return null;
  return { lyrics, synced, title: hit.trackName, artist: hit.artistName };
}
async function lyricsFor(title, artist) {
  const t = tidyTitle(title);
  const a = tidyArtist(artist);
  const tries = [];
  if (a && t) {
    tries.push(`https://lrclib.net/api/get?artist_name=${encodeURIComponent(a)}&track_name=${encodeURIComponent(t)}`);
  }
  const q = [a, t].filter(Boolean).join(" ");
  if (q) tries.push(`https://lrclib.net/api/search?q=${encodeURIComponent(q)}`);
  if (t) tries.push(`https://lrclib.net/api/search?track_name=${encodeURIComponent(t)}&artist_name=${encodeURIComponent(a)}`);
  for (const url of tries) {
    try {
      const data = await fetchJSON(url, {}, 10000);
      if (Array.isArray(data)) {
        const hit = data.find((x) => x.plainLyrics || x.syncedLyrics) || data[0];
        const parsed = parseLyricsHit(hit);
        if (parsed) return parsed;
      } else {
        const parsed = parseLyricsHit(data);
        if (parsed) return parsed;
      }
    } catch {}
  }
  return { lyrics: "", synced: [] };
}

const MOOD_CORE = [
  { id: "pop", title: "Pop", query: "english pop hits official audio", color: "#90e0ef", tags: "pop english hits" },
  { id: "hiphop", title: "Hip-Hop", query: "hip hop rap official audio", color: "#e9c46a", tags: "hiphop rap" },
  { id: "rnb", title: "R&B", query: "rnb soul hits official audio", color: "#c084fc", tags: "rnb soul" },
  { id: "rock", title: "Rock", query: "rock hits official audio", color: "#fb7185", tags: "rock alternative" },
  { id: "dance", title: "Dance", query: "edm dance hits official audio", color: "#4cc9f0", tags: "edm dance electronic" },
  { id: "indie", title: "Indie", query: "indie pop alternative official audio", color: "#80ed99", tags: "indie alternative" },
  { id: "lofi", title: "Lo-fi Focus", query: "lofi hip hop beats to relax", color: "#7c6cff", tags: "lofi chill study" },
  { id: "workout", title: "Workout", query: "workout gym motivation songs", color: "#c8f542", tags: "workout gym rap edm" },
  { id: "romance", title: "Late Night Love", query: "romantic english songs official audio", color: "#ff6b9d", tags: "romance love slow" },
  { id: "latin", title: "Latin", query: "latin hits official audio", color: "#f59e0b", tags: "latin reggaeton" },
];

const ENGLISH_SHELVES = [
  { id: "today", title: "Today's Top Hits", query: "billboard hot 100 official audio" },
  { id: "pop", title: "Pop", query: "english pop hits official audio" },
  { id: "hiphop", title: "Hip-Hop", query: "hip hop rap hits official audio" },
  { id: "rnb", title: "R&B", query: "rnb soul hits official audio" },
  { id: "rock", title: "Rock", query: "classic and new rock hits official audio" },
  { id: "dance", title: "Dance & Electronic", query: "edm dance hits official audio" },
  { id: "indie", title: "Indie", query: "indie pop alternative official audio" },
];

const MOODS_BY_COUNTRY = {
  IN: [
    { id: "bollywood", title: "Bollywood Gold", query: "best bollywood songs official", color: "#ff4d6d", tags: "bollywood hindi arijit" },
    { id: "punjabi", title: "Punjabi Heat", query: "punjabi hits official audio", color: "#ffb703", tags: "punjabi sidhu diljit" },
    { id: "tamil", title: "Kollywood", query: "tamil hits official", color: "#fb8500", tags: "tamil kollywood" },
    { id: "telugu", title: "Tollywood", query: "telugu hits official", color: "#ff6b35", tags: "telugu tollywood" },
    { id: "indie", title: "Indie India", query: "indian indie songs", color: "#4cc9f0", tags: "indie india" },
    { id: "romance-in", title: "Hindi Romance", query: "romantic hindi songs arijit singh", color: "#ff6b9d", tags: "romance hindi arijit" },
  ],
  PK: [
    { id: "pakistan", title: "Pakistani Hits", query: "pakistan hits official", color: "#22c55e", tags: "pakistan urdu" },
    { id: "qawwali", title: "Qawwali", query: "qawwali nusrat official", color: "#a78bfa", tags: "qawwali sufi" },
  ],
  BD: [{ id: "bangla", title: "Bangla Hits", query: "bangla hits official", color: "#22c55e", tags: "bangla bangladesh" }],
  US: [
    { id: "us-pop", title: "US Pop", query: "usa top 40 official audio", color: "#60a5fa", tags: "pop usa" },
    { id: "rnb", title: "R&B", query: "rnb hits official audio", color: "#c084fc", tags: "rnb soul" },
    { id: "country", title: "Country", query: "country hits official audio", color: "#f59e0b", tags: "country" },
    { id: "latin-us", title: "Latin", query: "latin hits official audio", color: "#fb7185", tags: "latin reggaeton" },
  ],
  GB: [
    { id: "uk-pop", title: "UK Hits", query: "uk top 40 official audio", color: "#818cf8", tags: "uk pop" },
    { id: "drill", title: "UK Drill", query: "uk drill official audio", color: "#64748b", tags: "drill grime uk" },
  ],
  KR: [
    { id: "kpop", title: "K-Pop", query: "kpop hits official audio", color: "#f72585", tags: "kpop korea" },
    { id: "krnb", title: "K-R&B", query: "k rnb official audio", color: "#c084fc", tags: "krnb kpop" },
  ],
  JP: [
    { id: "jpop", title: "J-Pop", query: "jpop hits official", color: "#fb7185", tags: "jpop japan" },
    { id: "anime", title: "Anime", query: "anime openings official", color: "#38bdf8", tags: "anime jpop" },
  ],
  NG: [{ id: "afrobeats", title: "Afrobeats", query: "afrobeats hits official", color: "#f59e0b", tags: "afrobeats nigeria" }],
  ZA: [{ id: "amapiano", title: "Amapiano", query: "amapiano hits official", color: "#84cc16", tags: "amapiano south africa" }],
  BR: [
    { id: "brazil", title: "Brazil Hits", query: "brazil top hits official", color: "#22c55e", tags: "brazil funk" },
    { id: "sertanejo", title: "Sertanejo", query: "sertanejo oficial", color: "#eab308", tags: "sertanejo brazil" },
  ],
  MX: [{ id: "mexico", title: "México", query: "mexico hits official", color: "#f97316", tags: "mexico latin regional" }],
  DE: [{ id: "german", title: "German Hits", query: "deutsche charts official", color: "#fbbf24", tags: "german pop" }],
  FR: [{ id: "french", title: "French Hits", query: "france top hits official", color: "#60a5fa", tags: "french pop" }],
  TR: [{ id: "turkish", title: "Türkçe Pop", query: "turkce pop hits official", color: "#ef4444", tags: "turkish pop" }],
  PH: [{ id: "opm", title: "OPM", query: "opm hits official", color: "#22d3ee", tags: "opm philippines" }],
  TH: [{ id: "tpop", title: "T-Pop", query: "thai hits official", color: "#f472b6", tags: "thai tpop" }],
  VN: [{ id: "vpop", title: "V-Pop", query: "vpop hits official", color: "#34d399", tags: "vpop vietnam" }],
  ID: [{ id: "indo", title: "Indonesia", query: "indonesia hits official", color: "#fb7185", tags: "indonesia pop" }],
  AE: [{ id: "arabic", title: "Arabic Hits", query: "arabic hits official", color: "#fbbf24", tags: "arabic khaleeji" }],
  SA: [{ id: "khaleeji", title: "Khaleeji", query: "khaleeji hits official", color: "#22c55e", tags: "arabic khaleeji" }],
  EG: [{ id: "egypt", title: "Egypt Hits", query: "egypt hits official", color: "#f59e0b", tags: "arabic egypt" }],
  IT: [{ id: "italian", title: "Italia", query: "italy hits official", color: "#22c55e", tags: "italian pop" }],
  ES: [{ id: "spain", title: "España", query: "spain hits official", color: "#f97316", tags: "spanish latin" }],
};

function utcDay() {
  return new Date().toISOString().slice(0, 10);
}
function playlistsOf(v) {
  if (!v) return [];
  if (Array.isArray(v.playlists)) return v.playlists;
  if (Array.isArray(v) && Array.isArray(v.playlists)) return v.playlists;
  return [];
}
function uniqPlaylists(list) {
  const seen = new Set();
  const out = [];
  for (const p of list || []) {
    const k = String((p && (p.playlistId || p.id || p.title)) || "").toLowerCase();
    if (!k || seen.has(k)) continue;
    seen.add(k);
    out.push(p);
  }
  return out;
}

function moodsForCountry(gl) {
  const local = (MOODS_BY_COUNTRY[gl] || []).slice(0, 2);
  const seen = new Set();
  const out = [];
  for (const m of [...MOOD_CORE, ...local]) {
    if (seen.has(m.id)) continue;
    seen.add(m.id);
    out.push(m);
  }
  return out.slice(0, 12);
}

async function handleApi(req, res, url) {
  const p = url.pathname;
  const q = url.searchParams.get("q") || url.searchParams.get("query") || "";

  if (p === "/api/health" || p === "/api/version") {
    return sendJSON(res, 200, {
      ok: true,
      name: APP_NAME,
      version: APP_VERSION,
      time: new Date().toISOString(),
      github: process.env.MUCHI_GITHUB || "",
    });
  }

  if (p === "/api/moods") {
    const gl = regionCode(url.searchParams.get("gl"));
    return sendJSON(res, 200, { country: gl, moods: moodsForCountry(gl) });
  }

  if (p === "/api/home") {
    const gl = regionCode(url.searchParams.get("gl"));
    const localQ = LOCAL_CHARTS[gl] || "top hits official audio";
    const take = (r) => (r.status === "fulfilled" ? r.value : []);
    let globalPart = { shelves: [], globalPlaylists: [], audius: [], underground: [], radio: [] };
    let localPart = { youtubeLocal: [], countryPlaylists: [] };
    try {
      globalPart = await cached(`home:english:v5:${utcDay()}`, 86400000, async () => {
        const prime = ENGLISH_SHELVES.slice(0, 2);
        const jobs = prime.map((s) => searchYouTube(s.query, "US", true));
        const extra = await Promise.allSettled([
          ...jobs,
          youtubeMusicSearch("global top hits playlist", "US", 6000, { limit: 40 }),
          audiusTrending(),
          audiusUnderground(),
          radioSearch("hits", 16),
        ]);
        const filled = prime.map((s, i) => take(extra[i]).slice(0, 18));
        const shelves = ENGLISH_SHELVES.map((s, i) => ({
          id: s.id,
          title: s.title,
          query: s.query,
          tracks: i < filled.length ? filled[i] : [],
        }));
        const globalPlaylists = uniqPlaylists([
          ...playlistsOf(extra[0].status === "fulfilled" ? extra[0].value : []),
          ...playlistsOf(extra[1].status === "fulfilled" ? extra[1].value : []),
          ...playlistsOf(extra[2].status === "fulfilled" ? extra[2].value : []),
        ]).slice(0, 16);
        return {
          shelves,
          globalPlaylists,
          audius: take(extra[jobs.length + 1]).slice(0, 18),
          underground: take(extra[jobs.length + 2]).slice(0, 12),
          radio: take(extra[jobs.length + 3]).slice(0, 12),
        };
      });
    } catch (e) {
      console.error("home english", e);
      globalPart.shelves = ENGLISH_SHELVES.map((s) => ({ id: s.id, title: s.title, query: s.query, tracks: [] }));
    }
    try {
      localPart = await cached(`home:local:${gl}:v5:${utcDay()}`, 86400000, async () => {
        const [ytLocal, ytPl] = await Promise.allSettled([
          searchYouTube(localQ, gl, true),
          youtubeMusicSearch(`${localQ} playlist`, gl, 6000, { limit: 40 }),
        ]);
        return {
          youtubeLocal: take(ytLocal).slice(0, 18),
          countryPlaylists: uniqPlaylists([
            ...playlistsOf(ytLocal.status === "fulfilled" ? ytLocal.value : []),
            ...playlistsOf(ytPl.status === "fulfilled" ? ytPl.value : []),
          ]).slice(0, 12),
        };
      });
    } catch (e) {
      console.error("home local", e);
    }
    const charts = (globalPart.shelves[0] && globalPart.shelves[0].tracks) || [];
    return sendJSON(res, 200, {
      country: gl,
      day: utcDay(),
      localQuery: localQ,
      moods: moodsForCountry(gl),
      shelves: globalPart.shelves.length
        ? globalPart.shelves
        : ENGLISH_SHELVES.map((s) => ({ id: s.id, title: s.title, query: s.query, tracks: [] })),
      youtubeCharts: charts,
      youtubeLocal: localPart.youtubeLocal,
      youtubeIndia: localPart.youtubeLocal,
      countryPlaylists: localPart.countryPlaylists || [],
      globalPlaylists: globalPart.globalPlaylists || [],
      audius: globalPart.audius,
      underground: globalPart.underground,
      radio: globalPart.radio,
    });
  }

  if (p === "/api/shelf") {
    const id = url.searchParams.get("id") || "";
    const shelf = ENGLISH_SHELVES.find((s) => s.id === id);
    const q = url.searchParams.get("q") || (shelf && shelf.query) || "";
    const full = url.searchParams.get("full") === "1";
    if (!q.trim()) return sendJSON(res, 400, { error: "Missing query" });
    const gl = url.searchParams.get("gl") || "US";
    const cap = full ? 80 : 18;
    const tracks = await cached(`shelf:${full ? "full" : "row"}:${id}:${q}:${gl}:${utcDay()}`, 86400000, async () => {
      const rows = await searchYouTube(q, gl, !full);
      return (rows || []).slice(0, cap);
    });
    return sendJSON(res, 200, {
      id: id || (shelf && shelf.id) || "",
      title: (shelf && shelf.title) || q,
      tracks: tracks || [],
    });
  }

  if (p === "/api/search") {
    if (!q.trim()) return sendJSON(res, 400, { error: "Missing query" });
    const gl = regionCode(url.searchParams.get("gl"));
    const source = (url.searchParams.get("source") || "all").toLowerCase();
    const tasks = [];
    if (source === "all" || source === "youtube") tasks.push(["youtube", searchYouTube(q, gl)]);
    if (source === "all" || source === "audius") tasks.push(["audius", audiusSearch(q)]);
    if (source === "all" || source === "radio") tasks.push(["radio", radioSearch(q, 16, url.searchParams.get("quality"))]);
    if (source === "all" || source === "apple") tasks.push(["apple", itunesSearch(q)]);
    if (source === "all" || source === "audius") tasks.push(["audiusUsers", audiusUserSearch(q)]);
    const settled = await Promise.allSettled(tasks.map((t) => t[1]));
    const result = { query: q, youtube: [], audius: [], radio: [], apple: [], artists: [], playlists: [] };
    settled.forEach((s, i) => {
      const key = tasks[i][0];
      result[key] = s.status === "fulfilled" ? s.value : [];
    });
    const yt = result.youtube || [];
    const apple = result.apple && !Array.isArray(result.apple) ? result.apple : { songs: [], artists: [], playlists: [] };
    if (Array.isArray(result.apple)) result.apple = [];
    else result.apple = apple.songs || [];
    const artists = [];
    const playlists = [];
    const seenA = new Set();
    const seenP = new Set();
    const pushA = (a) => {
      const k = String((a && a.name) || "").toLowerCase();
      if (!k || seenA.has(k)) return;
      seenA.add(k);
      artists.push(a);
    };
    const pushP = (p) => {
      const k = String((p && (p.playlistId || p.id || p.title)) || "").toLowerCase();
      if (!k || seenP.has(k)) return;
      seenP.add(k);
      playlists.push(p);
    };
    (apple.artists || []).forEach(pushA);
    (apple.playlists || []).forEach(pushP);
    (yt.artists || []).forEach(pushA);
    (yt.playlists || []).forEach(pushP);
    for (const u of result.audiusUsers || []) {
      pushA({
        id: `artist:audius:${u.id}`,
        kind: "artist",
        name: u.name,
        artwork: u.artwork,
        source: "audius",
        query: u.name,
      });
    }
    delete result.audiusUsers;
    result.artists = artists.slice(0, 20);
    result.playlists = playlists.slice(0, 20);
    if (Array.isArray(yt)) result.youtube = yt;
    return sendJSON(res, 200, result);
  }

  if (p === "/api/youtube/search") {
    const yq = (url.searchParams.get("q") || q || "").trim();
    if (!yq) return sendJSON(res, 400, { error: "Missing query" });
    const gl = regionCode(url.searchParams.get("gl"));
    try {
      const tracks = await searchYouTube(yq, gl);
      return sendJSON(res, 200, { tracks: Array.isArray(tracks) ? tracks.slice(0, 80) : [] });
    } catch (e) {
      return sendJSON(res, 502, { tracks: [], error: String(e.message || e) });
    }
  }

  if (p === "/api/yt/playlist") {
    const id = url.searchParams.get("id") || "";
    const tracks = await youtubePlaylistTracks(id);
    return sendJSON(res, 200, { tracks, playlistId: id });
  }

  if (p === "/api/artist") {
    const q = (url.searchParams.get("q") || "").trim();
    const appleId = String(url.searchParams.get("id") || "").replace(/[^\d]/g, "");
    const name = (url.searchParams.get("name") || q).trim();
    const handle = url.searchParams.get("handle") || "";
    const userId = url.searchParams.get("userId") || "";
    const gl = regionCode(url.searchParams.get("gl"));

    if (appleId || q) {
      let artistName = name || q;
      let artwork = "";
      let songs = [];
      let albums = [];
      if (appleId) {
        try {
          const look = await fetchJSON(`https://itunes.apple.com/lookup?id=${encodeURIComponent(appleId)}&entity=album&limit=25`);
          const rows = (look && look.results) || [];
          const self = rows.find((r) => r.wrapperType === "artist") || {};
          artistName = self.artistName || artistName;
          for (const al of rows) {
            if (al.wrapperType !== "collection" && al.collectionType !== "Album") continue;
            if (!artwork && al.artworkUrl100) artwork = String(al.artworkUrl100).replace("100x100bb", "600x600bb");
            albums.push({
              id: `album:${al.collectionId}`,
              kind: "playlist",
              title: al.collectionName || "Album",
              artist: al.artistName || artistName,
              artwork: String(al.artworkUrl100 || "").replace("100x100bb", "600x600bb") || "/cover-default.png",
              source: "apple",
              query: `${al.collectionName || ""} ${al.artistName || artistName}`.trim(),
            });
          }
        } catch {}
        try {
          const look = await fetchJSON(`https://itunes.apple.com/lookup?id=${encodeURIComponent(appleId)}&entity=song&limit=30`);
          for (const t of (look && look.results) || []) {
            if (!t.trackId || t.wrapperType === "artist") continue;
            songs.push({
              id: `apple:${t.trackId}`,
              source: "apple",
              title: t.trackName || "Song",
              artist: t.artistName || artistName,
              album: t.collectionName || "",
              duration: Math.round((t.trackTimeMillis || 0) / 1000),
              artwork: String(t.artworkUrl100 || "").replace("100x100bb", "600x600bb") || "/cover-default.png",
              playQuery: `${t.trackName || ""} ${t.artistName || artistName} official audio`.trim(),
            });
          }
        } catch {}
      }
      if (!songs.length && (q || name)) {
        try {
          const pack = await itunesSearch(q || name);
          if (!artwork && pack.artists[0]) artwork = pack.artists[0].artwork;
          if (!artistName && pack.artists[0]) artistName = pack.artists[0].name;
          songs = pack.songs || [];
          if (!albums.length) albums = pack.playlists || [];
        } catch {}
      }
      let ytSongs = [];
      if (q || name) {
        try {
          ytSongs = (await searchYouTube(`${q || name} official audio`, gl, true) || []).slice(0, 16);
        } catch {}
      }
      const haveYt = new Set(ytSongs.map((t) => String(t.title || "").toLowerCase()));
      const appleRest = songs.filter((t) => !haveYt.has(String(t.title || "").toLowerCase()));
      songs = [...ytSongs, ...appleRest];
      return sendJSON(res, 200, { name: artistName || q || name, artwork, songs: songs.slice(0, 40), albums: albums.slice(0, 24), tracks: songs.slice(0, 16), latest: songs[0] || null });
    }

    let audius = [];
    let youtube = [];
    if (userId) {
      try { audius = await audiusUserTracks(userId); } catch {}
    } else if (handle) {
      try {
        const users = await audiusUserSearch(handle);
        const u = users.find((x) => String(x.handle).toLowerCase() === handle.toLowerCase()) || users[0];
        if (u) audius = await audiusUserTracks(u.id);
      } catch {}
    } else if (name) {
      try {
        const users = await audiusUserSearch(name);
        if (users[0]) audius = await audiusUserTracks(users[0].id);
      } catch {}
      try { youtube = (await searchYouTube(`${name} official audio`, gl)).slice(0, 8); } catch {}
    }
    return sendJSON(res, 200, {
      tracks: [...audius, ...youtube].slice(0, 16),
      latest: audius[0] || youtube[0] || null,
    });
  }

  if (p === "/api/radio") {
    const rq = (url.searchParams.get("q") || "").trim();
    const quality = url.searchParams.get("quality") || "";
    const codec = url.searchParams.get("codec") || "auto";
    try {
      let tracks = await radioSearch(rq, 36, quality, codec);
      if (!tracks.length && rq) tracks = await radioSearch("", 36, quality, codec);
      return sendJSON(res, 200, { tracks });
    } catch (e) {
      return sendJSON(res, 502, { tracks: [], error: String(e.message || e) });
    }
  }

  if (p.startsWith("/api/radio/click/")) {
    const id = p.split("/").pop();
    radioBrowser(`/json/url/${encodeURIComponent(id)}`).catch(() => {});
    return sendJSON(res, 200, { ok: true });
  }

  if (p === "/api/stream") {
    return pipeUrl(req, res, url.searchParams.get("url") || "", "audio/*,*/*");
  }

  if (p.startsWith("/api/audius/stream/")) {
    const id = p.split("/").pop();
    try {
      const stream = await audiusStreamUrl(id);
      return sendJSON(res, 200, { url: stream });
    } catch (e) {
      return sendJSON(res, 502, { error: String(e.message || e) });
    }
  }

  if (p.startsWith("/api/audius/file/")) {
    const id = p.split("/").pop();
    try {
      const stream = await audiusStreamUrl(id);
      return pipeUrl(req, res, stream, "audio/*,*/*");
    } catch (e) {
      return sendJSON(res, 502, { error: String(e.message || e) });
    }
  }

  if (p === "/api/img") {
    const src = url.searchParams.get("url") || "";
    if (!/^https?:\/\//i.test(src)) return sendJSON(res, 400, { error: "bad url" });
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 10000);
    try {
      const r = await fetch(src, {
        signal: ctrl.signal,
        headers: { "User-Agent": `${APP_NAME}/1.0`, Accept: "image/*" },
      });
      if (!r.ok) return sendJSON(res, r.status, { error: "image fetch failed" });
      const buf = Buffer.from(await r.arrayBuffer());
      const ct = r.headers.get("content-type") || "image/jpeg";
      res.writeHead(200, {
        "Content-Type": ct.split(";")[0],
        "Cache-Control": "public, max-age=86400",
        "Access-Control-Allow-Origin": "*",
      });
      return res.end(buf);
    } catch (e) {
      return sendJSON(res, 502, { error: e.message || "image error" });
    } finally {
      clearTimeout(timer);
    }
  }

  if (p === "/api/discover" || p === "/api/for-you") {
    const artists = String(url.searchParams.get("artists") || "").split(",").map((s) => s.trim()).filter(Boolean).slice(0, 5);
    const genres = String(url.searchParams.get("genres") || "").split(",").map((s) => s.trim()).filter(Boolean).slice(0, 4);
    const week = String(url.searchParams.get("week") || "").trim();
    const gl = regionCode(url.searchParams.get("gl"));
    const qs = [];
    artists.forEach((a) => {
      qs.push(`${a} mix official audio`);
      qs.push(`${a} radio mix`);
    });
    genres.forEach((g) => qs.push(`${g} songs official audio`));
    if (!qs.length) {
      qs.push("english pop hits official audio");
      qs.push("new music mix official audio");
      qs.push("indie pop english songs");
    }
    qs.push("hidden gems english songs official audio");
    const queries = [...new Set(qs)].slice(0, 6);
    const cacheKey = `discover:${gl}:${week}:${queries.join("|")}`;
    try {
      const tracks = await cached(cacheKey, 6 * 3600000, async () => {
        const settled = await Promise.allSettled(queries.map((q) => searchYouTube(q, gl, true)));
        const seen = new Set();
        const out = [];
        for (const s of settled) {
          const rows = s.status === "fulfilled" ? s.value : [];
          for (const row of rows || []) {
            if (!row || row.source === "radio") continue;
            const k = String(row.videoId || row.id || "");
            if (!k || seen.has(k)) continue;
            seen.add(k);
            out.push(row);
            if (out.length >= 40) break;
          }
          if (out.length >= 40) break;
        }
        let seed = 0;
        const weekSeed = week || "mix";
        for (let i = 0; i < weekSeed.length; i++) seed = (seed * 31 + weekSeed.charCodeAt(i)) >>> 0;
        const shuffled = out.slice();
        for (let i = shuffled.length - 1; i > 0; i--) {
          seed = (seed * 1664525 + 1013904223) >>> 0;
          const j = seed % (i + 1);
          const tmp = shuffled[i];
          shuffled[i] = shuffled[j];
          shuffled[j] = tmp;
        }
        return shuffled.slice(0, 30);
      });
      return sendJSON(res, 200, { week, title: "Discovery Mix", tracks: tracks || [] });
    } catch (e) {
      return sendJSON(res, 200, { week, title: "Discovery Mix", tracks: [], error: String(e.message || e) });
    }
  }

  if (p === "/api/related") {
    const title = (url.searchParams.get("title") || "").trim();
    const artist = (url.searchParams.get("artist") || "").trim();
    const skip = (url.searchParams.get("skip") || "").trim();
    const gl = regionCode(url.searchParams.get("gl"));
    const a = artist.replace(/\s*[|–—-]\s*topic$/i, "").trim();
    const t = title.replace(/\s*\((official|lyrics|audio|video).*?\)/ig, "").trim();
    const qs = [];
    if (a && !/^(youtube|various artists|unknown)$/i.test(a)) {
      qs.push(`${a} official audio`);
      qs.push(`${a} mix`);
    }
    if (t && a) qs.push(`${t} ${a} official audio`);
    else if (t) qs.push(`${t} official audio`);
    const queries = [...new Set(qs.filter(Boolean))].slice(0, 1);
    if (!queries.length) return sendJSON(res, 200, { tracks: [] });
    const cacheKey = `related:${gl}:${queries.join("|")}`;
    try {
      const tracks = await cached(cacheKey, 180000, async () => {
        const settled = await Promise.allSettled(queries.map((q) => searchYouTube(q, gl, true)));
        const seen = new Set();
        const out = [];
        for (const s of settled) {
          const rows = s.status === "fulfilled" ? s.value : [];
          for (const row of rows || []) {
            if (!row || row.source === "radio") continue;
            const k = String(row.videoId || row.id || "");
            if (!k || seen.has(k) || seen.has(row.id)) continue;
            seen.add(k);
            if (row.id) seen.add(row.id);
            out.push(row);
            if (out.length >= 28) break;
          }
          if (out.length >= 28) break;
        }
        return out;
      });
      const skipSet = new Set(String(skip).split(",").map((x) => x.trim()).filter(Boolean));
      return sendJSON(res, 200, {
        tracks: (tracks || []).filter((row) => row && !skipSet.has(row.id) && !skipSet.has(row.videoId)).slice(0, 24),
      });
    } catch (e) {
      return sendJSON(res, 200, { tracks: [], error: String(e.message || e) });
    }
  }

  if (p === "/api/lyrics") {
    const title = url.searchParams.get("title") || "";
    const artist = url.searchParams.get("artist") || "";
    const data = await lyricsFor(title, artist);
    return sendJSON(res, 200, data);
  }

  return sendJSON(res, 404, { error: "Not found" });
}

function serveStatic(req, res, urlPath) {
  let rel = decodeURIComponent(urlPath);
  if (rel === "/") rel = "/index.html";
  if (rel === "/favicon.ico") rel = "/logo.png";
  const file = path.normalize(path.join(PUBLIC, rel));
  if (!file.startsWith(PUBLIC)) return send(res, 403, "Forbidden");
  fs.stat(file, (err, st) => {
    if (err || !st.isFile()) {
      if (!path.extname(rel)) {
        return serveStatic(req, res, "/index.html");
      }
      return send(res, 404, "Not found");
    }
    const ext = path.extname(file).toLowerCase();
    res.writeHead(200, {
      "Content-Type": MIME[ext] || "application/octet-stream",
      "Cache-Control": ext === ".html" || ext === ".js" || ext === ".css" ? "no-store, max-age=0" : "public, max-age=86400",
    });
    fs.createReadStream(file).pipe(res);
  });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (req.method === "OPTIONS") {
      res.writeHead(204, {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
      });
      return res.end();
    }
    if (url.pathname.startsWith("/api/")) {
      return await handleApi(req, res, url);
    }
    return serveStatic(req, res, url.pathname);
  } catch (err) {
    console.error(err);
    if (!res.headersSent) sendJSON(res, 500, { error: err.message || "Server error" });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Muchi listening on http://${HOST}:${PORT}`);
});
