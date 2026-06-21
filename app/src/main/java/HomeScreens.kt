package com.fireflicker.fireplex2

import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fireflicker.fireplex2.data.PlexLibrary
import com.fireflicker.fireplex2.data.PlexMediaItem
import com.fireflicker.fireplex2.data.PlexPin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
@Composable
fun rememberInitialFocusRequester(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { requester.requestFocus() }
    }
    return requester
}

@Composable
fun AppLoginScreen(
    playerName: String,
    message: String,
    loading: Boolean,
    onPlayerNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onRelinkPlex: () -> Unit
) {
    val nameFocus = rememberInitialFocusRequester()
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "FirePlex logo",
            modifier = Modifier.size(70.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
        Text("FirePlex3.0", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Player account", color = Color(0xFF00E676), fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FirePlexColors.Panel),
            shape = RoundedCornerShape(FirePlexDimens.PanelRadius)
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Enter player name", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Use the username from your FirePlex web panel. This also becomes the friendly name shown to Plex.",
                    color = Color(0xFFBAC6D3),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))

                OutlinedTextField(
                    value = playerName,
                    onValueChange = onPlayerNameChange,
                    singleLine = true,
                    enabled = !loading,
                    label = { Text("Username / player name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF2D2D),
                        focusedLabelColor = Color(0xFFFF2D2D),
                        cursorColor = Color(0xFFFF2D2D)
                    )
                )

                Spacer(Modifier.height(16.dp))
                Text(message, color = Color(0xFF4DFF9B), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusActionButton(
                        if (loading) "CHECKING..." else "CONTINUE",
                        Modifier.weight(1f),
                        Color(0xFF16202A)
                    ) { if (!loading) onContinue() }
                    FocusActionButton(
                        "RELINK PLEX",
                        Modifier.weight(1f),
                        Color(0xFF16202A)
                    ) { if (!loading) onRelinkPlex() }
                }
            }
        }
    }
}

@Composable
fun LinkScreen(
    pin: PlexPin?,
    status: String,
    linked: Boolean,
    loading: Boolean,
    onGenerate: () -> Unit,
    onCheckLink: () -> Unit,
    onLoadHome: () -> Unit
) {
    val context = LocalContext.current
    val codeText = pin?.code?.uppercase().orEmpty()
    val generateFocus = rememberInitialFocusRequester()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "FirePlex logo",
            modifier = Modifier.size(70.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
        Text("FirePlex3.0", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Your Plex player", color = Color(0xFF00E676), fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FirePlexColors.Panel), shape = RoundedCornerShape(FirePlexDimens.PanelRadius)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Go to plex.tv/link", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                Box(modifier = Modifier.background(Color(0xFF1A2028), RoundedCornerShape(FirePlexDimens.PanelRadius)).padding(horizontal = 34.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(text = codeText.ifBlank { "----" }, color = Color(0xFF00E676), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                }

                Spacer(Modifier.height(12.dp))
                Text(status, color = if (linked) Color(0xFF4DFF9B) else Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusActionButton(
                        if (loading) "WORKING..." else "GENERATE CODE",
                        Modifier.width(180.dp).focusRequester(generateFocus),
                        Color(0xFF203040)
                    ) { if (!loading) onGenerate() }
                    if (codeText.isNotBlank() && !linked) {
                        FocusActionButton("OPEN LINK PAGE", Modifier.width(190.dp), Color(0xFF203040)) {
                            if (!loading) {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Plex link code", codeText))
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://plex.tv/link")))
                            }
                        }
                        FocusActionButton("CHECK LINK", Modifier.width(150.dp), Color(0xFF203040)) { if (!loading) onCheckLink() }
                    }
                    if (linked) {
                        FocusActionButton("OPEN HOME", Modifier.width(150.dp), Color(0xFF203040)) { if (!loading) onLoadHome() }
                    }
                }
            }
        }
    }
}


@Composable
fun DisplayModeChooserScreen(
    onTv: () -> Unit,
    onMobile: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050608), Color(0xFF111820), Color.Black)))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val landscape = maxWidth > maxHeight
        val logoSize = if (landscape) 64.dp else 96.dp
        val titleSize = if (landscape) 32.sp else 42.sp
        val tileHeight = if (landscape) 138.dp else 210.dp
        val tileWidth = if (landscape) 760.dp else 520.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "FirePlex logo",
                modifier = Modifier.size(logoSize),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(if (landscape) 8.dp else 14.dp))
            Text("FirePlex4.0", color = Color.White, fontSize = titleSize, fontWeight = FontWeight.Bold)
            Text("Choose how you are using the app", color = Color(0xFF00E676), fontSize = if (landscape) 14.sp else 17.sp)
            Spacer(Modifier.height(if (landscape) 18.dp else 34.dp))

            if (landscape) {
                Row(
                    modifier = Modifier.widthIn(max = tileWidth).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DisplayModeTile(
                        title = "TV",
                        subtitle = "Remote friendly layout for Android TV / Fire Stick",
                        icon = "â–£",
                        modifier = Modifier.weight(1f).height(tileHeight),
                        onClick = onTv
                    )
                    DisplayModeTile(
                        title = "MOBILE",
                        subtitle = "Touch friendly layout for phones and tablets",
                        icon = "â–¤",
                        modifier = Modifier.weight(1f).height(tileHeight),
                        onClick = onMobile
                    )
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = tileWidth).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DisplayModeTile(
                        title = "TV",
                        subtitle = "Remote friendly layout for Android TV / Fire Stick",
                        icon = "â–£",
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        onClick = onTv
                    )
                    DisplayModeTile(
                        title = "MOBILE",
                        subtitle = "Touch friendly layout for phones and tablets",
                        icon = "â–¤",
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        onClick = onMobile
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayModeTile(
    title: String,
    subtitle: String,
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF203040) else Color(0xE90B0D25)),
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF00E676) else Color(0x44FFFFFF)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, color = Color(0xFF00E676), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color(0xFFB7C7D8), textAlign = TextAlign.Center, fontSize = 14.sp)
        }
    }
}

