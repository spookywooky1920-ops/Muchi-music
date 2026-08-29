package app.muchi.music.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.muchi.music.MuchiViewModel
import app.muchi.music.ui.theme.Cream
import app.muchi.music.ui.theme.GreenCard
import app.muchi.music.ui.theme.GreenSurface
import app.muchi.music.ui.theme.Lime
import app.muchi.music.ui.theme.Mute
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MuchiViewModel) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (vm.settingsPage.isNotEmpty()) vm.settingsPage = "" else vm.screen = "main"
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Cream)
            }
            Text(
                when (vm.settingsPage) {
                    "playback" -> "Playback"
                    "listening" -> "Listening"
                    "appearance" -> "Appearance"
                    else -> "Settings"
                },
                color = Cream,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (vm.settingsPage) {
                "playback" -> SubPage("Autoplay related songs after the one you tapped. Fade, speed, and quality stay on this phone.")
                "listening" -> SubPage("Background play and lock-screen controls use Android’s media session. Sleep timer comes next.")
                "appearance" -> SubPage("Dark green is the native Muchi look. More skins will live on this page — not dumped on Settings.")
                else -> {
                    AccountCard(vm) { vm.signIn(ctx) }
                    Spacer(Modifier.height(16.dp))
                    CardBlock {
                        GoRow("Appearance", "Themes live on their own page") { vm.settingsPage = "appearance" }
                        GoRow("Playback", "Autoplay, fade, speed, quality") { vm.settingsPage = "playback" }
                        GoRow("Listening", "Background play, lock screen, sleep") { vm.settingsPage = "listening" }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("CATALOG", color = Mute, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                    CardBlock {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Country", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "One local row on Home. The rest is English hits.",
                                    color = Mute,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            var open by remember { mutableStateOf(false) }
                            val label = vm.countries.find { it.first == vm.country }?.second ?: vm.country
                            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                                TextField(
                                    value = label,
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                                    modifier = Modifier.menuAnchor().width(140.dp),
                                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                                )
                                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    vm.countries.forEach { (code, name) ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            vm.pickCountry(code)
                                            open = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("FOLLOWING", color = Mute, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                    CardBlock {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("New-release alerts", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("When a followed Audius artist drops a track.", color = Mute, fontSize = 13.sp)
                            }
                            Switch(
                                checked = vm.followAlerts,
                                onCheckedChange = { vm.setAlerts(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Lime, checkedTrackColor = Lime.copy(alpha = 0.4f))
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Muchi 1.3.0 · Android 16", color = Mute, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SubPage(body: String) {
    Text(body, color = Mute, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(8.dp))
}

@Composable
private fun CardBlock(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GreenSurface), content = { content() })
}

@Composable
private fun GoRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Mute, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Mute)
    }
}

@Composable
private fun AccountCard(vm: MuchiViewModel, onGoogle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GreenSurface)
            .padding(16.dp)
    ) {
        val g = vm.google
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(GreenCard), contentAlignment = Alignment.Center) {
                if (g != null && g.photo.isNotBlank()) {
                    AsyncImage(g.photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text((g?.name ?: "G").take(1), color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g?.name ?: "Not signed in", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(g?.email ?: "Google account for a real signed-in app", color = Mute, fontSize = 13.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (g == null) {
            Button(
                onClick = onGoogle,
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = GreenCard),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) { Text("Continue with Google", fontWeight = FontWeight.SemiBold) }
            vm.authMsg?.let { Text(it, color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        } else {
            TextButton(onClick = { vm.signOut() }) { Text("Sign out", color = Lime) }
        }
    }
}
