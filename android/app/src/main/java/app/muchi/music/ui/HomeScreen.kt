package app.muchi.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.muchi.music.MuchiViewModel
import app.muchi.music.ui.theme.Cream
import app.muchi.music.ui.theme.GreenCard
import app.muchi.music.ui.theme.Lime
import app.muchi.music.ui.theme.Mute
import coil.compose.AsyncImage

@Composable
fun HomeScreen(vm: MuchiViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(vm.greeting(), color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Made for your ears", color = Mute, fontSize = 13.sp)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GreenCard)
                    .clickable { vm.screen = "settings" },
                contentAlignment = Alignment.Center
            ) {
                val g = vm.google
                if (g != null && g.photo.isNotBlank()) {
                    AsyncImage(g.photo, "Profile", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(
                        (g?.name ?: "M").take(1).uppercase(),
                        color = Lime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        when {
            vm.loadingHome && vm.home == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lime)
            }
            vm.homeError != null && vm.home == null -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(vm.homeError ?: "", color = Mute)
                TextButton(onClick = { vm.loadHome() }) { Text("Retry", color = Lime) }
            }
            else -> {
                val h = vm.home
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    val moods = h?.moods.orEmpty()
                    if (moods.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(moods, key = { it.id ?: it.title ?: "" }) { m ->
                                    Text(
                                        m.title ?: "",
                                        color = Cream,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(GreenCard)
                                            .clickable {
                                                vm.query = m.query ?: m.title.orEmpty()
                                                vm.tab = 1
                                                vm.runSearch()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    val local = h?.youtubeLocal.orEmpty()
                    val countryPl = h?.countryPlaylists.orEmpty()
                    if (countryPl.isNotEmpty()) {
                        item { PlaylistShelf("Trending in your country", countryPl) { vm.openPlaylistHit(it) } }
                    }
                    if (local.isNotEmpty()) {
                        item {
                            ShelfRow("Top songs in your country", local, onSeeAll = {
                                vm.openCatalog("Top songs in your country", tracks = local)
                            }) { t, list -> vm.play(t, list) }
                        }
                    }
                    val globalPl = h?.globalPlaylists.orEmpty()
                    if (globalPl.isNotEmpty()) {
                        item { PlaylistShelf("Global trending playlists", globalPl) { vm.openPlaylistHit(it) } }
                    }
                    h?.shelves.orEmpty().forEach { shelf ->
                        val rows = shelf.tracks.orEmpty()
                        item {
                            ShelfRow(shelf.title ?: "Mix", rows, onSeeAll = {
                                vm.openCatalog(
                                    title = shelf.title ?: "Playlist",
                                    query = shelf.query,
                                    tracks = rows,
                                    shelfId = shelf.id,
                                )
                            }) { t, list -> vm.play(t, list) }
                        }
                    }
                    val au = h?.audius.orEmpty()
                    if (au.isNotEmpty()) item {
                        ShelfRow("Independent on Audius", au, onSeeAll = {
                            vm.openCatalog("Independent artists", tracks = au)
                        }) { t, list -> vm.play(t, list) }
                    }
                    val ug = h?.underground.orEmpty()
                    if (ug.isNotEmpty()) item {
                        ShelfRow("Underground", ug, onSeeAll = {
                            vm.openCatalog("Underground", tracks = ug)
                        }) { t, list -> vm.play(t, list) }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
