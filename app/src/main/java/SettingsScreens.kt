package com.fireflicker.fireplex2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fireflicker.fireplex2.data.ExoPlayerSettings
import com.fireflicker.fireplex2.data.PlexLibrary
import com.fireflicker.fireplex2.data.PlexMediaItem
import com.fireflicker.fireplex2.data.PlexSubtitleTrack
import kotlinx.coroutines.delay

enum class SettingsPage {
    Main,
    App,
    PlayerSettings,
    PlayerName,
    SpeedTest
}

@Composable
fun SettingsScreen(
    libraries: List<PlexLibrary>,
    hiddenKeys: Set<String>,
    friendlyName: String,
    playerChoice: PlayerChoice,
    streamMode: String,
    deviceProfile: DeviceProfile,
    exoSettings: ExoPlayerSettings,
    speedResult: String,
    internetSpeedResult: String,
    status: String,
    cachedAt: Long,
    accountExpiryDate: String?,
    accountDaysRemaining: Int?,
    appDisplayMode: AppDisplayMode,
    onSaveAppDisplayMode: (AppDisplayMode) -> Unit,
    onSaveFriendlyName: (String) -> Unit,
    onSetLibraryEnabled: (PlexLibrary, Boolean) -> Unit,
    onSavePlayerChoice: (PlayerChoice) -> Unit,
    onSaveStreamMode: (String) -> Unit,
    onSaveDeviceProfile: (DeviceProfile) -> Unit,
    onSaveExoSettings: (ExoPlayerSettings) -> Unit,
    onRunSpeedTest: () -> Unit,
    onRunInternetSpeedTest: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenAdminDownloads: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshAccount: () -> Unit,
    onSignOut: () -> Unit,
    onCloseSettings: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.Main) }
    var nameDraft by remember(friendlyName) { mutableStateOf(friendlyName) }
    val accountSummary = when {
        !accountExpiryDate.isNullOrBlank() && accountDaysRemaining != null ->
            "Expires $accountExpiryDate - $accountDaysRemaining days remaining"
        !accountExpiryDate.isNullOrBlank() -> "Expires $accountExpiryDate"
        else -> "Recheck the current subscription"
    }

    BackHandler(enabled = page != SettingsPage.Main) {
        page = SettingsPage.Main
    }

    Row(modifier = Modifier.fillMaxSize().padding(FirePlexDimens.ScreenPadding)) {
        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .background(FirePlexColors.Panel, RoundedCornerShape(FirePlexDimens.CardRadius))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsRailButton("HOME", page == SettingsPage.Main) { onCloseSettings() }
            SettingsRailButton("PIN", page == SettingsPage.App) { page = SettingsPage.App }
            SettingsRailButton("PLAY", page == SettingsPage.PlayerSettings) { page = SettingsPage.PlayerSettings }
            SettingsRailButton("NAME", page == SettingsPage.PlayerName) { page = SettingsPage.PlayerName }
            SettingsRailButton("TEST", page == SettingsPage.SpeedTest) { page = SettingsPage.SpeedTest; onRunSpeedTest() }
            Spacer(Modifier.weight(1f))
            SettingsRailButton("BACK", false) { if (page == SettingsPage.Main) onCloseSettings() else page = SettingsPage.Main }
        }

        Spacer(Modifier.width(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xC9050B11), RoundedCornerShape(FirePlexDimens.CardRadius))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            text = when (page) {
                                SettingsPage.Main -> "SETTINGS"
                                SettingsPage.App -> "PIN CATEGORIES"
                                SettingsPage.PlayerSettings -> "PLAYER SETTINGS"
                                SettingsPage.PlayerName -> "PLAYER NAME"
                                SettingsPage.SpeedTest -> "PLEX TEST PING"
                            },
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 5.sp
                        )
                        Text(status, color = Color(0xFF00E676), fontSize = 14.sp)
                        Text(cacheLabel(cachedAt), color = Color(0xFFB7C7D8), fontSize = 12.sp)
                    }

                    if (page != SettingsPage.Main) {
                        FocusActionButton("BACK", Modifier.width(130.dp), Color(0xFF203040)) {
                            page = SettingsPage.Main
                        }
                    }
                }
            }
        }

        when (page) {
            SettingsPage.Main -> {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsMenuRow("STREAM SETTINGS", "Quality, subtitles, audio and playback") { page = SettingsPage.PlayerSettings }
                            SettingsMenuRow("PLAYER SETTINGS", "EXO, buffering, speed and zoom") { page = SettingsPage.PlayerSettings }
                            SettingsMenuRow("PIN CATEGORIES", "Choose which libraries show in Movies and Series") { page = SettingsPage.App }
                            SettingsMenuRow("PLAYER NAME", friendlyName) { page = SettingsPage.PlayerName }
                            SettingsMenuRow("UPDATE MEDIA", "Refresh movies and TV shows") { onOpenUpdate() }
                            SettingsMenuRow("PLEX TEST PING", speedResult) { page = SettingsPage.SpeedTest; onRunSpeedTest() }
                            SettingsMenuRow("SPEED TEST", internetSpeedResult) { page = SettingsPage.SpeedTest; onRunInternetSpeedTest() }
                            SettingsMenuRow("REFRESH ACCOUNT", accountSummary) { onRefreshAccount() }
                            SettingsMenuRow("CLEAR CACHE", "Remove saved media indexes and artwork") { onClearCache() }
                            SettingsMenuRow("ADMIN", "PIN protected offline downloads") { onOpenAdminDownloads() }
                        }
                        SettingsAboutPanel(
                            playerName = friendlyName,
                            player = playerLabel(playerChoice),
                            streamMode = streamModeLabel(streamMode),
                            categoryCount = libraries.count { !hiddenKeys.contains(it.key) },
                            accountExpiry = accountSummary,
                            onSignOut = onSignOut
                        )
                    }
                }
            }

            SettingsPage.App -> {
                item {
                    SettingsCard(title = "Device Layout") {
                        Text("Choose the layout for this device. TV mode is best for Google TV, Android TV boxes, and Fire Sticks.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            FocusActionButton("TV / REMOTE", Modifier.weight(1f), if (appDisplayMode == AppDisplayMode.Tv) Color(0xFF00E676) else Color(0xFF203040)) { onSaveAppDisplayMode(AppDisplayMode.Tv) }
                        }
                    }
                }

                item {
                    SettingsCard(title = "Pinned Categories") {
                        Text("Turn categories on or off. These apply to the Movies and Series pages.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        libraries.forEach { library ->
                            val enabled = !hiddenKeys.contains(library.key)
                            SettingsToggleRow(
                                title = library.title.ifBlank { "Library" },
                                subtitle = library.type.ifBlank { "category" },
                                checked = enabled,
                                onClick = { onSetLibraryEnabled(library, !enabled) }
                            )
                        }
                    }
                }
            }

            SettingsPage.PlayerName -> {
                item {
                    SettingsCard(title = "Player Name") {
                        Text("This name is used as the FirePlex player name and the Plex friendly device name.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        TextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Player name") }
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            FocusActionButton("SAVE NAME", Modifier.weight(1f), Color(0xFF4C8B1F)) {
                                onSaveFriendlyName(nameDraft)
                                page = SettingsPage.Main
                            }
                            FocusActionButton("RESET", Modifier.weight(1f), Color(0xFF8E0031)) {
                                nameDraft = "FirePlex3.0"
                                onSaveFriendlyName("FirePlex3.0")
                            }
                        }
                    }
                }
            }

            SettingsPage.SpeedTest -> {
                item {
                    SettingsCard(title = "Plex Test Ping") {
                        Text("Tests the connection to your selected Plex server.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF031C33), RoundedCornerShape(14.dp)).padding(22.dp), contentAlignment = Alignment.Center) {
                            Text(speedResult, color = Color(0xFF00E676), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(16.dp))
                        FocusActionButton("BEGIN PLEX TEST PING", Modifier.fillMaxWidth(), Color(0xFF007C86)) { onRunSpeedTest() }
                    }
                }

                item {
                    SettingsCard(title = "Speed Test") {
                        Text("Tests this device's internet download and upload speed. Useful for checking whether the TV box/network is the bottleneck.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF031C33), RoundedCornerShape(14.dp)).padding(22.dp), contentAlignment = Alignment.Center) {
                            Text(internetSpeedResult, color = Color(0xFF00E676), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Shows download and upload. Location depends on the public speed test endpoint route from this device.", color = Color(0xFFB7C7D8), fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        FocusActionButton("BEGIN SPEED TEST", Modifier.fillMaxWidth(), Color(0xFF00A843)) { onRunInternetSpeedTest() }
                    }
                }
            }

            SettingsPage.PlayerSettings -> {
                item {
                    SettingsCard(title = "Device Profile") {
                        Text("Device profile is locked to Auto Detect. FirePlex will tune the player for Google TV, Fire Stick, Android TV, or mobile automatically.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        FocusActionButton("AUTO DETECT", Modifier.fillMaxWidth(), Color(0xFF00E676)) { onSaveDeviceProfile(DeviceProfile.Auto) }
                    }
                }

                item {
                    SettingsCard(title = "Player") {
                        Text("Selected: ${playerLabel(playerChoice)}", color = Color(0xFF00E676), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            FocusActionButton("EXO BUILT IN", Modifier.fillMaxWidth(), Color(0xFF00E676)) { onSavePlayerChoice(PlayerChoice.Exo) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("FirePlex now uses one embedded player on every supported device.", color = Color(0xFFB7C7D8), fontSize = 12.sp)
                    }
                }

                item {
                    SettingsCard(title = "Stream Type") {
                        Text("Auto uses Direct Play first, Direct Stream when needed, and only falls back to Transcode if playback fails.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        FocusActionButton("AUTO: DIRECT PLAY â†’ DIRECT STREAM â†’ TRANSCODE", Modifier.fillMaxWidth(), Color(0xFF00E676)) { onSaveStreamMode("auto") }
                        Spacer(Modifier.height(8.dp))
                        Text("Current: ${streamModeLabel(streamMode)}", color = Color(0xFF00E676), fontSize = 12.sp)
                    }
                }

                item {
                    SettingsCard(title = "EXO Settings") {
                        SettingsOptionRow(
                            "Pre Buffer",
                            listOf(5 to "5 Sec", 10 to "10 Sec", 20 to "20 Sec", 30 to "30 Sec", 40 to "40 Sec", 60 to "60 Sec"),
                            exoSettings.preBufferSeconds
                        ) { onSaveExoSettings(exoSettings.copy(preBufferSeconds = it)) }
                        Spacer(Modifier.height(12.dp))
                        SettingsOptionRow(
                            "Zoom",
                            listOf("best_fit" to "Best Fit", "fill" to "Fill", "zoom" to "Zoom"),
                            exoSettings.zoomMode
                        ) { onSaveExoSettings(exoSettings.copy(zoomMode = it)) }
                        Spacer(Modifier.height(12.dp))
                        Text("Subtitles start OFF. Turn them on from inside the video player using SUBS.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        SettingsOptionRow(
                            "Volume",
                            listOf(60 to "60%", 70 to "70%", 80 to "80%", 90 to "90%", 100 to "100%"),
                            exoSettings.volumePercent
                        ) { onSaveExoSettings(exoSettings.copy(volumePercent = it)) }
                    }
                }

            }
        }
    }
    }
}

@Composable
fun SettingsRailButton(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .size(50.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = if (focused || selected) FirePlexColors.Accent else Color.Transparent,
        shape = RoundedCornerShape(FirePlexDimens.CardRadius),
        border = if (focused) BorderStroke(2.dp, FirePlexColors.AccentSoft) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (focused || selected) Color.Black else FirePlexColors.Text, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SettingsMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = if (focused) FirePlexColors.Accent else Color(0xFF111A24),
        shape = RoundedCornerShape(FirePlexDimens.ButtonRadius),
        border = BorderStroke(FirePlexDimens.ThinBorder, if (focused) FirePlexColors.AccentSoft else Color(0xFF273542))
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(if (focused) Color(0x22000000) else Color(0xFF182632), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                Text("+", color = if (focused) Color.Black else FirePlexColors.AccentBright, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (focused) Color.Black else FirePlexColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = if (focused) Color(0xFF073A18) else Color(0xFF94A2B1), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(">", color = if (focused) Color.Black else Color(0xFF8EA0B1), fontSize = 18.sp)
        }
    }
}

@Composable
fun SettingsAboutPanel(
    playerName: String,
    player: String,
    streamMode: String,
    categoryCount: Int,
    accountExpiry: String,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .heightIn(min = 390.dp)
            .background(Color(0xFF0C151E), RoundedCornerShape(FirePlexDimens.CardRadius))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painter = painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(88.dp))
        Text("FIRE", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("PLEX", color = Color(0xFF00FF66), fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(18.dp))
        Text(playerName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Player: $player", color = Color(0xFF9AA8B6), fontSize = 10.sp)
        Text("Stream: $streamMode", color = Color(0xFF9AA8B6), fontSize = 10.sp)
        Text("Visible categories: $categoryCount", color = Color(0xFF9AA8B6), fontSize = 10.sp)
        Text(accountExpiry, color = Color(0xFF00E676), fontSize = 10.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        FocusActionButton("SIGN OUT", Modifier.fillMaxWidth(), Color(0xFF7D1731), onSignOut)
    }
}

@Composable
fun <T> SettingsOptionRow(label: String, options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color(0xFFBFEFF2), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.second }) { option ->
                FocusOptionChip(
                    text = option.second,
                    selected = option.first == selected,
                    onClick = { onSelected(option.first) }
                )
            }
        }
    }
}

@Composable
fun YesNoRow(label: String, selected: Boolean, onSelected: (Boolean) -> Unit) {
    SettingsOptionRow(label, listOf(true to "Yes", false to "No"), selected, onSelected)
}

@Composable
fun SettingsTile(title: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .height(178.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF203040) else Color(0xE90B0D25)),
        border = BorderStroke(if (focused) 5.dp else 1.dp, if (focused) Color(0xFF00FF66) else Color(0xFF273553)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (focused) Color(0x2229D3FF) else Color.Transparent)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, color = if (focused) Color(0xFF00FF66) else Color(0xFFD9DEE8), fontSize = if (focused) 54.sp else 48.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(title, color = if (focused) Color(0xFFE8FFF0) else Color.White, fontSize = if (focused) 16.sp else 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (focused) {
                Spacer(Modifier.height(8.dp))
                Text("OK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.background(Color(0xFF00FF66), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FirePlexColors.Panel), shape = RoundedCornerShape(FirePlexDimens.CardRadius)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = FirePlexColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun DropDownSettingsCard(title: String, open: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SettingsCard(title = title) {
        SettingsExpandRow(open = open, onToggle = onToggle)

        if (open) {
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun SettingsExpandRow(open: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onToggle),
        color = if (focused) FirePlexColors.ButtonAlt else Color(0x99111820),
        shape = RoundedCornerShape(FirePlexDimens.ButtonRadius),
        border = BorderStroke(if (focused) FirePlexDimens.FocusBorder else FirePlexDimens.ThinBorder, if (focused) FirePlexColors.AccentBright else Color(0x334B5C70))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (open) "Close section" else "Open section", color = if (focused) Color.White else Color(0xFFB7C7D8), fontSize = 15.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
            Text(if (open) "Hide" else "Show", color = if (focused) Color.Black else FirePlexColors.AccentBright, fontWeight = FontWeight.Black, modifier = if (focused) Modifier.background(FirePlexColors.AccentBright, RoundedCornerShape(FirePlexDimens.ButtonRadius)).padding(horizontal = 12.dp, vertical = 5.dp) else Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
        }
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = if (focused) Color(0xFF203040) else Color(0x66111820),
        shape = RoundedCornerShape(FirePlexDimens.ButtonRadius),
        border = BorderStroke(if (focused) FirePlexDimens.FocusBorder else FirePlexDimens.ThinBorder, if (focused) FirePlexColors.AccentBright else Color(0x22384758))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(if (checked) FirePlexColors.AccentBright else Color.Transparent, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (checked) "ON" else "", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = if (focused) FontWeight.Black else FontWeight.Bold)
                Text(subtitle, color = if (focused) FirePlexColors.AccentBright else FirePlexColors.Muted, fontSize = 12.sp)
            }
            if (focused) {
                Text("OK", color = Color.Black, fontWeight = FontWeight.Black, modifier = Modifier.background(FirePlexColors.AccentBright, RoundedCornerShape(FirePlexDimens.ButtonRadius)).padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
fun FocusOptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || selected
    Surface(
        modifier = Modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = when {
            focused -> Color(0xFF00FF66)
            selected -> Color(0xFF2A3442)
            else -> Color(0xFF151C27)
        },
        shape = RoundedCornerShape(FirePlexDimens.ButtonRadius),
        border = BorderStroke(if (focused) FirePlexDimens.FocusBorder else FirePlexDimens.ThinBorder, if (focused) FirePlexColors.AccentBright else if (selected) Color(0xFF5D7188) else Color(0x44566678))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}



