package app.muchi.music

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.muchi.music.auth.GoogleAuth
import app.muchi.music.data.GoogleUser
import app.muchi.music.data.HomeDto
import app.muchi.music.data.LyricsDto
import app.muchi.music.data.MuchiApi
import app.muchi.music.data.PlaylistHit
import app.muchi.music.data.Prefs
import app.muchi.music.data.SearchDto
import app.muchi.music.data.Track
import app.muchi.music.data.UserPlaylist
import app.muchi.music.player.MuchiPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

class MuchiViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var tab by mutableIntStateOf(0)
    var screen by mutableStateOf("main")
    var country by mutableStateOf(prefs.country)
    var home by mutableStateOf<HomeDto?>(null)
    var homeError by mutableStateOf<String?>(null)
    var loadingHome by mutableStateOf(false)

    var query by mutableStateOf("")
    var search by mutableStateOf<SearchDto?>(null)
    var searching by mutableStateOf(false)

    var radio by mutableStateOf<List<Track>>(emptyList())
    var radioLoading by mutableStateOf(false)

    var now by mutableStateOf<Track?>(null)
    var playing by mutableStateOf(false)
    var queue = mutableStateListOf<Track>()
    var queueIndex by mutableIntStateOf(-1)

    var liked = mutableStateListOf<Track>().apply { addAll(prefs.liked()) }
    var recents = mutableStateListOf<Track>().apply { addAll(prefs.recents()) }
    var playlists = mutableStateListOf<UserPlaylist>().apply { addAll(prefs.playlists()) }

    var google by mutableStateOf(prefs.googleUser())
    var authMsg by mutableStateOf<String?>(null)
    var followAlerts by mutableStateOf(prefs.followAlerts())
    var lyrics by mutableStateOf<LyricsDto?>(null)
    var toast by mutableStateOf<String?>(null)
    var settingsPage by mutableStateOf("")
    var catalogTitle by mutableStateOf("Playlist")
    var catalogArtist by mutableStateOf("")
    var catalogArt by mutableStateOf<String?>(null)
    var catalogTracks = mutableStateListOf<Track>()
    var catalogLoading by mutableStateOf(false)

    private var tickJob: Job? = null
    private var homeFetchedAt = 0L

    val countries = listOf(
        "IN" to "India", "US" to "United States", "GB" to "United Kingdom",
        "CA" to "Canada", "AU" to "Australia", "DE" to "Germany", "FR" to "France",
        "JP" to "Japan", "KR" to "South Korea", "BR" to "Brazil", "MX" to "Mexico",
        "NG" to "Nigeria", "ZA" to "South Africa", "PK" to "Pakistan", "BD" to "Bangladesh",
        "ID" to "Indonesia", "PH" to "Philippines", "AE" to "UAE", "IT" to "Italy",
        "ES" to "Spain", "TR" to "Türkiye",
    )

    init {
        MuchiPlayer.onEnded = { next() }
        MuchiPlayer.onSkipNext = { next() }
        MuchiPlayer.onSkipPrev = { prev() }
        viewModelScope.launch {
            MuchiPlayer.now.collect { now = it }
        }
        viewModelScope.launch {
            MuchiPlayer.playing.collect { playing = it }
        }
        loadHome()
        loadRadio()
        startTicker()
    }

    fun greeting(): String {
        val tz = try {
            TimeZone.getTimeZone(when (country) {
                "IN" -> "Asia/Kolkata"
                "US" -> "America/New_York"
                "GB" -> "Europe/London"
                "JP" -> "Asia/Tokyo"
                "AU" -> "Australia/Sydney"
                "DE", "FR", "IT", "ES" -> "Europe/Berlin"
                "BR" -> "America/Sao_Paulo"
                "NG" -> "Africa/Lagos"
                "AE" -> "Asia/Dubai"
                "KR" -> "Asia/Seoul"
                "PK" -> "Asia/Karachi"
                "BD" -> "Asia/Dhaka"
                else -> TimeZone.getDefault().id
            })
        } catch (_: Exception) { TimeZone.getDefault() }
        val h = Calendar.getInstance(tz).get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    fun pickCountry(code: String) {
        country = code
        prefs.country = code
        loadHome(true)
    }

    fun loadHome(force: Boolean = false) {
        if (!force && home != null && System.currentTimeMillis() - homeFetchedAt < 60_000) return
        loadingHome = true
        homeError = null
        viewModelScope.launch {
            try {
                home = MuchiApi.home(country)
                homeFetchedAt = System.currentTimeMillis()
            } catch (e: Exception) {
                homeError = e.message ?: "Could not load Home"
            } finally {
                loadingHome = false
            }
        }
    }

    fun openCatalog(
        title: String,
        artist: String = "",
        artwork: String? = null,
        playlistId: String? = null,
        query: String? = null,
        tracks: List<Track> = emptyList(),
        shelfId: String? = null,
    ) {
        catalogTitle = title.ifBlank { "Playlist" }
        catalogArtist = artist
        catalogArt = artwork
        catalogTracks.clear()
        catalogTracks.addAll(tracks)
        val needFill = !shelfId.isNullOrBlank() || !playlistId.isNullOrBlank() || !query.isNullOrBlank()
        catalogLoading = needFill
        screen = "catalog"
        if (!needFill) return
        viewModelScope.launch {
            try {
                var got = emptyList<Track>()
                if (!shelfId.isNullOrBlank() || (!query.isNullOrBlank() && playlistId.isNullOrBlank())) {
                    got = MuchiApi.shelf(shelfId.orEmpty(), query.orEmpty())
                }
                if (got.isEmpty() && !playlistId.isNullOrBlank()) got = MuchiApi.ytPlaylist(playlistId)
                if (got.isEmpty() && !query.isNullOrBlank()) {
                    val s = MuchiApi.search(query, country)
                    got = (s.youtube.orEmpty() + s.apple.orEmpty() + s.audius.orEmpty())
                }
                if (got.isNotEmpty()) {
                    catalogTracks.clear()
                    catalogTracks.addAll(got)
                    if (catalogArt.isNullOrBlank()) catalogArt = got.firstOrNull()?.artwork
                }
                if (catalogTracks.isEmpty()) toast = "Couldn't open that playlist"
            } catch (_: Exception) {
                if (catalogTracks.isEmpty()) toast = "Couldn't open that playlist"
            } finally {
                catalogLoading = false
            }
        }
    }

    fun openPlaylistHit(hit: PlaylistHit) {
        openCatalog(
            title = hit.title ?: "Playlist",
            artist = hit.artist ?: "",
            artwork = hit.artwork,
            playlistId = hit.playlistId,
            query = hit.query ?: hit.title,
            tracks = emptyList(),
        )
    }

    fun loadRadio(q: String = "") {
        radioLoading = true
        viewModelScope.launch {
            try {
                radio = MuchiApi.radio(q).tracks.orEmpty()
            } catch (_: Exception) {
                radio = emptyList()
            } finally {
                radioLoading = false
            }
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        viewModelScope.launch {
            try {
                search = MuchiApi.search(q, country)
            } catch (_: Exception) {
                search = SearchDto()
            } finally {
                searching = false
            }
        }
    }

    fun playList(list: List<Track>, index: Int) {
        if (list.isEmpty()) return
        val i = index.coerceIn(0, list.lastIndex)
        play(list[i], list)
    }

    fun play(track: Track, extras: List<Track> = emptyList()) {
        val list = (listOf(track) + extras.filter { it.id != track.id }).distinctBy { it.id }
        queue.clear()
        queue.addAll(list)
        queueIndex = 0
        startTrack(track)
        viewModelScope.launch {
            try {
                val more = MuchiApi.related(track.title, track.artist, track.id, country)
                val add = more.filter { row -> queue.none { it.id == row.id } }
                queue.addAll(add)
            } catch (_: Exception) {}
        }
    }

    private fun startTrack(track: Track) {
        lyrics = null
        val ctx = getApplication<Application>()
        MuchiPlayer.startService(ctx)
        recents.removeAll { it.id == track.id }
        recents.add(0, track)
        prefs.saveRecents(recents)
        viewModelScope.launch {
            try {
                when (track.source) {
                    "audius" -> {
                        val id = track.trackId ?: track.id.removePrefix("audius:")
                        val url = MuchiApi.audiusStream(id).ifBlank {
                            MuchiApi.proxyStream("https://api.audius.co/v1/tracks/$id/stream?app_name=Muchi")
                        }
                        MuchiPlayer.playUrl(track, url)
                    }
                    "radio" -> {
                        val src = track.streamUrl.orEmpty()
                        if (src.isNotBlank()) MuchiPlayer.playUrl(track, MuchiApi.proxyStream(src))
                    }
                    "youtube" -> MuchiPlayer.playYouTube(track)
                    else -> {
                        val q = track.playQuery ?: "${track.title} ${track.artist} official audio"
                        val yt = MuchiApi.ytSearch(q, country).firstOrNull()
                        if (yt != null) MuchiPlayer.playYouTube(yt.copy(title = track.title, artist = track.artist, artwork = track.artwork ?: yt.artwork))
                        else toast = "No official playable source"
                    }
                }
            } catch (e: Exception) {
                toast = e.message ?: "Could not play"
            }
        }
    }

    fun toggle() = MuchiPlayer.toggle()

    fun next() {
        if (queueIndex + 1 < queue.size) {
            queueIndex++
            startTrack(queue[queueIndex])
        }
    }

    fun prev() {
        if (queueIndex > 0) {
            queueIndex--
            startTrack(queue[queueIndex])
        }
    }

    fun clearQueue() {
        if (queueIndex >= 0 && queueIndex < queue.size) {
            val keep = queue[queueIndex]
            queue.clear()
            queue.add(keep)
            queueIndex = 0
        } else {
            queue.clear()
            queueIndex = -1
        }
    }

    fun isLiked(t: Track?) = t != null && liked.any { it.id == t.id }

    fun toggleLike(t: Track) {
        val i = liked.indexOfFirst { it.id == t.id }
        if (i >= 0) liked.removeAt(i) else liked.add(0, t)
        prefs.saveLiked(liked)
    }

    fun addToPlaylist(t: Track, pl: UserPlaylist) {
        if (pl.tracks.none { it.id == t.id }) pl.tracks.add(t)
        prefs.savePlaylists(playlists)
        toast = "Added to ${pl.name}"
    }

    fun newPlaylist(name: String) {
        playlists.add(0, UserPlaylist(UUID.randomUUID().toString(), name.ifBlank { "Playlist" }))
        prefs.savePlaylists(playlists)
    }

    fun loadLyrics() {
        val t = now ?: return
        viewModelScope.launch {
            try { lyrics = MuchiApi.lyrics(t.title, t.artist) } catch (_: Exception) {}
        }
    }

    fun signIn(ctx: Context) {
        authMsg = null
        viewModelScope.launch {
            try {
                val u = GoogleAuth.signIn(ctx)
                google = u
                prefs.saveGoogle(u)
                toast = "Signed in as ${u.name}"
            } catch (e: Exception) {
                authMsg = e.message
            }
        }
    }

    fun signOut() {
        google = null
        prefs.saveGoogle(null)
    }

    fun setAlerts(on: Boolean) {
        followAlerts = on
        prefs.setFollowAlerts(on)
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                MuchiPlayer.tick()
                delay(500)
            }
        }
    }
}