@Composable
fun MobileLobbyScreen(
    serverName: String,
    friendlyName: String,
    libraries: List<PlexLibrary>,
    allLibrariesHidden: Boolean,
    status: String,
    loading: Boolean,
    cachedAt: Long,
    onOpenVod: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "FirePlex logo",
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("FirePlex4.0", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("$serverName - $friendlyName", color = Color(0xFFB7C7D8), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cacheLabel(cachedAt), color = Color(0xFF00E676), fontSize = 11.sp)
                }
            }
        }

        item {
            Text(if (loading) "Loading..." else status, color = Color(0xFFB7C7D8), fontSize = 14.sp, textAlign = TextAlign.Center)
        }

        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MobileBigButton("MOVIES", "Films and movies", !allLibrariesHidden && libraries.any { it.type.equals("movie", true) }, onOpenVod)
                MobileBigButton("SERIES", "TV shows and seasons", !allLibrariesHidden && libraries.any { it.type.equals("show", true) || it.type.equals("tv", true) }, onOpenSeries)
                MobileBigButton("FAVOURITES", "Saved movies and TV series", true, onOpenFavorites)
                MobileBigButton("GLOBAL SEARCH", "Search movies and TV series", true, onOpenSearch)
                MobileBigButton("UPDATE CONTENTS", "Refresh categories and artwork", true, onOpenUpdate)
                MobileBigButton("SETTINGS", "Player, speed test, player name", true, onOpenSettings)
            }
        }
    }
}

@Composable
fun MobileBigButton(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xD9111820) else Color(0x66111820)),
        border = BorderStroke(1.dp, if (enabled) Color(0x77FFFFFF) else Color(0x22FFFFFF)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = if (enabled) Color.White else Color(0xFF6D7784), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFB7C7D8), fontSize = 13.sp)
        }
    }
}

