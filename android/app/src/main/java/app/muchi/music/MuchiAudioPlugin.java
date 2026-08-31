package app.muchi.music;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Capacitor bridge to {@link MuchiAudioService}.
 *
 * JS (public/app.js) calls play/pause/resume/seekTo/stop with the same
 * stream URLs the web player uses; the service plays them natively in the
 * background. The service echoes system controls back as "muchiControls"
 * and playback position as "muchiProgress" events, which the app routes
 * through its own playback functions (single source of truth).
 */
@CapacitorPlugin(name = "MuchiAudio")
public class MuchiAudioPlugin extends Plugin {

  private static final int PERM_REQ_NOTIFICATIONS = 7101;

  private static MuchiAudioPlugin instance;

  @Override
  public void load() {
    instance = this;
  }

  @Override
  protected void handleOnDestroy() {
    if (instance == this) instance = null;
    super.handleOnDestroy();
  }

  static void emit(String event, JSObject data) {
    MuchiAudioPlugin p = instance;
    if (p != null) {
      try {
        p.notifyListeners(event, data);
      } catch (Throwable t) {
        // ignore
      }
    }
  }

  /** Android 13+ media notification permission — asked once, at first play. */
  private void ensureNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33 && getActivity() != null) {
      if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
          != PackageManager.PERMISSION_GRANTED) {
        try {
          getActivity().requestPermissions(
              new String[] { Manifest.permission.POST_NOTIFICATIONS }, PERM_REQ_NOTIFICATIONS);
        } catch (Throwable t) {
          // ignore
        }
      }
    }
  }

  @PluginMethod
  public void play(PluginCall call) {
    String url = call.getString("url");
    if (url == null || url.isEmpty()) {
      call.reject("url required");
      return;
    }
    String title = call.getString("title", "Muchi");
    String artist = call.getString("artist", "");
    String artwork = call.getString("artwork", "");
    long duration = (long) call.getDouble("duration", 0.0);
    ensureNotificationPermission();
    Intent intent = new Intent(getContext(), MuchiAudioService.class)
        .setAction(MuchiAudioService.ACTION_PLAY)
        .putExtra(MuchiAudioService.EXTRA_URL, url)
        .putExtra(MuchiAudioService.EXTRA_TITLE, title)
        .putExtra(MuchiAudioService.EXTRA_ARTIST, artist)
        .putExtra(MuchiAudioService.EXTRA_ARTWORK, artwork)
        .putExtra(MuchiAudioService.EXTRA_DURATION, duration);
    try {
      ContextCompat.startForegroundService(getContext(), intent);
    } catch (Throwable t) {
      call.reject("start failed: " + t.getMessage());
      return;
    }
    call.resolve();
  }

  @PluginMethod
  public void pause(PluginCall call) {
    MuchiAudioService svc = MuchiAudioService.current();
    if (svc != null) svc.pause();
    call.resolve();
  }

  @PluginMethod
  public void resume(PluginCall call) {
    MuchiAudioService svc = MuchiAudioService.current();
    if (svc != null) svc.resume();
    call.resolve();
  }

  @PluginMethod
  public void seekTo(PluginCall call) {
    double pos = call.getDouble("position", 0.0);
    MuchiAudioService svc = MuchiAudioService.current();
    if (svc != null) svc.seekTo((long) pos);
    call.resolve();
  }

  @PluginMethod
  public void stop(PluginCall call) {
    MuchiAudioService svc = MuchiAudioService.current();
    if (svc != null) svc.stopPlayback();
    call.resolve();
  }

  @PluginMethod
  public void getState(PluginCall call) {
    MuchiAudioService svc = MuchiAudioService.current();
    JSObject o = new JSObject();
    o.put("playing", svc != null && svc.isPlaying());
    o.put("positionMs", svc != null ? svc.getPositionMs() : 0);
    o.put("durationMs", svc != null ? svc.getDurationMs() : 0);
    call.resolve(o);
  }
}
