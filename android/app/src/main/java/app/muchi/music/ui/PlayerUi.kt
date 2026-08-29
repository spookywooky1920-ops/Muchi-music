package app.muchi.music.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.muchi.music.MuchiViewModel
import app.muchi.music.player.MuchiPlayer
import app.muchi.music.ui.theme.Cream
import app.muchi.music.ui.theme.GreenCard
import app.muchi.music.ui.theme.GreenSurface
import app.muchi.music.ui.theme.Lime
import app.muchi.music.ui.theme.Mute
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun MiniPlayer(vm: MuchiViewModel) {
    val t = vm.now ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GreenSurface)
            .clickable { vm.screen = "now" }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.artwork, 48, 8)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Cream, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(t.artist, color = Mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.toggle() }, modifier = Modifier.size(44.dp)) {
            Icon(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.playing) "Pause" else "Play", tint = Cream)
        }
        IconButton(onClick = { vm.next() }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.SkipNext, "Next", tint = Cream)
        }
    }
}

@Composable
fun NowPlayingScreen(vm: MuchiViewModel) {
    val t = vm.now
    val prog by MuchiPlayer.progress.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = "main" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Cream)
            }
            Text("Now playing", color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (t == null) {
            Text("Nothing playing", color = Mute, modifier = Modifier.padding(24.dp))
            return
        }
        val yt by MuchiPlayer.ytId.collectAsState()
        if (yt == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Cover(t.artwork, 280, 16)
            }
            Spacer(Modifier.height(20.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Text(t.title, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(t.artist, color = Mute, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.prev() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", tint = Cream, modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = { vm.toggle() },
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(Lime)
            ) {
                Icon(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = GreenCard, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", tint = Cream, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.toggleLike(t) }, modifier = Modifier.size(52.dp)) {
                Icon(
                    if (vm.isLiked(t)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Like",
                    tint = if (vm.isLiked(t)) Lime else Cream
                )
            }
        }
        if (prog.second > 0 && yt == null) {
            val p = (prog.first.toFloat() / prog.second).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(GreenCard)) {
                Box(Modifier.fillMaxWidth(p).height(4.dp).background(Lime))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.loadLyrics(); vm.screen = "lyrics" }) { Text("Lyrics", color = Lime) }
            TextButton(onClick = { vm.clearQueue() }) { Text("Clear queue", color = Mute) }
        }
        if (vm.queue.size > 1) {
            Text("Up next", color = Cream, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            vm.queue.drop(vm.queueIndex + 1).take(12).forEach { row ->
                TrackRow(row, onPlay = { vm.play(row, vm.queue) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LyricsScreen(vm: MuchiViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = "now" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Cream)
            }
            Text("Lyrics", color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        val text = vm.lyrics?.lyrics.orEmpty()
        Text(
            if (text.isBlank()) "No lyrics for this song." else text,
            color = Cream,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        )
    }
}

@Composable
fun PersistentYoutubeHost(modifier: Modifier = Modifier) {
    val videoId by MuchiPlayer.ytId.collectAsState()
    if (videoId == null) return
    AndroidView(
        factory = { ctx ->
            YouTubePlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        MuchiPlayer.attachYoutube(youTubePlayer)
                    }
                    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                        MuchiPlayer.onYoutubeState(state)
                    }
                })
            }
        },
        update = { },
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}
