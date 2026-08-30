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
  const COUNTRY_LABELS = {
    IN: "India", US: "United States", GB: "United Kingdom", CA: "Canada", AU: "Australia",
    DE: "Germany", FR: "France", JP: "Japan", KR: "South Korea", BR: "Brazil", MX: "Mexico",
    NG: "Nigeria", ZA: "South Africa", AE: "UAE", SA: "Saudi Arabia", PK: "Pakistan",
    BD: "Bangladesh", ID: "Indonesia", MY: "Malaysia", SG: "Singapore", PH: "Philippines",
    TH: "Thailand", VN: "Vietnam", EG: "Egypt", IT: "Italy", ES: "Spain", TR: "Turkey",
    NZ: "New Zealand", NL: "Netherlands", SE: "Sweden",
  };
  const COUNTRY_MUSIC_TERMS = {
    IN: "Indian Hindi Bollywood",
    US: "US American",
    GB: "UK British",
    CA: "Canadian",
    AU: "Australian",
    DE: "German Deutschland",
    FR: "French France",
    JP: "Japanese J-pop",
    KR: "Korean K-pop",
    BR: "Brazilian Portuguese",
    MX: "Mexican Spanish",
    NG: "Nigerian Afrobeats",
    ZA: "South African",
    AE: "Arabic UAE",
    SA: "Saudi Khaleeji Arabic",
    PK: "Pakistani Urdu",
    BD: "Bangla Bengali Bangladesh",
    ID: "Indonesian",
    MY: "Malaysian Malay",
    SG: "Singaporean",
    PH: "Filipino OPM",
    TH: "Thai T-pop",
    VN: "Vietnamese V-pop",
    EG: "Egyptian Arabic",
    IT: "Italian",
    ES: "Spanish Spain",
    TR: "Turkish",
    NZ: "New Zealand",
    NL: "Dutch Netherlands",
    SE: "Swedish",
  };
  const COUNTRY_REGIONAL_PLAYLISTS = {
    IN: [
      { id: "punjabi", title: "Punjabi & Bhangra", query: "Punjabi bhangra hits official audio" },
      { id: "tamil", title: "Tamil & Kollywood", query: "Tamil Kollywood hits official audio" },
      { id: "telugu", title: "Telugu & Tollywood", query: "Telugu Tollywood hits official audio" },
      { id: "bangla", title: "Bangla India", query: "Bangla Bengali songs India official audio" },
      { id: "marathi", title: "Marathi Hits", query: "Marathi songs hits official audio" },
    ],
    US: [
      { id: "country", title: "Country & Americana", query: "US country Americana hits official audio" },
      { id: "latin", title: "US Latin", query: "US Latin reggaeton hits official audio" },
      { id: "southern", title: "Southern Hip-Hop", query: "US southern hip hop rap hits official audio" },
    ],
    GB: [
      { id: "drill", title: "UK Drill", query: "UK drill hits official audio" },
      { id: "grime", title: "UK Grime", query: "UK grime hits official audio" },
    ],
    CA: [
      { id: "french", title: "French Canadian", query: "French Canadian hits official audio" },
      { id: "canadian-indie", title: "Canadian Indie", query: "Canadian indie rock hits official audio" }
    ],
    AU: [
      { id: "rock", title: "Aussie Rock", query: "Australian rock hits official audio" },
      { id: "indie", title: "Australian Indie", query: "Australian indie hits official audio" },
    ],
    DE: [{ id: "rap", title: "German Rap", query: "German rap Deutschrap hits official audio" }],
    FR: [{ id: "rap", title: "French Rap", query: "French rap hits official audio" }],
    JP: [
      { id: "anime", title: "Anime Songs", query: "Japanese anime songs official audio" },
      { id: "jrock", title: "J-Rock", query: "Japanese J-rock hits official audio" },
    ],
    KR: [
      { id: "krnb", title: "K-R&B", query: "Korean K-R&B hits official audio" },
      { id: "khiphop", title: "K-Hip-Hop", query: "Korean K-hip-hop hits official audio" },
    ],
    BR: [
      { id: "funk", title: "Brazilian Funk", query: "Brazilian funk hits official audio" },
      { id: "sertanejo", title: "Sertanejo", query: "Brazilian sertanejo hits official audio" },
    ],
    MX: [
      { id: "regional", title: "Regional Mexican", query: "Mexican regional hits official audio" },
      { id: "reggaeton", title: "Mexican Reggaeton", query: "Mexican reggaeton hits official audio" },
    ],
    NG: [
      { id: "afrobeats", title: "Afrobeats", query: "Nigerian Afrobeats hits official audio" },
      { id: "afropop", title: "Nigerian Afropop", query: "Nigerian Afropop hits official audio" },
    ],
    ZA: [
      { id: "amapiano", title: "Amapiano", query: "South African amapiano hits official audio" },
      { id: "house", title: "South African House", query: "South African house dance hits official audio" },
    ],
    PK: [
      { id: "qawwali", title: "Qawwali & Sufi", query: "Pakistani qawwali sufi official audio" },
      { id: "pop", title: "Pakistani Pop", query: "Pakistani pop hits official audio" },
    ],
    BD: [{ id: "bangla", title: "Bangla Hits", query: "Bangladesh Bangla hits official audio" }],
    PH: [{ id: "opm", title: "OPM", query: "Filipino OPM hits official audio" }],
    ID: [{ id: "indo", title: "Indonesian Pop", query: "Indonesian pop hits official audio" }],
    TH: [{ id: "tpop", title: "Thai T-Pop", query: "Thai T-pop hits official audio" }],
    VN: [{ id: "vpop", title: "Vietnamese V-Pop", query: "Vietnamese V-pop hits official audio" }],
    AE: [{ id: "arabic", title: "Arabic Gulf", query: "UAE Arabic Khaleeji hits official audio" }],
    SA: [{ id: "khaleeji", title: "Saudi Khaleeji", query: "Saudi Khaleeji hits official audio" }],
    EG: [{ id: "mahraganat", title: "Egyptian Mahraganat", query: "Egyptian mahraganat hits official audio" }],
    IT: [{ id: "italian", title: "Italian Pop", query: "Italian pop hits official audio" }],
    ES: [{ id: "flamenco", title: "Spanish & Flamenco", query: "Spanish flamenco hits official audio" }],
    TR: [{ id: "turkish", title: "Türkçe Pop", query: "Turkish pop hits official audio" }],
  };
  const LOCAL_QUERIES = {
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

  function countryLabel(code) {
    return COUNTRY_LABELS[String(code || "IN").toUpperCase()] || String(code || "IN").toUpperCase();
  }

  function countryTerm(code) {
    const key = String(code || "IN").toUpperCase();
    return COUNTRY_MUSIC_TERMS[key] || countryLabel(key);
  }

  function shelfDefinitions(country = "IN") {
    const code = String(country || "IN").toUpperCase();
    const term = countryTerm(code);
    const local = LOCAL_QUERIES[code] || `${term} top songs`;
    const today = `${local} latest trending official audio`;
    return [
      { id: "today", title: "Today's Top Hits", query: today, queries: [today, `${term} biggest hits classic songs all time official audio`] },
      { id: "pop", title: "Pop", query: `${term} pop hits official audio` },
      { id: "hiphop", title: "Hip-Hop", query: `${term} hip hop rap hits official audio` },
      { id: "rnb", title: "R&B", query: `${term} R&B soul hits official audio` },
      { id: "rock", title: "Rock", query: `${term} rock alternative hits official audio` },
      { id: "dance", title: "Dance & Electronic", query: `${term} dance EDM electronic hits official audio` },
      { id: "indie", title: "Indie", query: `${term} indie alternative songs official audio` },
    ].map((shelf) => ({ ...shelf, country: code, countryLabel: countryLabel(code) }));
  }

  function playlistDefinitions(country = "IN") {
    const code = String(country || "IN").toUpperCase();
    const label = countryLabel(code);
    const term = countryTerm(code);
    const year = new Date().getUTCFullYear();
    const base = [
      { id: "new", title: `New & Trending in ${label}`, query: `${term} new songs ${year} official audio` },
      { id: "hits", title: `${label} Biggest Hits`, query: `${term} biggest hits official audio` },
      { id: "classics", title: `${label} Classics`, query: `${term} classic songs all time official audio` },
      { id: "love", title: `${label} Love Songs`, query: `${term} romantic love songs official audio` },
      { id: "party", title: `${label} Party & Dance`, query: `${term} party dance songs official audio` },
      { id: "indie", title: `${label} Indie & Underground`, query: `${term} indie alternative underground songs official audio` },
      { id: "workout", title: `${label} Workout`, query: `${term} workout hip hop dance songs official audio` },
    ];
    const regional = (COUNTRY_REGIONAL_PLAYLISTS[code] || []).map((playlist) => ({
      ...playlist,
      title: `${playlist.title} in ${label}`,
    }));
    return [...base, ...regional].map((playlist) => ({
      ...playlist,
      id: `${code.toLowerCase()}-${playlist.id}`,
      kind: "playlist",
      source: "youtube",
      country: code,
      countryLabel: label,
    }));
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

  async function appleQueries(queries, limit = 24) {
    const list = Array.isArray(queries) && queries.length ? queries : [String(queries || "")];
    const settled = await Promise.allSettled(list.filter(Boolean).map((query) => appleSearch(query, limit)));
    return uniqueTracks(settled.flatMap((result) => result.status === "fulfilled" ? result.value : []), limit);
  }

  async function youtubeQueries(queries, limit = 24) {
    const list = Array.isArray(queries) && queries.length ? queries : [String(queries || "")];
    const settled = await Promise.allSettled(list.filter(Boolean).map((query) => youtubeSearch(query, limit)));
    return uniqueTracks(settled.flatMap((result) => result.status === "fulfilled" ? result.value : []), limit);
  }

  async function localSearch(country = "IN", limit = 50) {
    const code = String(country || "IN").toUpperCase();
    const definition = shelfDefinitions(code)[0];
    const queries = definition.queries || [definition.query];
    const [apple, youtube] = await Promise.allSettled([
      appleQueries(queries, limit),
      youtubeQueries(queries, limit),
    ]);
    const rows = (result) => result.status === "fulfilled" ? result.value : [];
    return uniqueTracks([...rows(youtube), ...rows(apple)], limit);
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

  async function home(country = "IN") {
    const code = String(country || "IN").toUpperCase();
    const shelvesDef = shelfDefinitions(code);
    const playlistsDef = playlistDefinitions(code);
    const [shelfResults, playlistResults, extras] = await Promise.all([
      Promise.allSettled(
        shelvesDef.map((shelf) => appleQueries(shelf.queries || [shelf.query], 30))
      ),
      Promise.allSettled(
        playlistsDef.map((playlist) => appleSearch(playlist.query, 24))
      ),
      Promise.allSettled([
        localSearch(code, 50),
        audiusTrending(24),
        radioSearch("hits", 18),
      ]),
    ]);
    const [localResult, audiusResult, radioResult] = extras;
    const value = (result) => result.status === "fulfilled" ? result.value : [];
    const localTracks = value(localResult);
    const shelves = shelvesDef.map((shelf, index) => ({
      ...shelf,
      tracks: value(shelfResults[index]).slice(0, 30),
    }));
    if (!shelves[0].tracks.length && localTracks.length) shelves[0].tracks = localTracks.slice(0, 30);
    const countryPlaylists = playlistsDef.map((playlist, index) => ({
      ...playlist,
      artwork: value(playlistResults[index])[0] && value(playlistResults[index])[0].artwork || "/cover-default.png",
      // Keep a useful preview in the card. Opening the card fetches a larger
      // country-specific result set through the same direct API path.
      tracks: value(playlistResults[index]).slice(0, 24),
    }));
    const directAudius = value(audiusResult);
    return {
      country: code,
      countryLabel: countryLabel(code),
      localQuery: shelvesDef[0].query,
      day: new Date().toISOString().slice(0, 10),
      shelves,
      youtubeCharts: shelves[0].tracks,
      youtubeLocal: localTracks,
      youtubeIndia: localTracks,
      countryPlaylists,
      globalPlaylists: [],
      audius: directAudius,
      underground: directAudius.slice(0, 18),
      radio: value(radioResult),
      direct: true,
    };
  }

  window.MuchiDirectApi = {
    search,
    home,
    localSearch,
    shelfDefinitions,
    playlistDefinitions,
    youtubeSearch,
    radioSearch,
    audiusSearch,
  };
})();
