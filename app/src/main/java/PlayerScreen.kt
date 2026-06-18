package com.fireflicker.fireplex2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fireflicker.fireplex2.data.ExoPlayerSettings
import com.fireflicker.fireplex2.data.OpenSubtitleResult
import com.fireflicker.fireplex2.data.PlexMediaItem
import com.fireflicker.fireplex2.data.PlexSubtitleTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
@Composable
fun SelectedVideoScreen(
    item: PlexMediaItem,
    playUrl: String?,
    subtitleTracks: List<PlexSubtitleTrack>,
    selectedSubtitle: PlexSubtitleTrack?,
    subtitleUrl: String?,
    externalSubtitleName: String?,
    playerChoice: PlayerChoice,
    exoSettings: ExoPlayerSettings,
    startPositionMs: Long,
    loading: Boolean,
    status: String,
    errorMessage: String?,
    onSubtitleSelected: (PlexSubtitleTrack?) -> Unit,
    onSearchOpenSubtitles: suspend (String, String) -> List<OpenSubtitleResult>,
    onOpenSubtitleSelected: suspend (OpenSubtitleResult) -> Unit,
    onPlayerError: (String) -> Unit,
    onRetryExoTranscode: () -> Unit,
    onPlayback: (String, Long, Long) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (playUrl.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (loading) "Opening player..." else status, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        } else if (!errorMessage.isNullOrBlank()) {
            PlayerErrorScreen(errorMessage, onRetryExoTranscode)
        } else if (playerChoice == PlayerChoice.Exo) {
            ExoVideoPlayer(
                playUrl = playUrl,
                itemTitle = item.title,
                subtitleUrl = if (exoSettings.subtitlesEnabled) subtitleUrl else null,
                subtitleTracks = subtitleTracks,
                selectedSubtitle = selectedSubtitle,
                externalSubtitleName = externalSubtitleName,
                settings = exoSettings,
                startPositionMs = startPositionMs,
                onSubtitleSelected = onSubtitleSelected,
                onSearchOpenSubtitles = onSearchOpenSubtitles,
                onOpenSubtitleSelected = onOpenSubtitleSelected,
                onPlayerError = onPlayerError,
                onPlayback = onPlayback
            )
        } else {
            ExternalPlayerScreen(
                playUrl = playUrl,
                playerChoice = playerChoice,
                onFallback = onRetryExoTranscode
            )
        }
    }
}