@Composable
fun MobileContentBrowseScreen(
    mode: ContentMode,
    libraries: List<PlexLibrary>,
    recentlyMovies: List<PlexMediaItem>,
    recentlyShows: List<PlexMediaItem>,
    continueWatching: List<PlexMediaItem>,
    favoriteItems: List<PlexMediaItem>,
    favoriteKeys: Set<String>,
    categoryItems: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    status: String,
    loading: Boolean,
    cachedAt: Long,
    loadingMore: Boolean,
    onOpenLibrary: (PlexLibrary) -> Unit,
    onLoadMore: () -> Unit,
    onPreloadLibrary: (PlexLibrary) -> Unit,
    onToggleFavorite: (PlexMediaItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
    onSelectDetails: (PlexMediaItem) -> Unit
) {
    val movieMode = mode == ContentMode.Vod
    val title = if (movieMode) "MOVIES" else "SERIES"
    val modeLibraries = libraries.filter {
        if (movieMode) it.type.equals("movie", true) else it.type.equals("show", true) || it.type.equals("tv", true)
    }
    val recent = (if (movieMode) recentlyMovies else recentlyShows).filter { itemMatchesMode(it, mode) }
    val recentLabel = if (movieMode) "RECENTLY ADDED MOVIES" else "RECENTLY ADDED SERIES"
    val libraryByKey = remember(modeLibraries) { modeLibraries.associateBy { it.key } }

    var selectedCategory by remember(mode, libraries) { mutableStateOf("RECENTLY ADDED") }
    var searchText by remember { mutableStateOf("") }
    var sortAz by remember(mode) { mutableStateOf(false) }
    var sortNewestFirst by remember(mode) { mutableStateOf(true) }

    val selectedCategoryTitle = when (selectedCategory) {
        "FAVORITES" -> "FAVORITES"
        "RECENTLY ADDED" -> recentLabel
        "CONTINUE WATCHING" -> "RECENTLY WATCHED"
        else -> libraryByKey[selectedCategory]?.title?.uppercase() ?: selectedCategory
    }

    LaunchedEffect(selectedCategory, modeLibraries) {
        delay(900)
        val selectedIndex = modeLibraries.indexOfFirst { it.key == selectedCategory }
        val nextLibrary = when {
            selectedIndex >= 0 -> modeLibraries.getOrNull(selectedIndex + 1)
            selectedCategory == "RECENTLY ADDED" || selectedCategory == "CONTINUE WATCHING" -> modeLibraries.firstOrNull()
            else -> null
        }
        if (nextLibrary != null) {
            onPreloadLibrary(nextLibrary)
        }
    }

    val baseGridItems = when (selectedCategory) {
        "CONTINUE WATCHING" -> continueWatching.filter { itemMatchesMode(it, mode) }
        "FAVORITES" -> favoriteItems.filter { itemMatchesMode(it, mode) }
        "RECENTLY ADDED" -> recent
        else -> if (libraryByKey.containsKey(selectedCategory)) categoryItems else recent
    }

    val visibleGridItems = remember(selectedCategory, mediaItemsFingerprint(baseGridItems), searchText, sortAz, sortNewestFirst) {
        val searched = if (searchText.isBlank()) baseGridItems else baseGridItems.filter { it.title.contains(searchText, ignoreCase = true) }
        if (sortAz) searched.sortedBy { it.title.lowercase() }
        else if (sortNewestFirst) searched.sortedByDescending { it.addedAt }
        else searched.sortedBy { it.addedAt }
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, visibleGridItems.size, selectedCategory, loadingMore) {
        if (!libraryByKey.containsKey(selectedCategory)) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!loadingMore && visibleGridItems.isNotEmpty() && lastVisible >= visibleGridItems.lastIndex - 12) {
                    onLoadMore()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(cacheLabel(cachedAt), color = Color(0xFF00E676), fontSize = 11.sp)
        }
        Text(selectedCategoryTitle, color = Color(0xFF00E676), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item { MobileCategoryChip("FAVORITES", selectedCategory == "FAVORITES") { selectedCategory = "FAVORITES" } }
            item { MobileCategoryChip(recentLabel, selectedCategory == "RECENTLY ADDED") { selectedCategory = "RECENTLY ADDED" } }
            item { MobileCategoryChip("RECENTLY WATCHED", selectedCategory == "CONTINUE WATCHING") { selectedCategory = "CONTINUE WATCHING" } }
            items(modeLibraries, key = { it.key }) { library ->
                MobileCategoryChip(library.title.uppercase(), selectedCategory == library.key) {
                    selectedCategory = library.key
                    onOpenLibrary(library)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.weight(1f).height(54.dp),
                singleLine = true,
                label = { Text("Search") }
            )
            FocusActionButton(if (sortAz) "A-Z ON" else "A-Z", Modifier.width(92.dp), Color(0x66111820)) { sortAz = !sortAz }
            FocusActionButton(if (sortNewestFirst) "NEW" else "OLD", Modifier.width(82.dp), Color(0x66111820)) { sortNewestFirst = !sortNewestFirst; sortAz = false }
        }

        Spacer(Modifier.height(8.dp))
        Text(if (loading) "Loading..." else if (loadingMore) "$status  Loading more..." else status, color = Color(0xFFB7C7D8), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))

        if (visibleGridItems.isEmpty()) {
            EmptyPanel(if (loading) "Loading..." else "Nothing found in this section.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 118.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(visibleGridItems, key = { it.ratingKey.ifBlank { it.key } }) { item ->
                    MobilePosterCard(
                        item = item,
                        artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                        isFavorite = favoriteKeys.contains(mediaItemStableId(item)),
                        onClick = { onSelectDetails(item) },
                        onLongClick = { onToggleFavorite(item) }
                    )
                }
                if (loadingMore) {
                    item {
                        LoadingMoreTile()
                    }
                }
            }
        }
    }
}


