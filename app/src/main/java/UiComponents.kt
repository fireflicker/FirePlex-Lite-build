package com.fireflicker.fireplex2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import com.fireflicker.fireplex2.data.PlexLibrary
import com.fireflicker.fireplex2.data.PlexMediaItem
import com.fireflicker.fireplex2.data.PlexSubtitleTrack
import kotlinx.coroutines.delay
@Composable
fun TvSeasonsScreen(
    show: PlexMediaItem,
    seasons: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    status: String,
    loading: Boolean,
    onSelectSeason: (PlexMediaItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text(show.title.ifBlank { "TV Series" }, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(if (loading) "Loading seasons..." else status, color = Color(0xFF00E676), fontSize = 14.sp)
            }
        }

        if (seasons.isEmpty()) {
            item { EmptyPanel(if (loading) "Loading seasons..." else "No seasons found for this TV series.") }
        } else {
            items(seasons, key = { it.ratingKey.ifBlank { it.key } }) { season ->
                MediaWideRow(
                    item = season,
                    artworkUrl = artworkUrls[season.ratingKey].orEmpty().ifBlank { artworkUrls[show.ratingKey].orEmpty() },
                    onClick = { onSelectSeason(season) }
                )
            }
        }
    }
}

@Composable
fun TvEpisodesScreen(
    show: PlexMediaItem?,
    season: PlexMediaItem,
    episodes: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    status: String,
    loading: Boolean,
    onSelectEpisode: (PlexMediaItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text(show?.title?.takeIf { it.isNotBlank() } ?: "TV Series", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(season.title.ifBlank { "Episodes" }, color = Color(0xFF00E676), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (loading) "Loading episodes..." else status, color = Color(0xFFB7C7D8), fontSize = 14.sp)
            }
        }

        if (episodes.isEmpty()) {
            item { EmptyPanel(if (loading) "Loading episodes..." else "No episodes found in this season.") }
        } else {
            items(episodes, key = { it.ratingKey.ifBlank { it.key } }) { episode ->
                MediaWideRow(
                    item = episode,
                    artworkUrl = artworkUrls[episode.ratingKey].orEmpty().ifBlank { artworkUrls[season.ratingKey].orEmpty() },
                    onClick = { onSelectEpisode(episode) }
                )
            }
        }
    }
}

@Composable
fun LibraryContentScreen(
    library: PlexLibrary,
    mediaItems: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    status: String,
    loading: Boolean,
    onSelectDetails: (PlexMediaItem) -> Unit
) {
    var selectedLetter by remember(library.key, mediaItems) { mutableStateOf("All") }
    val letters = remember(mediaItems) {
        val available = mediaItems
            .map { titleBucket(it.title) }
            .distinct()
            .sortedWith(compareBy<String> { if (it == "#") "0" else it })
        listOf("All") + available
    }
    val visibleItems = remember(mediaItems, selectedLetter) {
        if (selectedLetter == "All") mediaItems else mediaItems.filter { titleBucket(it.title) == selectedLetter }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (mediaItems.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.width(72.dp).fillMaxHeight().padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(letters, key = { it }) { letter ->
                    AlphabetButton(
                        text = letter,
                        selected = selectedLetter == letter,
                        onClick = { selectedLetter = letter }
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column {
                    Text(library.title.ifBlank { "Library" }, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (selectedLetter == "All") status else "Showing $selectedLetter",
                color = Color(0xFFFFD54F),
                        fontSize = 14.sp
                    )
                }
            }

            if (mediaItems.isEmpty()) {
                item { EmptyPanel(if (loading) "Loading videos..." else "Nothing found in this category.") }
            } else if (visibleItems.isEmpty()) {
                item { EmptyPanel("Nothing found under $selectedLetter.") }
            } else {
                items(visibleItems, key = { it.ratingKey.ifBlank { it.key } }) { item ->
                    MediaWideRow(item = item, artworkUrl = artworkUrls[item.ratingKey].orEmpty(), onClick = { onSelectDetails(item) })
                }
            }
        }
    }
}

@Composable
fun AlphabetButton(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color(0xFF00E676)
        selected -> Color(0xFF284152)
        else -> Color(0xF2111820)
    }
    val fg = if (focused) Color.Black else Color.White

    Surface(
        modifier = Modifier
            .width(58.dp)
            .height(34.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = if (focused || selected) BorderStroke(2.dp, Color(0xFF00E676)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

fun titleBucket(title: String): String {
    val first = title.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (first in 'A'..'Z') first.toString() else "#"
}
@Composable
fun MediaDetailsScreen(
    item: PlexMediaItem,
    artworkUrl: String,
    backdropUrl: String,
    playerChoice: PlayerChoice,
    onPlay: (PlexMediaItem, PlexSubtitleTrack?, Boolean) -> Unit,
    showDownloadButton: Boolean,
    onDownload: (PlexMediaItem) -> Unit
) {
    var subtitle by remember(item) { mutableStateOf(item.subtitles.firstOrNull { it.selected }) }
    var playClicked by remember(item.ratingKey, item.key) { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.ratingKey, item.key) {
        delay(150)
        runCatching { playFocus.requestFocus() }
    }
    fun startPlay(fromStart: Boolean) {
        playClicked = true
        onPlay(item, null, fromStart)
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111820))) {
                if (backdropUrl.isNotBlank()) {
                    AsyncImage(
                        model = cachedImageModel(backdropUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0xDD000000)))))
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                    Text(item.title.ifBlank { "Untitled" }, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    if (item.viewOffsetMs > 0L) {
                        Text("Continue watching available", color = Color(0xFF00E676), fontSize = 14.sp)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.rating.isNotBlank()) RatingPill("â˜… ${item.rating}")
                if (item.audienceRating.isNotBlank()) RatingPill("AUD ${item.audienceRating}")
                if (item.contentRating.isNotBlank()) RatingPill(item.contentRating)
                if (item.year.isNotBlank()) RatingPill(item.year)
                if (item.durationMs > 0L) RatingPill(formatDuration(item.durationMs))
            }
            Spacer(Modifier.height(12.dp))
            Text(item.summary.ifBlank { "No summary available." }, color = Color(0xFFD7DEE8), fontSize = 15.sp)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FocusActionButton(
                    if (playClicked) "LOADING..." else if (item.viewOffsetMs > 0L) "CONTINUE WATCHING" else "PLAY",
                    Modifier.weight(1f).focusRequester(playFocus),
                    Color(0xFF203040)
                ) { if (!playClicked) startPlay(false) }
                if (item.viewOffsetMs > 0L) {
                    FocusActionButton(
                        if (playClicked) "LOADING..." else "PLAY FROM START",
                        Modifier.weight(1f),
                        Color(0xFF203040)
                    ) { if (!playClicked) startPlay(true) }
                }
                if (showDownloadButton) {
                    FocusActionButton(
                        "DOWNLOAD",
                        Modifier.weight(1f),
                        Color(0xFF203040)
                    ) { if (!playClicked) onDownload(item) }
                }
            }
            if (playClicked) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Color(0xFF00FF66), modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                    Text("Opening stream from Plex...", color = Color(0xFF00FF66), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Subtitles and audio options are inside the video player.", color = Color(0xFFB7C7D8), fontSize = 13.sp)
        }
    }
}

