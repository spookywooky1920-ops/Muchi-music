package app.muchi.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import app.muchi.music.player.MuchiPlayer
import app.muchi.music.ui.MuchiRoot
import app.muchi.music.ui.theme.MuchiTheme

class MainActivity : ComponentActivity() {
    private val vm: MuchiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MuchiPlayer.init(applicationContext)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                71
            )
        }
        setContent {
            MuchiTheme {
                MuchiRoot(vm)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (MuchiPlayer.shouldKeepAlive()) MuchiPlayer.startService(this)
    }

    override fun onStop() {
        if (MuchiPlayer.shouldKeepAlive()) MuchiPlayer.startService(this)
        super.onStop()
    }
}
