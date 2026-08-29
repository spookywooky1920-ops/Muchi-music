package app.muchi.music.data

import app.muchi.music.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object MuchiApi {
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(18, TimeUnit.SECONDS)
        .readTimeout(28, TimeUnit.SECONDS)
        .build()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else BuildConfig.API_BASE + path
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException("HTTP ${res.code}")
            body
        }
    }

    suspend fun home(gl: String): HomeDto =
        gson.fromJson(get("/api/home?gl=${enc(gl)}"), HomeDto::class.java) ?: HomeDto()

    suspend fun search(q: String, gl: String): SearchDto =
        gson.fromJson(get("/api/search?q=${enc(q)}&gl=${enc(gl)}"), SearchDto::class.java) ?: SearchDto()

    suspend fun radio(q: String = ""): RadioDto =
        gson.fromJson(get("/api/radio?q=${enc(q)}"), RadioDto::class.java) ?: RadioDto()

    suspend fun related(title: String, artist: String, skip: String, gl: String): List<Track> {
        val body = get("/api/related?title=${enc(title)}&artist=${enc(artist)}&skip=${enc(skip)}&gl=${enc(gl)}")
        return gson.fromJson(body, RelatedDto::class.java)?.tracks.orEmpty()
    }

    suspend fun lyrics(title: String, artist: String): LyricsDto =
        gson.fromJson(get("/api/lyrics?title=${enc(title)}&artist=${enc(artist)}"), LyricsDto::class.java)
            ?: LyricsDto()

    suspend fun audiusStream(trackId: String): String {
        val body = get("/api/audius/stream/${enc(trackId)}")
        return gson.fromJson(body, StreamDto::class.java)?.url.orEmpty()
    }

    suspend fun ytPlaylist(id: String): List<Track> {
        val body = get("/api/yt/playlist?id=${enc(id)}")
        return gson.fromJson(body, PlaylistDto::class.java)?.tracks.orEmpty()
    }

    suspend fun shelf(id: String, q: String): List<Track> {
        val body = get("/api/shelf?id=${enc(id)}&q=${enc(q)}&full=1&gl=US")
        return gson.fromJson(body, PlaylistDto::class.java)?.tracks.orEmpty()
    }

    suspend fun ytSearch(q: String, gl: String): List<Track> {
        val body = get("/api/youtube/search?q=${enc(q)}&gl=${enc(gl)}")
        val map = gson.fromJson(body, RelatedDto::class.java)
        return map?.tracks.orEmpty()
    }

    fun proxyStream(url: String): String =
        BuildConfig.API_BASE + "/api/stream?url=" + enc(url)
}
