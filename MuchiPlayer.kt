package app.muchi.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import app.muchi.music.BuildConfig
import app.muchi.music.MainActivity
import app.muchi.music.R
import app.muchi.music.data.Track
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object MuchiPlayer {
    const val NOTICE_ID = 77
    const val CHANNEL_ID = "muchi_playback"
    const val ACTION_PLAY = "app.muchi.music.PLAY"
    const val ACTION_PAUSE = "app.muchi.music.PAUSE"
    const val ACTION_NEXT = "app.muchi.music.NEXT"
    const val ACTION_PREV = "app.muchi.music.PREV"
    const val ACTION_STOP = "app.muchi.music.STOP"

    lateinit var exo: ExoPlayer
        private set
    lateinit var router: RouterPlayer
        private set
    var session: MediaSession? = null
        private set

    private val _now = MutableStateFlow<Track?>(null)
    val now: StateFlow<Track?> = _now
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    private val _progress = MutableStateFlow(0L to 0L)
    val progress: StateFlow<Pair<Long, Long>> = _progress
    private val _ytId = MutableStateFlow<String?>(null)
    val ytId: StateFlow<String?> = _ytId

    var youtubeVideoId: String?
        get() = _ytId.value
        private set(v) { _ytId.value = v }

    var onEnded: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null
    var onSkipPrev: (() -> Unit)? = null

    private var yt: YouTubePlayer? = null
    private var artBmp: Bitmap? = null
    private var app: Context? = null
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(ctx: Context) {
        if (this::exo.isInitialized) return
        app = ctx.applicationContext
        ensureChannel(ctx.applicationContext)
        exo = ExoPlayer.Builder(ctx.applicationContext)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()
        router = RouterPlayer(exo)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!router.ytMode) {
                    _playing.value = isPlaying
                    notifyNow()
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!router.ytMode && playbackState == Player.STATE_ENDED) onEnded?.invoke()
            }
        })
        val open = PendingIntent.getActivity(
            ctx.applicationContext,
            0,
            Intent(ctx.applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaSession.Builder(ctx.applicationContext, router)
            .setId("muchi")
            .setSessionActivity(open)
            .build()
    }

    fun attachYoutube(player: YouTubePlayer) {
        yt = player
        val id = youtubeVideoId
        if (id != null) player.loadVideo(id, 0f)
    }

    fun onYoutubeState(state: PlayerConstants.PlayerState) {
        when (state) {
            PlayerConstants.PlayerState.PLAYING -> {
                router.ytMode = true
                router.ytOn = true
                _playing.value = true
                notifyNow()
            }
            PlayerConstants.PlayerState.PAUSED, PlayerConstants.PlayerState.VIDEO_CUED -> {
                router.ytOn = false
                _playing.value = false
                notifyNow()
            }
            PlayerConstants.PlayerState.ENDED -> onEnded?.invoke()
            else -> {}
        }
    }

    fun playUrl(track: Track, url: String) {
        router.ytMode = false
        router.ytOn = false
        youtubeVideoId = null
        try { yt?.pause() } catch (_: Exception) {}
        _now.value = track
        _playing.value = true
        exo.setMediaItem(mediaItem(track, url))
        exo.prepare()
        exo.play()
        loadArt(track)
        notifyNow()
    }

    fun playYouTube(track: Track) {
        exo.pause()
        router.ytMode = true
        router.ytOn = true
        val id = track.videoId ?: return
        youtubeVideoId = id
        _now.value = track
        _playing.value = true
        try { yt?.loadVideo(id, 0f) } catch (_: Exception) {}
        loadArt(track)
        notifyNow()
    }

    fun toggle() {
        if (router.ytMode) {
            if (_playing.value) pause() else play()
        } else {
            if (exo.isPlaying) exo.pause() else exo.play()
        }
    }

    fun play() {
        if (router.ytMode) {
            router.ytOn = true
            _playing.value = true
            try { yt?.play() } catch (_: Exception) {}
        } else {
            exo.play()
        }
        notifyNow()
    }

    fun pause() {
        if (router.ytMode) {
            router.ytOn = false
            _playing.value = false
            try { yt?.pause() } catch (_: Exception) {}
        } else {
            exo.pause()
        }
        notifyNow()
    }

    fun setYoutubePlaying(on: Boolean) {
        router.ytMode = true
        router.ytOn = on
        _playing.value = on
        notifyNow()
    }

    fun shouldKeepAlive(): Boolean {
        if (_now.value == null) return false
        if (router.ytMode) return _playing.value
        return this::exo.isInitialized && (exo.isPlaying || exo.playWhenReady)
    }

    fun startService(ctx: Context) {
        init(ctx)
        val i = Intent(ctx, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    }

    fun tick() {
        if (!router.ytMode && this::exo.isInitialized) {
            _progress.value = exo.currentPosition to exo.duration.coerceAtLeast(0)
        }
    }

    fun handleAction(action: String?) {
        when (action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> onSkipNext?.invoke()
            ACTION_PREV -> onSkipPrev?.invoke()
            ACTION_STOP -> {
                pause()
                _now.value = null
                youtubeVideoId = null
            }
        }
    }

    fun placeholderNotification(ctx: Context): Notification {
        ensureChannel(ctx)
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_muchi)
            .setContentTitle("Muchi")
            .setContentText("Starting playback")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun notifyNow() {
        val ctx = app ?: return
        val svc = PlaybackService.instance ?: return
        val t = _now.value
        if (t == null) {
            ServiceCompat.stopForeground(svc, ServiceCompat.STOP_FOREGROUND_REMOVE)
            return
        }
        val playAction = if (_playing.value) {
            action(ctx, ACTION_PAUSE, android.R.drawable.ic_media_pause, "Pause")
        } else {
            action(ctx, ACTION_PLAY, android.R.drawable.ic_media_play, "Play")
        }
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_muchi)
            .setContentTitle(t.title.ifBlank { "Muchi" })
            .setContentText(t.artist.ifBlank { "Now playing" })
            .setLargeIcon(artBmp)
            .setOngoing(_playing.value)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .addAction(action(ctx, ACTION_PREV, android.R.drawable.ic_media_previous, "Previous"))
            .addAction(playAction)
            .addAction(action(ctx, ACTION_NEXT, android.R.drawable.ic_media_next, "Next"))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val n = builder.build()
        try {
            ServiceCompat.startForeground(
                svc,
                NOTICE_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (_: Exception) {
            svc.startForeground(NOTICE_ID, n)
        }
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 8,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun action(ctx: Context, act: String, icon: Int, title: String): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            ctx, act.hashCode(),
            Intent(ctx, PlaybackService::class.java).setAction(act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(icon, title, pi)
    }

    private fun mediaItem(track: Track, url: String): MediaItem {
        val art = absArt(track.artwork)
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(art?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(url)
            .setMediaMetadata(meta)
            .build()
    }

    private fun absArt(src: String?): String? {
        if (src.isNullOrBlank()) return null
        if (src.startsWith("http")) return src
        return BuildConfig.API_BASE.trimEnd('/') + src
    }

    private fun loadArt(track: Track) {
        val src = absArt(track.artwork) ?: return
        io.launch {
            val bmp = decodeArt(src)
            artBmp = bmp
            withContext(Dispatchers.Main) { notifyNow() }
        }
    }

    private fun decodeArt(src: String): Bitmap? = try {
        val c = URL(src).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.instanceFollowRedirects = true
        c.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.playback_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Now playing"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    class RouterPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        @Volatile var ytMode = false
        @Volatile var ytOn = false

        override fun play() {
            if (ytMode) MuchiPlayer.play() else super.play()
        }
        override fun pause() {
            if (ytMode) MuchiPlayer.pause() else super.pause()
        }
        override fun isPlaying(): Boolean = if (ytMode) ytOn else super.isPlaying()
        override fun getPlayWhenReady(): Boolean = if (ytMode) ytOn else super.getPlayWhenReady()
        override fun getPlaybackState(): Int = if (ytMode) Player.STATE_READY else super.getPlaybackState()
        override fun seekToNext() { MuchiPlayer.onSkipNext?.invoke() }
        override fun seekToPrevious() { MuchiPlayer.onSkipPrev?.invoke() }
        override fun seekToNextMediaItem() { MuchiPlayer.onSkipNext?.invoke() }
        override fun seekToPreviousMediaItem() { MuchiPlayer.onSkipPrev?.invoke() }
        override fun hasNextMediaItem() = true
        override fun hasPreviousMediaItem() = true
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(COMMAND_PLAY_PAUSE)
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(COMMAND_STOP)
                .build()
    }
}