@Composable
fun RatingPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xAA121A24), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = Color(0xFF00FF66), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun TopBar(
    title: String,
    subtitle: String,
    loading: Boolean,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column {
            Text(title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFB7C7D8), fontSize = 14.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onToggleMenu) { Text(if (menuOpen) "Hide Menu" else "Show Menu") }
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Loading..." else "Refresh") }
        }
    }
}

@Composable
fun HeroPanel(status: String) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF2111820)), shape = RoundedCornerShape(24.dp)) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF31220A), Color(0xFF111820), Color(0xFF050608)))).padding(24.dp)) {
            Column {
                Text("Home", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(status, color = Color(0xFFD7DEE8), fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun MediaRow(title: String, items: List<PlexMediaItem>, artworkUrls: Map<String, String>, onSelectDetails: (PlexMediaItem) -> Unit) {
    if (title.isNotBlank()) {
        SectionTitle(title)
    }
    if (items.isEmpty()) {
        EmptyPanel("Nothing found yet.")
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.ratingKey.ifBlank { it.key } }) { item ->
                MediaPosterCard(item = item, artworkUrl = artworkUrls[item.ratingKey].orEmpty(), onClick = { onSelectDetails(item) })
            }
        }
    }
}


fun Modifier.tvRemoteClick(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
): Modifier = composed {
    var keyDownAt by remember { mutableStateOf(0L) }
    this.onPreviewKeyEvent { event ->
        if (!enabled) return@onPreviewKeyEvent false
        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter
        val isMenu = event.key == Key.Menu
        when {
            isMenu && event.type == KeyEventType.KeyUp -> {
                onLongClick()
                true
            }
            isOk && event.type == KeyEventType.KeyDown -> {
                if (keyDownAt == 0L) keyDownAt = System.currentTimeMillis()
                true
            }
            isOk && event.type == KeyEventType.KeyUp -> {
                val heldMs = if (keyDownAt == 0L) 0L else System.currentTimeMillis() - keyDownAt
                keyDownAt = 0L
                if (heldMs >= 650L) onLongClick() else onClick()
                true
            }
            else -> false
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MediaPosterCard(
    item: PlexMediaItem,
    artworkUrl: String,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scaleWidth = if (focused) 124.dp else 116.dp
    val scaleHeight = if (focused) 186.dp else 176.dp

    Card(
        modifier = Modifier
            .width(scaleWidth)
            .height(scaleHeight)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick, onLongClick = onLongClick)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF17222D) else Color(0xF2111820)),
        border = if (focused) BorderStroke(3.dp, Color(0xFF00E676)) else null,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(116.dp).background(Color(0xFF1A2028)), contentAlignment = Alignment.Center) {
                if (artworkUrl.isNotBlank()) {
                    AsyncImage(model = cachedImageModel(artworkUrl), contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(item.type.take(1).uppercase().ifBlank { "V" }, color = Color(0xFF00E676), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                if (isFavorite) {
                    Text(
                        "â˜…",
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        color = Color(0xFF00E676),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.padding(7.dp)) {
                Text(item.title.ifBlank { "Untitled" }, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(mediaMetaLine(item), color = Color(0xFF00E676), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable
fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
}


