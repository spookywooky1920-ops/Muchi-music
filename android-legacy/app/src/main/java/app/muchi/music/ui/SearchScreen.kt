package app.muchi.music.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.muchi.music.MuchiViewModel
import app.muchi.music.ui.theme.Cream
import app.muchi.music.ui.theme.GreenCard
import app.muchi.music.ui.theme.Lime
import app.muchi.music.ui.theme.Mute

@Composable
fun SearchScreen(vm: MuchiViewModel) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Text(
            "Search",
            color = Cream,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Songs, artists, radio") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Mute) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.runSearch()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lime,
                unfocusedBorderColor = GreenCard,
                focusedTextColor = Cream,
                unfocusedTextColor = Cream,
                cursorColor = Lime
            )
        )
        if (vm.searching) {
            CircularProgressIndicator(color = Lime, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        } else {
            val s = vm.search
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                if (s == null) {
                    item {
                        Text("History", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
                    }
                    items(vm.recents.take(20), key = { it.id }) { t ->
                        TrackRow(t, onPlay = { vm.play(t, vm.recents) })
                    }
                    if (vm.recents.isEmpty()) {
                        item { Text("Play something — it shows up here.", color = Mute, modifier = Modifier.padding(16.dp)) }
                    }
                } else {
                    val songs = (s.youtube.orEmpty() + s.apple.orEmpty() + s.audius.orEmpty()).distinctBy { it.id }
                    if (songs.isNotEmpty()) {
                        item { Text("Songs", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
                        items(songs, key = { it.id }) { t -> TrackRow(t, onPlay = { vm.play(t, songs) }) }
                    }
                    val artists = s.artists.orEmpty()
                    if (artists.isNotEmpty()) {
                        item { Text("Artists", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 12.dp)) }
                        items(artists, key = { it.id ?: it.name ?: "" }) { a ->
                            TrackRow(
                                app.muchi.music.data.Track(
                                    id = a.id ?: a.name.orEmpty(),
                                    title = a.name.orEmpty(),
                                    artist = "Artist",
                                    artwork = a.artwork,
                                    playQuery = a.query ?: a.name
                                ),
                                onPlay = {
                                    vm.query = a.query ?: a.name.orEmpty()
                                    vm.runSearch()
                                }
                            )
                        }
                    }
                    val playlists = s.playlists.orEmpty()
                    if (playlists.isNotEmpty()) {
                        item { Text("Playlists", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 12.dp)) }
                        items(playlists, key = { it.playlistId ?: it.id ?: it.title ?: "" }) { p ->
                            TrackRow(
                                app.muchi.music.data.Track(
                                    id = p.id ?: p.playlistId.orEmpty(),
                                    title = p.title.orEmpty(),
                                    artist = p.artist ?: "Playlist",
                                    artwork = p.artwork,
                                ),
                                onPlay = { vm.openPlaylistHit(p) }
                            )
                        }
                    }
                    val radioHits = s.radio.orEmpty()
                    if (radioHits.isNotEmpty()) {
                        item { Text("Radio", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 12.dp)) }
                        items(radioHits, key = { it.id }) { t -> TrackRow(t, onPlay = { vm.play(t) }) }
                    }
                    if (songs.isEmpty() && artists.isEmpty() && playlists.isEmpty() && radioHits.isEmpty()) {
                        item { Text("No music for that search.", color = Mute, modifier = Modifier.padding(16.dp)) }
                    }
                }
            }
        }
    }
}