@Composable
fun ExoVideoPlayer(
    playUrl: String,
    itemTitle: String,
    subtitleUrl: String?,
    subtitleTracks: List<PlexSubtitleTrack>,
    selectedSubtitle: PlexSubtitleTrack?,
    externalSubtitleName: String?,
    settings: ExoPlayerSettings,
    startPositionMs: Long,
    onSubtitleSelected: (PlexSubtitleTrack?) -> Unit,
    onSearchOpenSubtitles: suspend (String, String) -> List<OpenSubtitleResult>,
    onOpenSubtitleSelected: suspend (OpenSubtitleResult) -> Unit,
    onPlayerError: (String) -> Unit,
    onPlayback: (String, Long, Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var subtitlesOpen by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableStateOf(1) }
    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val player = remember(playUrl, subtitleUrl, settings.preBufferSeconds) {
        val targetBufferMs = settings.preBufferSeconds.coerceIn(5, 60) * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(targetBufferMs, targetBufferMs * 2, 1500, 3000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            playWhenReady = true
            volume = settings.volumePercent.coerceIn(0, 100) / 100f
        }
    }

    fun resizeModeFor(zoom: String): Int {
        return when (zoom) {
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun subtitleMime(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".vtt") || lower.contains("format=vtt") -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    fun buildMediaItem(): MediaItem {
        val subtitles = if (subtitleUrl.isNullOrBlank()) {
            emptyList()
        } else {
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                    .setMimeType(subtitleMime(subtitleUrl))
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        }
        return MediaItem.Builder()
            .setUri(Uri.parse(playUrl))
            .setMediaId(itemTitle)
            .setSubtitleConfigurations(subtitles)
            .build()
    }

    fun updatePlayerState() {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.takeIf { it > 0L } ?: 0L
    }

    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            updatePlayerState()
            onPlayback(if (player.isPlaying) "playing" else "paused", positionMs, durationMs)
        }
    }

    LaunchedEffect(playUrl, subtitleUrl, startPositionMs) {
        player.setMediaItem(buildMediaItem())
        player.prepare()
        if (startPositionMs > 0L) player.seekTo(startPositionMs)
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlayerError(error.message ?: "EXO could not play this video.")
            }
        }
        player.addListener(listener)
        onDispose {
            val position = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            scope.launch { onPlayback("stopped", position, duration) }
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { controlsVisible = !controlsVisible }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    setUseController(true)
                    resizeMode = resizeModeFor(settings.zoomMode)
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeModeFor(settings.zoomMode)
                player.volume = settings.volumePercent.coerceIn(0, 100) / 100f
            }
        )

        if (controlsVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color(0xCC000000)))))
            Text(
                "EXO",
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (durationMs > 0L) {
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = { player.seekTo(it.toLong()); updatePlayerState() },
                        valueRange = 0f..durationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(positionMs), color = Color.White, fontSize = 13.sp)
                    Text(formatTime(durationMs), color = Color.White, fontSize = 13.sp)
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FocusActionButton("-10", Modifier.width(78.dp), Color(0xAA203040)) { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); updatePlayerState() }
                    FocusActionButton(if (player.isPlaying) "PAUSE" else "PLAY", Modifier.width(110.dp), Color(0xFFE5A00D)) {
                        if (player.isPlaying) player.pause() else player.play()
                        updatePlayerState()
                    }
                    FocusActionButton("+30", Modifier.width(78.dp), Color(0xAA203040)) { player.seekTo(player.currentPosition + 30_000L); updatePlayerState() }
                    FocusActionButton("SPEED ${speeds[speedIndex]}x", Modifier.width(140.dp), Color(0xAA203040)) {
                        speedIndex = (speedIndex + 1) % speeds.size
                        player.setPlaybackSpeed(speeds[speedIndex])
                    }
                    FocusActionButton("SUBS", Modifier.width(100.dp), Color(0xAA203040)) { subtitlesOpen = !subtitlesOpen }
                    FocusActionButton("START", Modifier.width(100.dp), Color(0xAA203040)) { player.seekTo(0L); updatePlayerState() }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !externalSubtitleName.isNullOrBlank() -> "Subtitles: $externalSubtitleName"
                        selectedSubtitle != null -> "Subtitles: ${selectedSubtitle.title.ifBlank { selectedSubtitle.language.ifBlank { "ON" } }}"
                        else -> "Subtitles: OFF"
                    },
                    color = Color(0xFFB7C7D8),
                    fontSize = 12.sp
                )

                if (subtitlesOpen) {
                    SubtitlePickerPanel(
                        subtitleTracks = subtitleTracks,
                        selectedSubtitle = if (externalSubtitleName != null) null else selectedSubtitle,
                        externalSubtitleName = externalSubtitleName,
                        itemTitle = itemTitle,
                        onSubtitleSelected = {
                            onSubtitleSelected(it)
                            subtitlesOpen = false
                        },
                        onSearchOpenSubtitles = onSearchOpenSubtitles,
                        onOpenSubtitleSelected = onOpenSubtitleSelected
                    )
                }
            }
        }
    }
}

@Composable
fun ExternalPlayerScreen(
    playUrl: String,
    playerChoice: PlayerChoice,
    onFallback: () -> Unit
) {
    val context = LocalContext.current
    val packageNames = when (playerChoice) {
        PlayerChoice.ExternalMpv -> listOf("is.xyz.mpv", "is.xyz.mpv.debug")
        PlayerChoice.ExternalVlc -> listOf("org.videolan.vlc", "org.videolan.vlc.debug", "org.videolan.vlc.betav7neon")
        PlayerChoice.Exo -> emptyList()
    }
    var launchMessage by remember(playUrl, playerChoice) { mutableStateOf("Opening ${playerLabel(playerChoice)}...") }

    LaunchedEffect(playUrl, playerChoice) {
        val packageManager = context.packageManager
        val installedPackage = packageNames.firstOrNull { packageName ->
            runCatching { packageManager.getPackageInfo(packageName, 0) }.isSuccess
        }

        if (installedPackage == null) {
            launchMessage = "${playerLabel(playerChoice)} is not installed."
            return@LaunchedEffect
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(playUrl), "video/*")
            setPackage(installedPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
            launchMessage = "Playback opened in ${playerLabel(playerChoice)}."
        } catch (_: ActivityNotFoundException) {
            launchMessage = "Could not open ${playerLabel(playerChoice)}."
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xF0111820)),
            border = BorderStroke(1.dp, Color(0x554B5C70))
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(launchMessage, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text("External players are optional in the Lite build.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                FocusActionButton("USE EXO + TRANSCODE", Modifier.fillMaxWidth(), Color(0xFFE5A00D), onFallback)
            }
        }
    }
}

@Composable
fun PlayerErrorScreen(message: String, onRetryExoTranscode: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xF0111820)),
            border = BorderStroke(1.dp, Color(0xFF8E0031))
        ) {
            Column(
                modifier = Modifier.widthIn(max = 620.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Player failed", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(message, color = Color(0xFFB7C7D8), fontSize = 14.sp, textAlign = TextAlign.Center)
                Text("Try EXO with Plex Transcode.", color = Color(0xFFE5A00D), fontSize = 14.sp)
                FocusActionButton("RETRY EXO TRANSCODE", Modifier.fillMaxWidth(), Color(0xFFE5A00D), onRetryExoTranscode)
            }
        }
    }
}

