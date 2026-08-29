package app.muchi.music.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("muchi", Context.MODE_PRIVATE)
    private val gson = Gson()

    var country: String
        get() = sp.getString("country", "IN") ?: "IN"
        set(v) { sp.edit().putString("country", v).apply() }

    var googleJson: String
        get() = sp.getString("google", "") ?: ""
        set(v) { sp.edit().putString("google", v).apply() }

    fun googleUser(): GoogleUser? {
        val s = googleJson
        if (s.isBlank()) return null
        return try { gson.fromJson(s, GoogleUser::class.java) } catch (_: Exception) { null }
    }

    fun saveGoogle(u: GoogleUser?) {
        googleJson = if (u == null) "" else gson.toJson(u)
    }

    fun liked(): MutableList<Track> = list("liked")
    fun recents(): MutableList<Track> = list("recents")
    fun saveLiked(rows: List<Track>) = saveList("liked", rows)
    fun saveRecents(rows: List<Track>) = saveList("recents", rows)

    fun playlists(): MutableList<UserPlaylist> {
        val raw = sp.getString("playlists", "[]") ?: "[]"
        return try {
            gson.fromJson(raw, object : TypeToken<MutableList<UserPlaylist>>() {}.type)
                ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }
    }

    fun savePlaylists(rows: List<UserPlaylist>) {
        sp.edit().putString("playlists", gson.toJson(rows)).apply()
    }

    fun followAlerts(): Boolean = sp.getBoolean("followAlerts", true)
    fun setFollowAlerts(v: Boolean) { sp.edit().putBoolean("followAlerts", v).apply() }

    private fun list(key: String): MutableList<Track> {
        val raw = sp.getString(key, "[]") ?: "[]"
        return try {
            gson.fromJson(raw, object : TypeToken<MutableList<Track>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveList(key: String, rows: List<Track>) {
        sp.edit().putString(key, gson.toJson(rows.take(200))).apply()
    }
}
