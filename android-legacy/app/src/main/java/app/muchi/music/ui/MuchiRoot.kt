package app.muchi.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.muchi.music.MuchiViewModel
import app.muchi.music.player.MuchiPlayer
import app.muchi.music.ui.theme.Cream
import app.muchi.music.ui.theme.GreenBg
import app.muchi.music.ui.theme.GreenSurface
import app.muchi.music.ui.theme.Lime
import app.muchi.music.ui.theme.Mute

private data class Tab(val label: String, val filled: ImageVector, val outline: ImageVector)

@Composable
fun MuchiRoot(vm: MuchiViewModel) {
    val snack = remember { SnackbarHostState() }
    BackHandler(enabled = vm.screen != "main" || vm.tab != 0) {
        when (vm.screen) {
            "catalog" -> vm.screen = "main"
            "lyrics" -> vm.screen = "now"
            "now", "settings" -> vm.screen = "main"
            else -> vm.tab = 0
        }
    }
    LaunchedEffect(vm.toast) {
        val msg = vm.toast ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        vm.toast = null
    }
    val tabs = listOf(
        Tab("Home", Icons.Filled.Home, Icons.Outlined.Home),
        Tab("Search", Icons.Filled.Search, Icons.Outlined.Search),
        Tab("Radio", Icons.Filled.Radio, Icons.Outlined.Radio),
        Tab("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    )
    Scaffold(
        modifier = Modifier.fillMaxSize().background(GreenBg).statusBarsPadding(),
        containerColor = GreenBg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (vm.screen == "main") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(GreenBg)
                        .navigationBarsPadding()
                ) {
                    if (vm.now != null) {
                        MiniPlayer(vm)
                        Spacer(Modifier.height(6.dp))
                    }
                    NavigationBar(
                        containerColor = GreenSurface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(72.dp)
                    ) {
                        tabs.forEachIndexed { i, tab ->
                            val on = vm.tab == i
                            NavigationBarItem(
                                selected = on,
                                onClick = { vm.tab = i },
                                icon = {
                                    Icon(
                                        if (on) tab.filled else tab.outline,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(26.dp)
                                    )
                                },
                                label = {
                                    Text(tab.label, fontSize = 11.sp, maxLines = 1)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Lime,
                                    selectedTextColor = Lime,
                                    unselectedIconColor = Mute,
                                    unselectedTextColor = Mute,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val yt by MuchiPlayer.ytId.collectAsState()
            if (yt != null) {
                PersistentYoutubeHost(
                    Modifier
                        .fillMaxWidth()
                        .height(if (vm.screen == "now") 220.dp else 80.dp)
                        .padding(horizontal = if (vm.screen == "now") 20.dp else 10.dp)
                )
            }
            when (vm.screen) {
                "settings" -> SettingsScreen(vm)
                "now" -> NowPlayingScreen(vm)
                "lyrics" -> LyricsScreen(vm)
                "catalog" -> CatalogScreen(vm)
                else -> when (vm.tab) {
                    1 -> SearchScreen(vm)
                    2 -> RadioScreen(vm)
                    3 -> LibraryScreen(vm)
                    else -> HomeScreen(vm)
                }
            }
        }
    }
}