fun mediaItemStableId(item: PlexMediaItem): String {
    return item.ratingKey.ifBlank { item.key }
}

fun itemMatchesMode(item: PlexMediaItem, mode: ContentMode): Boolean {
    val type = item.type.lowercase()
    return if (mode == ContentMode.Vod) {
        (type == "movie" || type == "video" || type == "clip") &&
            item.grandparentRatingKey.isBlank() &&
            item.parentRatingKey.isBlank() &&
            item.seriesTitle.isBlank()
    } else {
        type == "show" ||
            type == "tv" ||
            type == "season" ||
            type == "episode" ||
            item.grandparentRatingKey.isNotBlank() ||
            item.seriesTitle.isNotBlank()
    }
}


@Composable
fun GlobalSearchScreen(
    items: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    favoriteKeys: Set<String>,
    loading: Boolean,
    status: String,
    onSearchRemote: suspend (String) -> List<PlexMediaItem>,
    onSelectDetails: (PlexMediaItem) -> Unit,
    onToggleFavorite: (PlexMediaItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var remoteResults by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteStatus by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        val clean = query.trim()
        remoteResults = emptyList()
        remoteStatus = ""
        if (clean.length < 2) return@LaunchedEffect

        delay(350)
        remoteLoading = true
        remoteStatus = "Searching Plex libraries..."
        try {
            remoteResults = onSearchRemote(clean)
            remoteStatus = if (remoteResults.isEmpty()) "No server results yet." else "Server search loaded."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            remoteStatus = e.message ?: "Server search failed."
        } finally {
            remoteLoading = false
        }
    }

    val results = remember(query, mediaItemsFingerprint(items), mediaItemsFingerprint(remoteResults)) {
        val clean = query.trim()
        if (clean.isBlank()) emptyList()
        else (remoteResults + items.filter {
            it.title.contains(clean, ignoreCase = true) ||
                it.seriesTitle.contains(clean, ignoreCase = true) ||
                it.summary.contains(clean, ignoreCase = true)
        })
            .distinctBy { mediaItemStableId(it) }
            .sortedBy { it.title.lowercase() }
            .take(250)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("GLOBAL SEARCH", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Search Movies and TV Series together", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().height(62.dp),
            singleLine = true,
            label = { Text("Type movie or TV show name") }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (loading) {
                "Loading search cache..."
            } else if (query.isBlank()) {
                status
            } else if (remoteLoading) {
                "Searching Plex... ${results.size} cached result(s)"
            } else {
                "${results.size} result(s)" + remoteStatus.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
            },
            color = Color(0xFFB7C7D8),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) {
            EmptyPanel("Start typing to search Movies and TV Series.")
        } else if (results.isEmpty()) {
            EmptyPanel("No results found.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(results, key = { mediaItemStableId(it) }) { item ->
                    MediaPosterCard(
                        item = item,
                        artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                        isFavorite = favoriteKeys.contains(mediaItemStableId(item)),
                        onClick = { onSelectDetails(item) },
                        onLongClick = { onToggleFavorite(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    items: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    favoriteKeys: Set<String>,
    loading: Boolean,
    status: String,
    mobileMode: Boolean,
    onSelectDetails: (PlexMediaItem) -> Unit,
    onToggleFavorite: (PlexMediaItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("FAVOURITES", color = Color.White, fontSize = if (mobileMode) 28.sp else 38.sp, fontWeight = FontWeight.Bold)
        Text("Hold a poster again to remove it from favourites.", color = Color(0xFF00E676), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(if (loading) "Loading..." else status, color = Color(0xFFB7C7D8), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        if (items.isEmpty()) {
            EmptyPanel("No favourites yet. Hold OK / long press a movie or TV series poster to add it here.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (mobileMode) 118.dp else 150.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(if (mobileMode) 10.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (mobileMode) 12.dp else 18.dp)
            ) {
                gridItems(items, key = { mediaItemStableId(it) }) { item ->
                    if (mobileMode) {
                        MobilePosterCard(
                            item = item,
                            artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                            isFavorite = favoriteKeys.contains(mediaItemStableId(item)),
                            onClick = { onSelectDetails(item) },
                            onLongClick = { onToggleFavorite(item) }
                        )
                    } else {
                        MediaPosterCard(
                            item = item,
                            artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                            isFavorite = favoriteKeys.contains(mediaItemStableId(item)),
                            onClick = { onSelectDetails(item) },
                            onLongClick = { onToggleFavorite(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MobileCategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF00E676) else Color(0xCC111820),
        border = BorderStroke(1.dp, if (selected) Color(0xFF00E676) else Color(0x44FFFFFF))
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MobilePosterCard(
    item: PlexMediaItem,
    artworkUrl: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(118.dp)
            .height(202.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD111820)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(165.dp).background(Color(0xFF1A2028)), contentAlignment = Alignment.Center) {
                if (artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = cachedImageModel(artworkUrl),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("No Art", color = Color(0xFFB7C7D8), fontSize = 12.sp)
                }
                if (isFavorite) {
                    Text(
                        "â˜…",
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                color = Color(0xFFFFD54F),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                item.title.ifBlank { "Untitled" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@Composable
fun LobbyScreen(
    serverName: String,
    friendlyName: String,
    libraries: List<PlexLibrary>,
    allLibrariesHidden: Boolean,
    status: String,
    loading: Boolean,
    cachedAt: Long,
    onOpenVod: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "FirePlex logo",
                        modifier = Modifier.size(58.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("FirePlex3.0", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("$serverName - $friendlyName", color = Color(0xFFB7C7D8), fontSize = 13.sp)
                        Text(cacheLabel(cachedAt), color = Color(0xFF00E676), fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LobbySmallButton("SEARCH") { }
                    LobbySmallButton("UPDATE") { onOpenUpdate() }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                LobbyCircleTile(label = "MOVIES", icon = "â–·", enabled = !allLibrariesHidden && libraries.any { it.type.equals("movie", true) }, onClick = onOpenVod)
                Spacer(Modifier.width(32.dp))
                LobbyCircleTile(label = "SERIES", icon = "â–¤", enabled = !allLibrariesHidden && libraries.any { it.type.equals("show", true) || it.type.equals("tv", true) }, onClick = onOpenSeries)
            }

            Spacer(Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(if (loading) "Loading..." else status, color = Color(0xFFB7C7D8), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LobbySmallButton("SETTINGS") { onOpenSettings() }
                    LobbySmallButton("FAVOURITES") { onOpenFavorites() }
                }
            }
        }
    }
}


@Composable
fun TvHomeScreen(
    serverName: String,
    friendlyName: String,
    libraries: List<PlexLibrary>,
    allLibrariesHidden: Boolean,
    recentlyMovies: List<PlexMediaItem>,
    recentlyShows: List<PlexMediaItem>,
    continueWatching: List<PlexMediaItem>,
    favoriteItems: List<PlexMediaItem>,
    favoriteKeys: Set<String>,
    artworkUrls: Map<String, String>,
    backdropUrls: Map<String, String>,
    status: String,
    loading: Boolean,
    cachedAt: Long,
    onOpenVod: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
    onSelectDetails: (PlexMediaItem) -> Unit,
    onToggleFavorite: (PlexMediaItem) -> Unit
) {
    val vodEnabled = !allLibrariesHidden && libraries.any { it.type.equals("movie", true) }
    val seriesEnabled = !allLibrariesHidden && libraries.any { it.type.equals("show", true) || it.type.equals("tv", true) }
    val cleanContinueWatching = continueWatching.filter {
        itemMatchesMode(it, ContentMode.Vod) || itemMatchesMode(it, ContentMode.Series)
    }
    val cleanRecentlyMovies = recentlyMovies.filter { itemMatchesMode(it, ContentMode.Vod) }
    val cleanRecentlyShows = recentlyShows.filter { itemMatchesMode(it, ContentMode.Series) }
    val hero = cleanContinueWatching.firstOrNull() ?: cleanRecentlyMovies.firstOrNull() ?: cleanRecentlyShows.firstOrNull()
    val heroBackdrop = hero?.ratingKey?.let { backdropUrls[it] }.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF03080D))
    ) {
        if (heroBackdrop.isNotBlank()) {
            AsyncImage(
                model = cachedImageModel(heroBackdrop),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFA03080D), Color(0xE803080D), Color(0x5603080D), Color(0xE803080D))
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xC903080D), Color.Transparent, Color(0xF203080D))))
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "FirePlex",
                    modifier = Modifier.size(46.dp),
                    contentScale = ContentScale.Fit
                )
                Text("FIRE", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("PLEX", color = Color(0xFF00E85B), fontSize = 23.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(28.dp))
                TvTopNavButton("HOME", true, true) { }
                TvTopNavButton("MOVIES", false, vodEnabled, onOpenVod)
                TvTopNavButton("TV SERIES", false, seriesEnabled, onOpenSeries)
                TvTopNavButton("FAVOURITES", false, true, onOpenFavorites)
                TvTopNavButton("GLOBAL SEARCH", false, vodEnabled || seriesEnabled, onOpenSearch)
                TvTopNavButton("SETTINGS", false, true, onOpenSettings)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(friendlyName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(serverName, color = Color(0xFF9BA9B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                TvTopNavButton("UPDATE", false, true, onOpenUpdate)
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (cleanContinueWatching.isNotEmpty()) {
                        HomeSectionTitle("RECENTLY WATCHED ON THIS DEVICE")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(106.dp)) {
                            items(cleanContinueWatching.take(6), key = { mediaItemStableId(it) }) { item ->
                                ContinueWatchingCard(item, artworkUrls[item.ratingKey].orEmpty()) { onSelectDetails(item) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    HomeSectionTitle("RECENTLY ADDED MOVIES")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(152.dp)) {
                        items(cleanRecentlyMovies.take(10), key = { mediaItemStableId(it) }) { item ->
                            HomePosterCard(
                                item = item,
                                artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                                favorite = favoriteKeys.contains(mediaItemStableId(item)),
                                onClick = { onSelectDetails(item) },
                                onLongClick = { onToggleFavorite(item) }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    HomeSectionTitle("RECENTLY ADDED SERIES")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(152.dp)) {
                        if (cleanRecentlyShows.isEmpty()) {
                            item { EmptyHomeSeriesCard() }
                        } else {
                            items(cleanRecentlyShows.take(10), key = { mediaItemStableId(it) }) { item ->
                                HomePosterCard(
                                    item = item,
                                    artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                                    favorite = favoriteKeys.contains(mediaItemStableId(item)),
                                    onClick = { onSelectDetails(item) },
                                    onLongClick = { onToggleFavorite(item) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(22.dp))

                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(Color(0xB8050B11), RoundedCornerShape(8.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(hero?.title?.uppercase() ?: "FIREPLEX", color = Color(0xFF00FF66), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(if (hero != null) mediaMetaLine(hero) else "Your Plex Media Server", color = Color(0xFFD5DBE2), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        hero?.summary?.ifBlank { "Choose a movie or TV series from your Plex Media Server." }
                            ?: "Choose a movie or TV series from your Plex Media Server.",
                        color = Color(0xFFC7D0DA),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    if (hero != null) {
                        FocusActionButton("OPEN DETAILS", Modifier.fillMaxWidth(), Color(0xFF00A843)) { onSelectDetails(hero) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(if (loading) "Refreshing..." else cacheLabel(cachedAt), color = Color(0xFF8EA0B1), fontSize = 10.sp)
                }
            }

            Text(if (loading) "Loading..." else status, color = Color(0xFF8EA0B1), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TvTopNavButton(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .height(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .tvRemoteClick(enabled = enabled, onClick = onClick),
        color = if (focused || selected) Color(0xFF12C743) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        border = if (focused) BorderStroke(2.dp, Color(0xFF8CFFAA)) else null
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(text, color = if (enabled) Color.White else Color(0xFF596574), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HomeSectionTitle(text: String) {
    Text(text, color = Color(0xFF00FF66), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
}

@Composable
fun EmptyHomeSeriesCard() {
    Card(
        modifier = Modifier.width(240.dp).height(132.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC111923)),
        border = BorderStroke(1.dp, Color(0x334D6072)),
        shape = RoundedCornerShape(7.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            Text(
                "No recent series cached yet. Run Update Contents.",
                color = Color(0xFFB7C7D8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ContinueWatchingCard(item: PlexMediaItem, artworkUrl: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .width(190.dp)
            .height(106.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF00FF66) else Color(0x334D6072))
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF111923))) {
            AsyncImage(model = cachedImageModel(artworkUrl), contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))
            Text(item.title, modifier = Modifier.align(Alignment.BottomStart).padding(9.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val progress = if (item.durationMs > 0L) (item.viewOffsetMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Color(0xFF00FF66), trackColor = Color(0x66404B56))
        }
    }
}

@Composable
fun HomePosterCard(item: PlexMediaItem, artworkUrl: String, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .width(106.dp)
            .height(160.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF00FF66) else Color(0x334D6072))
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF111923))) {
            AsyncImage(model = cachedImageModel(artworkUrl), contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (favorite) Text("FAV", modifier = Modifier.align(Alignment.TopEnd).background(Color(0xD9FFD54F)).padding(horizontal = 5.dp, vertical = 2.dp), color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TvQuickTile(
    title: String,
    subtitle: String,
    icon: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .height(if (focused) 138.dp else 128.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .tvRemoteClick(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF173B28) else Color(0xE9141720)),
        border = BorderStroke(if (focused) 4.dp else 1.dp, if (focused) Color(0xFF00FF66) else Color(0x44FFFFFF)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            if (focused) Color(0xFF00A843) else Color(0xFF182231),
                            if (focused) Color(0xFF0A2014) else Color(0xFF0B1018)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Text(icon, color = if (enabled) Color(0xFF00FF66) else Color(0xFF6D7785), fontSize = 44.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd))
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(title, color = if (enabled) Color.White else Color(0xFF6D7785), fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(subtitle.uppercase(), color = if (focused) Color(0xFFCCFFE0) else Color(0xFFB7C7D8), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable
fun TvPosterRow(
    title: String,
    items: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    favoriteKeys: Set<String>,
    onSelectDetails: (PlexMediaItem) -> Unit,
    onToggleFavorite: (PlexMediaItem) -> Unit
) {
    Column {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { mediaItemStableId(it) }) { item ->
                MediaPosterCard(
                    item = item,
                    artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                    isFavorite = favoriteKeys.contains(mediaItemStableId(item)),
                    onClick = { onSelectDetails(item) },
                    onLongClick = { onToggleFavorite(item) }
                )
            }
        }
    }
}

@Composable
fun ContentBrowseScreen(
    mode: ContentMode,
    libraries: List<PlexLibrary>,
    recentlyMovies: List<PlexMediaItem>,
    recentlyShows: List<PlexMediaItem>,
    continueWatching: List<PlexMediaItem>,
    favoriteItems: List<PlexMediaItem>,
    favoriteKeys: Set<String>,
    categoryItems: List<PlexMediaItem>,
    artworkUrls: Map<String, String>,
    status: String,
    loading: Boolean,
    cachedAt: Long,
    loadingMore: Boolean,
    onOpenLibrary: (PlexLibrary) -> Unit,
    onLoadMore: () -> Unit,
    onPreloadLibrary: (PlexLibrary) -> Unit,
    onToggleFavorite: (PlexMediaItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
    onSelectDetails: (PlexMediaItem) -> Unit
) {
    val movieMode = mode == ContentMode.Vod
    val title = if (movieMode) "MOVIES" else "SERIES"
    val modeLibraries = libraries.filter {
        if (movieMode) {
            it.type.equals("movie", true)
        } else {
            it.type.equals("show", true) || it.type.equals("tv", true)
        }
    }
    val recent = (if (movieMode) recentlyMovies else recentlyShows).filter { itemMatchesMode(it, mode) }
    val recentLabel = if (movieMode) "RECENTLY ADDED MOVIES" else "RECENTLY ADDED SERIES"
    val libraryByKey = remember(modeLibraries) { modeLibraries.associateBy { it.key } }

    var selectedCategory by remember(mode, libraries) { mutableStateOf("RECENTLY ADDED") }
    var sortNewestFirst by remember(mode) { mutableStateOf(true) }
    var sortAz by remember(mode) { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val selectedCategoryTitle = when (selectedCategory) {
        "FAVORITES" -> "FAVORITES"
        "RECENTLY ADDED" -> recentLabel
        "CONTINUE WATCHING" -> "RECENTLY WATCHED"
        else -> libraryByKey[selectedCategory]?.title?.uppercase() ?: selectedCategory
    }

    LaunchedEffect(selectedCategory, modeLibraries) {
        delay(900)
        val selectedIndex = modeLibraries.indexOfFirst { it.key == selectedCategory }
        val nextLibrary = when {
            selectedIndex >= 0 -> modeLibraries.getOrNull(selectedIndex + 1)
            selectedCategory == "RECENTLY ADDED" || selectedCategory == "CONTINUE WATCHING" -> modeLibraries.firstOrNull()
            else -> null
        }
        if (nextLibrary != null) {
            onPreloadLibrary(nextLibrary)
        }
    }

    val baseGridItems = when (selectedCategory) {
        "CONTINUE WATCHING" -> continueWatching.filter { itemMatchesMode(it, mode) }
        "FAVORITES" -> favoriteItems.filter { itemMatchesMode(it, mode) }
        "RECENTLY ADDED" -> recent
        else -> if (libraryByKey.containsKey(selectedCategory)) categoryItems else recent
    }

    val visibleGridItems = remember(selectedCategory, mediaItemsFingerprint(baseGridItems), searchText, sortNewestFirst, sortAz) {
        val searched = if (searchText.isBlank()) {
            baseGridItems
        } else {
            baseGridItems.filter { it.title.contains(searchText, ignoreCase = true) }
        }

        if (sortAz) searched.sortedBy { it.title.lowercase() }
        else if (sortNewestFirst) searched.sortedByDescending { it.addedAt }
        else searched.sortedBy { it.addedAt }
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, visibleGridItems.size, selectedCategory, loadingMore) {
        if (!libraryByKey.containsKey(selectedCategory)) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!loadingMore && visibleGridItems.isNotEmpty() && lastVisible >= visibleGridItems.lastIndex - 12) {
                    onLoadMore()
                }
            }
    }

    Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(
            modifier = Modifier
                .width(215.dp)
                .fillMaxHeight()
                .background(Color(0xE807111B), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Column {
                    Text(title, color = Color(0xFF00FF66), fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text("YOUR PLEX LIBRARIES", color = Color(0xFF7D8B99), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                }
            }

            item { VodSeriesMenuItem("FAVORITES", selectedCategory == "FAVORITES") { selectedCategory = "FAVORITES" } }
            item { VodSeriesMenuItem(recentLabel, selectedCategory == "RECENTLY ADDED") { selectedCategory = "RECENTLY ADDED" } }
            item { VodSeriesMenuItem("RECENTLY WATCHED", selectedCategory == "CONTINUE WATCHING") { selectedCategory = "CONTINUE WATCHING" } }

            items(modeLibraries, key = { it.key }) { library ->
                VodSeriesMenuItem(library.title.uppercase(), selectedCategory == library.key) {
                    selectedCategory = library.key
                    onOpenLibrary(library)
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
            item { VodSeriesMenuItem("UPDATE CONTENTS", false, onOpenUpdate) }
            item { VodSeriesMenuItem("SETTINGS", false, onOpenSettings) }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xB8050B11), RoundedCornerShape(8.dp)).padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchOpen) {
                    TextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.weight(1f).height(58.dp),
                        singleLine = true,
                        label = { Text("Search") }
                    )
                    Spacer(Modifier.width(12.dp))
                    FocusActionButton("CLOSE", Modifier.width(104.dp), Color(0xFF203040)) {
                        searchOpen = false
                        searchText = ""
                    }
                } else {
                    Column {
                        Text(selectedCategoryTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(if (movieMode) "MOVIES / VOD" else "TV SERIES", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FocusActionButton(if (sortNewestFirst) "NEW" else "OLD", Modifier.width(72.dp), Color(0xFF16202A)) { sortNewestFirst = !sortNewestFirst; sortAz = false }
                        FocusActionButton("A-Z", Modifier.width(66.dp), if (sortAz) Color(0xFFFFB000) else Color(0xFF16202A)) { sortAz = !sortAz }
                        FocusActionButton("SEARCH", Modifier.width(100.dp), Color(0xFF16202A)) { searchOpen = true }
                    }
                }
            }

            Text(if (loading) "Loading..." else if (loadingMore) "$status  Loading more in background..." else status, color = Color(0xFFB7C7D8), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))

            if (visibleGridItems.isEmpty()) {
                EmptyPanel(if (loading) "Loading..." else "Nothing found in this section.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(visibleGridItems, key = { it.ratingKey.ifBlank { it.key } }) { item ->
                        MediaPosterCard(
                            item = item,
                            artworkUrl = artworkUrls[item.ratingKey].orEmpty(),
                            isFavorite = favoriteKeys.contains(mediaItemStableId(item)),
                            onClick = { onSelectDetails(item) },
                            onLongClick = { onToggleFavorite(item) }
                        )
                    }
                    if (loadingMore) {
                        item {
                            LoadingMoreTile()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingMoreTile() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x99111820)),
        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF00E676), modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(10.dp))
            Text("Loading more...", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun VodSeriesMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || selected

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = when {
            focused -> Color(0xFF00D74B)
            selected -> Color(0xFF17232C)
            else -> Color.Transparent
        },
        border = if (focused) BorderStroke(2.dp, Color(0xFF8CFFAA)) else if (selected) BorderStroke(1.dp, Color(0xFFFFB000)) else null,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(30.dp)
                    .background(if (focused) Color.White else if (selected) Color(0xFFFFB000) else Color.Transparent)
            )
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                color = if (focused) Color.Black else if (selected) Color(0xFFFFB000) else Color(0xFFE4E9EE),
                fontSize = if (active) 16.sp else 15.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun mediaItemsFingerprint(items: List<PlexMediaItem>): String {
    return items.joinToString("|") { it.ratingKey.ifBlank { it.key } }
}


