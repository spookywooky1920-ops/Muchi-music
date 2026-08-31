package app.muchi.music

import android.app.Application
import app.muchi.music.player.MuchiPlayer

class MuchiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MuchiPlayer.init(this)
    }
}
