package app.muchi.music.player

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)

class PlaybackService : MediaSessionService() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        MuchiPlayer.init(this)
        try {
            ServiceCompat.startForeground(
                this,
                MuchiPlayer.NOTICE_ID,
                MuchiPlayer.placeholderNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (_: Exception) {
            startForeground(MuchiPlayer.NOTICE_ID, MuchiPlayer.placeholderNotification(this))
        }
        MuchiPlayer.notifyNow()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return MuchiPlayer.session
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        MuchiPlayer.notifyNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) MuchiPlayer.handleAction(intent.action)
        super.onStartCommand(intent, flags, startId)
        MuchiPlayer.notifyNow()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!MuchiPlayer.shouldKeepAlive()) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: PlaybackService? = null
    }
}
