/*
 * Direct browser catalog fallback.
 *
 * The web app normally asks the local Muchi server for catalog data. In hosted
 * previews the server may be unable to make outbound requests, so this client
 * talks to public catalog APIs from the user's browser instead. It never uses
 * GitHub for music data and never extracts YouTube audio: YouTube results are
 * still handed to the official IFrame player.
 */
(() => {
  "use strict";

  const APP_NAME = "Muchi";
  const DEFAULT_TIMEOUT = 9000;
  const RADIO_HOSTS = [
    "https://de1.api.radio-browser.info",
    "https://fi1.api.radio-browser.info",
    "https://at1.api.radio-browser.info",
    "https://nl1.api.radio-browser.info",
  ];
  // These endpoints return metadata only. Playback continues through the
  // official YouTube IFrame player after a video id is found.
  const VIDEO_APIS = [
    (q) => `https://pipedapi.kavin.rocks/search?q=${encodeURIComponent(q)}&filter=music_songs`,
    (q) => `https://pipedapi.leptons.xyz/search?q=${encodeURIComponent(q)}&filter=music_songs`,
    (q) => `https://pipedapi.nosebs.ru/search?q=${encodeURIComponent(q)}&filter=music_songs`,
    (q) => `https://pipedapi.reallyaweso.me/search?q=${encodeURIComponent(q)}&filter=music_songs`,
    (q) => `https://pipedapi.drgns.space/search?q=${encodeURIComponent(q)}&filter=music_songs`,
    (q) => `https://api.piped.yt/search?q=${encodeURIComponent(q)}&filter=music_songs`,
    (q) => `https://piped-api.privacy.com.de/search?q=${encodeURIComponent(q)}&filter=music_songs`,
  ];

  function withTimeout(ms) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), ms);
    return { ctrl, timer };
  }

  async function json(url, options = {}, timeout = DEFAULT_TIMEOUT) {
    const { ctrl, timer } = withTimeout(timeout);
    try {
      const res = await fetch(url, {
        ...options,
        mode: "cors",
        signal: ctrl.signal,
        headers: {
          Accept: "application/json",
          ...(options.headers || {}),
        },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } finally {
      clearTimeout(timer);
    }
  }

  // Apple exposes the Search API as JSONP rather than CORS JSON. JSONP keeps
  // this fallback browser-direct without introducing another proxy service.
  function jsonp(url, timeout = DEFAULT_TIMEOUT) {
    return new Promise((resolve, reject) => {
      const callback = `__muchi_jsonp_${Date.now()}_${Math.random().toString(36).slice(2)}`;
      const script = document.createElement("script");
      let timer;
      const cleanup = () => {
        clearTimeout(timer);
        try { script.remove(); } catch {}
        try { delete window[callback]; } catch { window[callback] = undefined; }
      };
      window[callback] = (value) => {
        cleanup();
        resolve(value);
      };
      script.onerror = () => {
        cleanup();
        reject(new Error("JSONP request failed"));
      };
      timer = setTimeout(() => {
        cleanup();
        reject(new Error("JSONP request timed out"));
      }, timeout);
      script.src = `${url}${url.includes("?") ? "&" : "?"}callback=${encodeURIComponent(callback)}`;
      (document.head || document.documentElement).appendChild(script);
    });
  }

  async function first(urls, options = {}, timeout = DEFAULT_TIMEOUT, valid = () => true) {
    const jobs = urls.map(async (url) => {
      const value = await json(url, options, timeout);
      if (!valid(value)) throw new Error("empty direct response");
      return value;
    });
    try {
      return await Promise.any(jobs);
    } catch {
      throw new Error("direct catalog unavailable");
    }
  }

  function lastArtwork(value, fallback = "/cover-default.png") {
    if (!value) return fallback;
    if (Array.isArray(value) && value.length) {
      const item = value[value.length - 1];
      return item && (item.url || item.src || item.thumbnail) || fallback;
    }
    if (typeof value === "object") {
      return value["600x600"] || value["480x480"] || value["1000x1000"] ||
        value["400x400"] || value["150x150"] || value.url || fallback;
    }
    return String(value) || fallback;
  }

  function uniqueTracks(rows, limit = 60) {
    const seen = new Set();
    const out = [];
    for (const row of rows || []) {
      if (!row) continue;
      const key = String(row.id || row.videoId || row.trackId || row.title || "").toLowerCase();
      if (!key || seen.has(key)) continue;
      seen.add(key);
      out.push(row);
      if (out.length >= limit) break;
    }
    return out;
  }

  function uniqueByName(rows, limit = 20) {
    const seen = new Set();
    const out = [];
    for (const row of rows || []) {
      const key = String(row && (row.name || row.title) || "").trim().toLowerCase();
      if (!key || seen.has(key)) continue;
      seen.add(key);
      out.push(row);
      if (out.length >= limit) break;
    }
    return out;
  }

  function mapApple(row) {
    if (!row || !row.trackId) return null;
    const artwork = String(row.artworkUrl100 || "").replace(/100x100bb/g, "600x600bb") || "/cover-default.png";
    const preview = row.previewUrl || "";
    return {
      id: `apple:${row.trackId}`,
      source: "apple",
      title: row.trackName || "Song",
      artist: row.artistName || "Artist",
      album: row.collectionName || "",
      duration: Math.round((row.trackTimeMillis || 0) / 1000),
      artwork,
      streamUrl: preview,
      preview: !!preview,
      direct: true,
      playQuery: `${row.trackName || ""} ${row.artistName || ""} official audio`.trim(),
    };
  }

  function mapAudius(row) {
    if (!row || !row.id) return null;
    const user = row.user || {};
    const artwork = lastArtwork(row.artwork);
    return {
      id: `audius:${row.id}`,
      source: "audius",
      trackId: row.id,
      title: row.title || "Untitled",
      artist: user.name || user.handle || "Independent artist",
      album: row.genre || "Audius",
      duration: Number(row.duration) || 0,
      artwork,
      genre: row.genre || "",
      mood: row.mood || "",
      plays: row.play_count || 0,
      permalink: row.permalink || "",
      streamUrl: row.stream && row.stream.url
        ? row.stream.url
        : `https://api.audius.co/v1/tracks/${encodeURIComponent(row.id)}/stream?app_name=${APP_NAME}`,
      direct: true,
    };
  }

  function codecMatch(raw, want) {
    const codec = String(raw || "").toLowerCase();
    if (!want || want === "auto") return true;
    if (want === "mp3") return /mp3|mpeg/.test(codec);
    if (want === "aac") return /aac/.test(codec);
    if (want === "opus") return /opus|ogg|vorbis/.test(codec);
    return true;
  }

  function mapRadio(row) {
    if (!row || !row.stationuuid || !row.url_resolved) return null;
    return {
      id: `radio:${row.stationuuid}`,
      source: "radio",
      stationId: row.stationuuid,
      title: String(row.name || "Radio").trim(),
      artist: [row.country, row.tags].filter(Boolean).join(" · ") || "Live radio",
      album: row.codec || "Radio",
      duration: 0,
      artwork: row.favicon || "/cover-default.png",
      streamUrl: row.url_resolved,
      homepage: row.homepage || "",
      bitrate: row.bitrate || 0,
      codec: row.codec || "",
      direct: true,
    };
  }

  function videoIdFrom(value) {
    const text = String(value || "");
    if (/^[\w-]{11}$/.test(text)) return text;
    const match = text.match(/(?:v=|\/watch\/|youtu\.be\/|\/shorts\/|\/embed\/)([\w-]{11})/);
    return match ? match[1] : "";
  }

  function mapVideo(row) {
    if (!row) return null;
    const videoId = videoIdFrom(row.videoId || row.url || row.id);
    if (!videoId) return null;
    const thumbs = row.thumbnail || row.thumbnails || row.videoThumbnails || row.thumbnailUrl;
    return {
      id: `yt:${videoId}`,
      source: "youtube",
      videoId,
      title: row.title || "YouTube",
      artist: row.uploaderName || row.uploader || row.author || row.channelName || "YouTube",
      album: "",
      duration: Number(row.duration) || 0,
      artwork: lastArtwork(thumbs, `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`),
    };
  }

  async function appleSearch(query, limit = 24) {
    const url = `https://itunes.apple.com/search?term=${encodeURIComponent(query)}&media=music&entity=song&limit=${Math.min(limit, 50)}`;
    let data;
    try {
      data = await jsonp(url, 8000);
    } catch {
      // Some browsers or privacy extensions allow CORS even though the
      // documented interface is JSONP, so keep fetch as a secondary path.
      data = await json(url, {}, 8000);
    }
    return (data.results || []).map(mapApple).filter(Boolean);
  }

  async function audiusSearch(query, limit = 24) {
    const data = await json(
      `https://api.audius.co/v1/tracks/search?query=${encodeURIComponent(query)}&app_name=${APP_NAME}&limit=${Math.min(limit, 50)}`,
      {},
      8000
    );
    return (data.data || []).map(mapAudius).filter(Boolean);
  }

  async function audiusTrending(limit = 24) {
    const data = await json(
      `https://api.audius.co/v1/tracks/trending?app_name=${APP_NAME}&limit=${Math.min(limit, 50)}`,
      {},
      8000
    );
    return (data.data || []).map(mapAudius).filter(Boolean);
  }

  async function radioSearch(query = "", limit = 24, quality = "", codec = "auto") {
    const params = new URLSearchParams({
      limit: String(Math.max(limit * 2, 24)),
      hidebroken: "true",
      order: query ? "votes" : "clickcount",
      reverse: "true",
      lastcheckok: "true",
    });
    if (query) params.set("name", query);
    if (quality === "low") params.set("bitrateMax", "96");
    if (quality === "high") params.set("bitrateMin", "192");
    const data = await first(
      RADIO_HOSTS.map((host) => `${host}/json/stations/search?${params}`),
      {},
      6000,
      (value) => Array.isArray(value) && value.length > 0
    );
    let rows = (data || []).filter((row) => row && row.url_resolved && Number(row.hls) !== 1 && !/\.m3u8(\?|$)/i.test(row.url_resolved));
    if (quality === "low") rows = rows.filter((row) => !row.bitrate || Number(row.bitrate) <= 96);
    if (quality === "high") rows.sort((a, b) => Number(b.bitrate || 0) - Number(a.bitrate || 0));
    rows = rows.filter((row) => codecMatch(row.codec, codec));
    return rows.map(mapRadio).filter(Boolean).slice(0, limit);
  }

  async function youtubeSearch(query, limit = 24) {
    const urls = VIDEO_APIS.map((makeUrl) => makeUrl(query));
    const data = await first(
      urls,
      {},
      8000,
      (value) => {
        const items = Array.isArray(value) ? value : value.items || value.results || [];
        return items.some((item) => mapVideo(item));
      }
    );
    const items = Array.isArray(data) ? data : data.items || data.results || [];
    return items.map(mapVideo).filter(Boolean).slice(0, limit);
  }

  async function search(query, options = {}) {
    const q = String(query || "").trim();
    if (!q) return { query: q, youtube: [], audius: [], radio: [], apple: [], artists: [], playlists: [] };
    const quality = options.quality || "";
    const codec = options.codec || "auto";
    const [youtube, audius, radio, apple] = await Promise.allSettled([
      youtubeSearch(q, options.limit || 24),
      audiusSearch(q, options.limit || 24),
      radioSearch(q, 12, quality, codec),
      appleSearch(q, options.limit || 24),
    ]);
    const rows = (result) => result.status === "fulfilled" ? result.value : [];
    const songs = uniqueTracks([...rows(youtube), ...rows(apple), ...rows(audius)], 80);
    const artists = uniqueByName(
      songs.map((track) => ({
        id: `artist:direct:${track.artist}`,
        kind: "artist",
        name: track.artist,
        artwork: track.artwork,
        source: track.source,
        query: track.artist,
      })),
      20
    );
    return {
      query: q,
      youtube: rows(youtube),
      audius: rows(audius),
      radio: rows(radio),
      apple: rows(apple),
      artists,
      playlists: [],
    };
  }

  async function home() {
    const shelfQueries = ["pop hits", "new music", "hip hop hits", "r&b hits", "rock hits", "dance music", "indie music"];
    const [general, bollywood, audius, radio, youtube] = await Promise.allSettled([
      appleSearch("pop hits", 30),
      appleSearch("bollywood hits", 18),
      audiusTrending(18),
      radioSearch("hits", 12),
      youtubeSearch("top hits official audio", 18),
    ]);
    const value = (result) => result.status === "fulfilled" ? result.value : [];
    const base = uniqueTracks([...value(youtube), ...value(general), ...value(bollywood), ...value(audius)], 80);
    const shelves = shelfQueries.map((item, index) => ({
      id: ["today", "pop", "hiphop", "rnb", "rock", "dance", "indie"][index],
      title: ["Today's Top Hits", "Pop", "Hip-Hop", "R&B", "Rock", "Dance & Electronic", "Indie"][index],
      query: item,
      // iTunes previews make the fallback immediately playable while a
      // YouTube/Audius result is still opened in its native player.
      tracks: base.slice((index * 7) % Math.max(base.length, 1), ((index * 7) % Math.max(base.length, 1)) + 12),
    }));
    if (base.length && shelves.every((shelf) => !shelf.tracks.length)) shelves[0].tracks = base.slice(0, 12);
    const directAudius = value(audius);
    return {
      day: new Date().toISOString().slice(0, 10),
      shelves,
      youtubeCharts: value(youtube),
      youtubeLocal: value(youtube),
      youtubeIndia: value(youtube),
      countryPlaylists: [],
      globalPlaylists: [],
      audius: directAudius,
      underground: directAudius.slice(0, 12),
      radio: value(radio),
      direct: true,
    };
  }

  window.MuchiDirectApi = {
    search,
    home,
    youtubeSearch,
    radioSearch,
    audiusSearch,
  };
})();
