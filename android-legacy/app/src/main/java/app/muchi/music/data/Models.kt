package app.muchi.music.data

data class Track(
    val id: String = "",
    val source: String = "",
    val videoId: String? = null,
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String? = "",
    val duration: Long = 0,
    val artwork: String? = "",
    val streamUrl: String? = null,
    val playQuery: String? = null,
    val stationId: String? = null,
)

data class Mood(
    val id: String? = null,
    val title: String? = null,
    val query: String? = null,
    val color: String? = null,
)

data class Shelf(
    val id: String? = null,
    val title: String? = null,
    val query: String? = null,
    val tracks: List<Track>? = emptyList(),
)

data class ArtistHit(
    val id: String? = null,
    val name: String? = null,
    val artwork: String? = null,
    val query: String? = null,
    val source: String? = null,
)

data class PlaylistHit(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artwork: String? = null,
    val playlistId: String? = null,
    val query: String? = null,
)

data class HomeDto(
    val country: String? = null,
    val day: String? = null,
    val shelves: List<Shelf>? = emptyList(),
    val youtubeLocal: List<Track>? = emptyList(),
    val youtubeCharts: List<Track>? = emptyList(),
    val countryPlaylists: List<PlaylistHit>? = emptyList(),
    val globalPlaylists: List<PlaylistHit>? = emptyList(),
    val audius: List<Track>? = emptyList(),
    val underground: List<Track>? = emptyList(),
    val radio: List<Track>? = emptyList(),
    val moods: List<Mood>? = emptyList(),
)

data class PlaylistDto(
    val tracks: List<Track>? = emptyList(),
    val playlistId: String? = null,
)

data class SearchDto(
    val youtube: List<Track>? = emptyList(),
    val audius: List<Track>? = emptyList(),
    val radio: List<Track>? = emptyList(),
    val apple: List<Track>? = emptyList(),
    val artists: List<ArtistHit>? = emptyList(),
    val playlists: List<PlaylistHit>? = emptyList(),
)

data class RadioDto(val tracks: List<Track>? = emptyList())
data class StreamDto(val url: String? = null)
data class LyricsDto(val lyrics: String? = "", val title: String? = null, val artist: String? = null)
data class RelatedDto(val tracks: List<Track>? = emptyList())
data class UserPlaylist(
    val id: String,
    val name: String,
    val tracks: MutableList<Track> = mutableListOf(),
)
data class GoogleUser(
    val id: String,
    val name: String,
    val email: String,
    val photo: String,
)
