package app.muchi.music;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionResult;

import com.getcapacitor.JSObject;

/**
 * MUCHI real background audio — Android native media layer.
 *
 * A Media3 (ExoPlayer) foreground {@link MediaSessionService} that keeps
 * playing while the app is backgrounded, the screen is off, or the phone
 * is locked. The WebView JS stays the single source of truth for WHAT
 * plays: this service only renders audio and the OS media notification /
 * lock-screen / headset / Bluetooth controls, and every system action is
 * echoed back to the JS layer (muchiControls events), which drives the
 * app's own playback functions (togglePlay/next/prev/seekTo).
 *
 * Lifecycle: started via startForegroundService(ACTION_PLAY). Media3
 * promotes the service to foreground automatically while the session is
 * active (playing). Audio focus, ducking and media-button routing are
 * handled by ExoPlayer + MediaSession out of the box.
 *
 * Built against Media3 1.5.1 (media3-session): MediaSession.Callback is an
 * interface whose default onConnect() accepts every controller, and media
 * button / notification actions arrive via onPlayerCommandRequest().
 */
public class MuchiAudioService extends MediaSessionService {

  public static final String ACTION_PLAY = "app.muchi.music.action.PLAY";
  public static final String EXTRA_URL = "url";
  public static final String EXTRA_TITLE = "title";
  public static final String EXTRA_ARTIST = "artist";
  public static final String EXTRA_ARTWORK = "artwork";
  public static final String EXTRA_DURATION = "duration";

  private static MuchiAudioService current;

  private ExoPlayer player;
  private MediaSession session;
  private final Handler ticker = new Handler(Looper.getMainLooper());
  private final Runnable tickerRun = new Runnable() {
    @Override public void run() {
      if (player != null) {
        int st = player.getPlaybackState();
        if (player.isPlaying() || st == Player.STATE_READY || st == Player.STATE_BUFFERING) {
          JSObject o = new JSObject();
          o.put("positionMs", player.getCurrentPosition());
          long d = player.getDuration();
          o.put("durationMs", d > 0 ? d : 0);
          o.put("playing", player.isPlaying());
          emit("muchiProgress", o);
        }
      }
      ticker.postDelayed(this, 1000L);
    }
  };

  public static MuchiAudioService current() {
    return current;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    current = this;
    player = new ExoPlayer.Builder(this).build();
    session = new MediaSession.Builder(this, player)
        .setCallback(new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, MediaSession.ControllerInfo controllerInfo) {
            // Accept the controller and make tapping the media notification
            // return to the app. (Media3 1.5.1: session activity is part of
            // the connection result, not MediaSession.)
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setSessionActivity(PendingIntent.getActivity(
                    MuchiAudioService.this, 0,
                    new Intent(MuchiAudioService.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();
          }

          // Media3 1.5.1: notification / lock-screen / headset actions arrive
          // here as player commands. Echo them to JS (which is the single
          // source of truth for the queue); returning RESULT_SUCCESS lets the
          // command reach the player too.
          @Override
          public int onPlayerCommandRequest(
              MediaSession session, MediaSession.ControllerInfo controllerInfo, int playerCommand) {
            switch (playerCommand) {
              case Player.COMMAND_PLAY_PAUSE:
                // The command hasn't run yet: report the state it will produce.
                emitControl(player != null && player.isPlaying() ? "pause" : "play");
                break;
              case Player.COMMAND_SEEK_TO_NEXT:
              case Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM:
                emitControl("next");
                break;
              case Player.COMMAND_SEEK_TO_PREVIOUS:
              case Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM:
                emitControl("previous");
                break;
              case Player.COMMAND_STOP:
                emitControl("stop");
                break;
              default:
                break;
            }
            return SessionResult.RESULT_SUCCESS;
          }
        })
        .build();

    // The default Media3 notification provider shows Previous / Play-Pause /
    // Next (plus the seekbar) automatically, based on the player's commands.

    player.addListener(new Player.Listener() {
      @Override public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_ENDED) emitControl("ended");
      }

      @Override public void onPlayerError(PlaybackException error) {
        emitControl("error");
      }
    });

    ticker.post(tickerRun);
  }

  @Override
  public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
    return session;
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    if (intent != null && ACTION_PLAY.equals(intent.getAction())) {
      play(
          intent.getStringExtra(EXTRA_URL),
          intent.getStringExtra(EXTRA_TITLE),
          intent.getStringExtra(EXTRA_ARTIST),
          intent.getStringExtra(EXTRA_ARTWORK),
          intent.getLongExtra(EXTRA_DURATION, 0L));
    }
    return START_NOT_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    if (player != null) player.pause();
    super.onTaskRemoved(rootIntent);
  }

  @Override
  public void onDestroy() {
    ticker.removeCallbacks(tickerRun);
    if (session != null) session.release();
    if (player != null) player.release();
    if (current == this) current = null;
    super.onDestroy();
  }

  /** Loads and starts playing one track (replaces whatever was playing). */
  public void play(String url, String title, String artist, String artwork, long durationMs) {
    if (player == null || url == null || url.isEmpty()) return;
    MediaMetadata.Builder meta = new MediaMetadata.Builder()
        .setTitle((title == null || title.isEmpty()) ? "Muchi" : title)
        .setArtist(artist == null ? "" : artist)
        .setAlbumTitle("Muchi");
    if (artwork != null && !artwork.isEmpty()
        && (artwork.startsWith("https:") || artwork.startsWith("http:"))) {
      try { meta.setArtworkUri(Uri.parse(artwork)); } catch (Throwable t) { /* ignore */ }
    }
    MediaItem item = new MediaItem.Builder()
        .setMediaId(url)
        .setUri(url)
        .setMediaMetadata(meta.build())
        .build();
    player.setMediaItem(item);
    player.prepare();
    player.setPlayWhenReady(true);
  }

  public void pause() {
    if (player != null && player.isPlaying()) player.pause();
  }

  public void resume() {
    if (player != null && !player.isPlaying() && player.getPlaybackState() != Player.STATE_ENDED) {
      player.play();
    }
  }

  public void seekTo(long positionMs) {
    if (player != null) player.seekTo(positionMs);
  }

  public void stopPlayback() {
    if (player == null) return;
    player.stop();
    player.clearMediaItems();
  }

  public long getPositionMs() {
    return player == null ? 0 : player.getCurrentPosition();
  }

  public long getDurationMs() {
    return player == null ? 0 : player.getDuration();
  }

  public boolean isPlaying() {
    return player != null && player.isPlaying();
  }

  private void emitControl(String action) {
    JSObject o = new JSObject();
    o.put("action", action);
    emit("muchiControls", o);
  }

  private static void emit(String event, JSObject data) {
    MuchiAudioPlugin.emit(event, data);
  }
}
