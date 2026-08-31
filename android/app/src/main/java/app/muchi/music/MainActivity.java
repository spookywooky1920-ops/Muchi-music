package app.muchi.music;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    // IMPORTANT: plugins MUST be registered before the bridge loads
    // (super.onCreate) or they never get bound — the native Media3
    // background player silently wouldn't exist.
    registerPlugin(MuchiAudioPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
