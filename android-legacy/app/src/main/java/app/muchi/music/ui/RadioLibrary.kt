package app.muchi.music.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.muchi.music.MuchiViewModel
import app.muchi.music.data.UserPlaylist
import app.muchi.music.ui.theme.Cream
import app.muchi.music.ui.theme.Lime
import app.muchi.music.ui.theme.Mute

@Composable
fun RadioScreen(vm: MuchiViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text("Radio", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
        if (vm.radioLoading && vm.radio.isEmpty()) {
            CircularProgressIndicator(color = Lime, modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(vm.radio, key = { it.id }) { t ->
                    TrackRow(t, onPlay = { vm.play(t, vm.radio) })
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(vm: MuchiViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text("Library", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            item {
                Text("Liked songs", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
            }
            if (vm.liked.isEmpty()) {
                item { Text("Like a song from the player.", color = Mute, modifier = Modifier.padding(16.dp, 4.dp)) }
            } else {
                items(vm.liked, key = { it.id }) { t ->
                    TrackRow(t, onPlay = { vm.play(t, vm.liked) }, onMenu = { vm.toggleLike(t) })
                }
            }
            item {
                Text("Playlists", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            }
            items(vm.playlists, key = { it.id }) { p: UserPlaylist ->
                TrackRow(
                    app.muchi.music.data.Track(id = p.id, title = p.name, artist = "${p.tracks.size} songs"),
                    onPlay = {
                        vm.openCatalog(p.name, "Your playlist", p.tracks.firstOrNull()?.artwork, tracks = p.tracks)
                    }
                )
            }
            item {
                Text("Recently played", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            }
            items(vm.recents.take(30), key = { "r" + it.id }) { t ->
                TrackRow(t, onPlay = { vm.play(t, vm.recents) })
            }
        }
    }
}

@Composable
fun CatalogScreen(vm: MuchiViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = "main" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Cream)
            }
            Text("Playlist", color = Mute, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(vm.catalogArt, 96, 12)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.catalogTitle, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (vm.catalogArtist.isNotBlank()) Text(vm.catalogArtist, color = Mute, fontSize = 13.sp)
                Text(
                    if (vm.catalogLoading) "Loading songs…" else "${vm.catalogTracks.size} songs",
                    color = Mute,
                    fontSize = 13.sp
                )
                if (vm.catalogTracks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { vm.playList(vm.catalogTracks.toList(), 0) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                }
            }
        }
        if (vm.catalogLoading && vm.catalogTracks.isEmpty()) {
            CircularProgressIndicator(color = Lime, modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                itemsIndexed(vm.catalogTracks, key = { i, t -> t.id + i }) { i, t ->
                    TrackRow(t, onPlay = { vm.playList(vm.catalogTracks.toList(), i) })
                }
                if (vm.catalogTracks.isEmpty() && !vm.catalogLoading) {
                    item { Text("No songs in this playlist", color = Mute, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}