@Composable
fun SubtitlePickerPanel(
    subtitleTracks: List<PlexSubtitleTrack>,
    selectedSubtitle: PlexSubtitleTrack?,
    externalSubtitleName: String?,
    itemTitle: String,
    onSubtitleSelected: (PlexSubtitleTrack?) -> Unit,
    onSearchOpenSubtitles: suspend (String, String) -> List<OpenSubtitleResult>,
    onOpenSubtitleSelected: suspend (OpenSubtitleResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    var finderOpen by remember { mutableStateOf(false) }
    var subtitleQuery by remember(itemTitle) { mutableStateOf(itemTitle) }
    var subtitleLanguage by remember { mutableStateOf("en") }
    var subtitleSearchStatus by remember { mutableStateOf("Search OpenSubtitles while the video keeps playing.") }
    var openSubtitleResults by remember { mutableStateOf<List<OpenSubtitleResult>>(emptyList()) }

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(max = if (finderOpen) 430.dp else 180.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE8111820)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x554B5C70))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Subtitles", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FocusActionButton(
                        text = "OFF",
                        modifier = Modifier.width(82.dp),
                        color = if (selectedSubtitle == null && externalSubtitleName == null) Color(0xFFE5A00D) else Color(0xAA203040)
                    ) {
                        onSubtitleSelected(null)
                    }
                }

                item {
                    FocusActionButton(
                        text = "FIND",
                        modifier = Modifier.width(92.dp),
                        color = if (finderOpen) Color(0xFF007C86) else Color(0xAA203040)
                    ) {
                        finderOpen = !finderOpen
                    }
                }

                if (!externalSubtitleName.isNullOrBlank()) {
                    item {
                        FocusActionButton(
                            text = externalSubtitleName.take(18),
                            modifier = Modifier.widthIn(min = 130.dp, max = 210.dp),
                            color = Color(0xFFE5A00D)
                        ) {
                            onSubtitleSelected(selectedSubtitle)
                        }
                    }
                }

                items(subtitleTracks) { track ->
                    val label = track.title
                        .ifBlank { track.language }
                        .ifBlank { "Subtitle" }
                        .take(22)
                    FocusActionButton(
                        text = label,
                        modifier = Modifier.widthIn(min = 118.dp, max = 220.dp),
                        color = if (track == selectedSubtitle && externalSubtitleName == null) Color(0xFFE5A00D) else Color(0xAA203040)
                    ) {
                        onSubtitleSelected(track)
                    }
                }
            }

            if (finderOpen) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = subtitleQuery,
                        onValueChange = { subtitleQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Search title") }
                    )
                    TextField(
                        value = subtitleLanguage,
                        onValueChange = { subtitleLanguage = it.take(3).lowercase() },
                        modifier = Modifier.width(92.dp),
                        singleLine = true,
                        label = { Text("Lang") }
                    )
                    FocusActionButton("SEARCH", Modifier.width(120.dp), Color(0xFF007C86)) {
                        scope.launch {
                            subtitleSearchStatus = "Searching OpenSubtitles..."
                            openSubtitleResults = emptyList()
                            try {
                                val results = onSearchOpenSubtitles(subtitleQuery, subtitleLanguage.ifBlank { "en" })
                                openSubtitleResults = results
                                subtitleSearchStatus = if (results.isEmpty()) "No subtitles found." else "Found ${results.size} subtitle results."
                            } catch (e: Throwable) {
                                subtitleSearchStatus = e.message ?: "Subtitle search failed."
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(subtitleSearchStatus, color = Color(0xFFE5A00D), fontSize = 12.sp)

                if (openSubtitleResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(openSubtitleResults, key = { it.fileId }) { result ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        subtitleSearchStatus = "Loading ${result.displayName}..."
                                        try {
                                            onOpenSubtitleSelected(result)
                                            subtitleSearchStatus = "Subtitle active: ${result.displayName}"
                                            finderOpen = false
                                        } catch (e: Throwable) {
                                            subtitleSearchStatus = e.message ?: "Could not load subtitle."
                                        }
                                    }
                                },
                                color = Color(0x66111820),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0x55FFFFFF))
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(result.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${result.language.uppercase()} - downloads ${result.downloads} - ${result.releaseName}", color = Color(0xFFB7C7D8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (subtitleTracks.isEmpty() && externalSubtitleName == null) {
                Spacer(Modifier.height(8.dp))
                Text("No subtitle tracks found for this video.", color = Color(0xFFB7C7D8), fontSize = 12.sp)
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
