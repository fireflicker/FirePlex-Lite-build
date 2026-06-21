package com.fireflicker.fireplex2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.fireflicker.fireplex2.data.AppAuthRepository
import com.fireflicker.fireplex2.data.ExoPlayerSettings
import com.fireflicker.fireplex2.data.OpenSubtitleResult
import com.fireflicker.fireplex2.data.OpenSubtitlesRepository
import com.fireflicker.fireplex2.data.PlexLibrary
import com.fireflicker.fireplex2.data.PlexMediaItem
import com.fireflicker.fireplex2.data.PlexPin
import com.fireflicker.fireplex2.data.PlexRepository
import com.fireflicker.fireplex2.data.PlexSubtitleTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class PlayerChoice {
    Exo
}

enum class ContentMode {
    Vod,
    Series
}

enum class AppDisplayMode {
    Tv,
    Mobile
}

@Composable
fun cachedImageModel(url: String): Any? {
    if (url.isBlank()) return null
    val context = LocalContext.current
    return remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = PlexRepository(applicationContext)
        setContent { FirePlexApp(repo) }
    }
}

@Composable
fun FirePlexApp(repo: PlexRepository) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val appAuth = remember { AppAuthRepository() }
    val openSubtitles = remember(context) { OpenSubtitlesRepository(context) }

    var status by remember { mutableStateOf("Checking saved Plex login...") }
    var needsAppLogin by remember { mutableStateOf(false) }
    var appLoginName by remember { mutableStateOf("") }
    var appLoginLoading by remember { mutableStateOf(false) }
    var appLoginMessage by remember { mutableStateOf("Enter your FirePlex username / player name.") }
    var accountExpiryDate by remember { mutableStateOf<String?>(null) }
    var accountDaysRemaining by remember { mutableStateOf<Int?>(null) }
    var pin by remember { mutableStateOf<PlexPin?>(null) }
    var linked by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var appDisplayMode by remember { mutableStateOf<AppDisplayMode?>(null) }
    var appDisplayModeLoaded by remember { mutableStateOf(false) }

    var serverName by remember { mutableStateOf<String?>(null) }
    var friendlyName by remember { mutableStateOf("FirePlex3.0") }
    var preferredPlayer by remember { mutableStateOf(PlayerChoice.Exo) }
    var streamMode by remember { mutableStateOf("auto") }
    var playbackAttemptMode by remember { mutableStateOf("direct_play") }
    var deviceProfile by remember { mutableStateOf(DeviceProfile.Auto) }
    var exoSettings by remember { mutableStateOf(ExoPlayerSettings()) }

    var libraries by remember { mutableStateOf<List<PlexLibrary>>(emptyList()) }
    var hiddenKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mediaItems by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var categoryMemoryCache by remember { mutableStateOf<Map<String, List<PlexMediaItem>>>(emptyMap()) }
    var categoryLoadJob by remember { mutableStateOf<Job?>(null) }
    var categoryLoadMoreJob by remember { mutableStateOf<Job?>(null) }
    var activeCategoryLibrary by remember { mutableStateOf<PlexLibrary?>(null) }
    var categoryNextOffset by remember { mutableStateOf(0) }
    var categoryHasMore by remember { mutableStateOf(false) }
    var preloadingLibraryKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var libraryLoadingMore by remember { mutableStateOf(false) }
    var recentlyMovies by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var recentlyShows by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var continueWatching by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var favoriteKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteItems by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var showFavoritesScreen by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showAdminDownloads by remember { mutableStateOf(false) }
    var adminDownloadsUnlocked by remember { mutableStateOf(false) }
    var globalSearchItems by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var artworkUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var backdropUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var homeArtworkUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var homeBackdropUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cachedAt by remember { mutableStateOf(0L) }

    var showSettings by remember { mutableStateOf(false) }
    var showUpdateScreen by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<ContentMode?>(null) }
    var menuOpen by remember { mutableStateOf(true) }
    var selectedLibrary by remember { mutableStateOf<PlexLibrary?>(null) }
    var selectedShow by remember { mutableStateOf<PlexMediaItem?>(null) }
    var selectedSeason by remember { mutableStateOf<PlexMediaItem?>(null) }
    var seasonItems by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var episodeItems by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var selectedDetailItem by remember { mutableStateOf<PlexMediaItem?>(null) }
    var selectedItem by remember { mutableStateOf<PlexMediaItem?>(null) }

    var playUrl by remember { mutableStateOf<String?>(null) }
    var selectedSubtitle by remember { mutableStateOf<PlexSubtitleTrack?>(null) }
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var externalSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var externalSubtitleName by remember { mutableStateOf<String?>(null) }
    var currentPlaybackPositionMs by remember { mutableStateOf(0L) }
    var offlinePlayback by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var playerRetryInProgress by remember { mutableStateOf(false) }
    var lastPlaybackReportKey by remember { mutableStateOf("") }
    var lastPlaybackReportState by remember { mutableStateOf("") }
    var lastPlaybackReportWallMs by remember { mutableStateOf(0L) }
    var lastPlaybackReportTimeMs by remember { mutableStateOf(-1L) }

    var speedResult by remember { mutableStateOf("Not tested yet") }
    var internetSpeedResult by remember { mutableStateOf("Not tested yet") }
    var updateVodStatus by remember { mutableStateOf("Waiting") }
    var updateSeriesStatus by remember { mutableStateOf("Waiting") }
    var updateArtworkStatus by remember { mutableStateOf("Waiting") }
    var favoriteToast by remember { mutableStateOf<String?>(null) }

    DisposableEffect(selectedItem) {
        val window = activity?.window
        if (window != null && selectedItem != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun sendPlayback(
        item: PlexMediaItem,
        state: String,
        timeMs: Long,
        durationMs: Long,
        saveLocalHistory: Boolean = true
    ) {
        val reportKey = item.ratingKey.ifBlank { item.key }
        val normalizedState = state.lowercase()
        val now = System.currentTimeMillis()
        val itemChanged = reportKey != lastPlaybackReportKey
        val stateChanged = normalizedState != lastPlaybackReportState
        val positionDelta = if (lastPlaybackReportTimeMs < 0L) Long.MAX_VALUE else kotlin.math.abs(timeMs - lastPlaybackReportTimeMs)
        val wallDelta = now - lastPlaybackReportWallMs
        val terminalState = normalizedState == "paused" || normalizedState == "stopped"

        if (!itemChanged && !stateChanged && positionDelta < 1_000L) return
        if (!itemChanged && !stateChanged && normalizedState == "playing" && wallDelta < 15_000L && positionDelta < 15_000L) return
        if (!itemChanged && !terminalState && normalizedState != "playing") return

        lastPlaybackReportKey = reportKey
        lastPlaybackReportState = normalizedState
        lastPlaybackReportWallMs = now
        lastPlaybackReportTimeMs = timeMs

        scope.launch {
            try {
                if (saveLocalHistory) {
                    repo.recordLocalPlayback(item, state, timeMs, durationMs)
                    continueWatching = repo.continueWatching()
                }
                repo.reportPlayback(item, state, timeMs, durationMs)
            } catch (_: Throwable) {
            }
        }
    }

    fun clearPlayback() {
        if (!offlinePlayback) {
            selectedItem?.let { sendPlayback(it, "stopped", currentPlaybackPositionMs, it.durationMs) }
        }
        selectedItem = null
        playUrl = null
        selectedSubtitle = null
        selectedSubtitleUrl = null
        externalSubtitleUrl = null
        externalSubtitleName = null
        currentPlaybackPositionMs = 0L
        offlinePlayback = false
        playerError = null
        playerRetryInProgress = false
        lastPlaybackReportKey = ""
        lastPlaybackReportState = ""
        lastPlaybackReportWallMs = 0L
        lastPlaybackReportTimeMs = -1L
    }

    fun goHome() {
        categoryLoadJob?.cancel()
        categoryLoadJob = null
        categoryLoadMoreJob?.cancel()
        categoryLoadMoreJob = null
        activeCategoryLibrary = null
        showSettings = false
        showUpdateScreen = false
        showFavoritesScreen = false
        showGlobalSearch = false
        showAdminDownloads = false
        selectedMode = null
        selectedLibrary = null
        mediaItems = emptyList()
        categoryMemoryCache = emptyMap()
        selectedShow = null
        selectedSeason = null
        seasonItems = emptyList()
        episodeItems = emptyList()
        selectedDetailItem = null
        clearPlayback()
        status = "Choose something to watch."
    }

    BackHandler {
        when {
            needsAppLogin -> {
                appLoginMessage = "A valid player name is required before FirePlex can open."
                showExitDialog = true
            }

            selectedItem != null -> {
                clearPlayback()
                status = "Choose something to play."
            }

            showGlobalSearch -> {
                showGlobalSearch = false
                status = "Choose something to watch."
            }

            showFavoritesScreen -> {
                showFavoritesScreen = false
                status = "Choose something to watch."
            }

            selectedDetailItem != null -> {
                selectedDetailItem = null
                status = "Choose something to play."
            }

            selectedSeason != null -> {
                selectedSeason = null
                episodeItems = emptyList()
                status = "Choose a season."
            }

            selectedShow != null -> {
                selectedShow = null
                selectedSeason = null
                seasonItems = emptyList()
                episodeItems = emptyList()
                status = "Choose a TV series."
            }

            selectedLibrary != null -> {
                selectedLibrary = null
                selectedShow = null
                selectedSeason = null
                seasonItems = emptyList()
                episodeItems = emptyList()
                selectedDetailItem = null
                status = "Choose something to watch."
            }

            selectedMode != null -> {
                selectedMode = null
                selectedDetailItem = null
                status = "Choose Movies or Series."
            }

            showSettings -> {
                showSettings = false
                status = "Choose something to watch."
            }

            showUpdateScreen -> {
                showUpdateScreen = false
                showSettings = true
                status = "Settings."
            }

            else -> showExitDialog = true
        }
    }

    suspend fun loadArtwork(items: List<PlexMediaItem>): Pair<Map<String, String>, Map<String, String>> {
        val thumbs = mutableMapOf<String, String>()
        val backs = mutableMapOf<String, String>()

        items
            .asSequence()
            .filter { it.ratingKey.isNotBlank() }
            .distinctBy { it.ratingKey }
            .forEach { item ->
                if (item.thumb.isNotBlank()) {
                    thumbs[item.ratingKey] = repo.imageUrl(item.thumb, width = 300, height = 450)
                }

                if (item.art.isNotBlank()) {
                    backs[item.ratingKey] = repo.imageUrl(item.art, width = 1280, height = 720)
                }
            }

        return thumbs to backs
    }

    suspend fun loadCachedContent(): Boolean {
        // Remove full-library caches created by older builds. Home indexes remain cached.
        repo.clearLibraryCaches()
        val cachedLibraries = repo.cachedLibraries()
        val cachedMovies = repo.cachedRecentlyAddedMovies()
        val cachedShows = repo.cachedRecentlyAddedShows()
        cachedAt = repo.cachedUpdatedAt()

        if (cachedLibraries.isEmpty() && cachedMovies.isEmpty() && cachedShows.isEmpty()) {
            return false
        }

        libraries = cachedLibraries
        recentlyMovies = cachedMovies
        recentlyShows = cachedShows
        continueWatching = repo.continueWatching()
        hiddenKeys = repo.hiddenLibraryKeys()

        val artwork = loadArtwork((recentlyMovies + recentlyShows + continueWatching).take(120))
        homeArtworkUrls = artwork.first
        homeBackdropUrls = artwork.second
        artworkUrls = artwork.first
        backdropUrls = artwork.second

        return true
    }

    suspend fun refreshFavoritesFromCache() {
        val keys = repo.favoriteKeys()
        favoriteKeys = keys
        if (keys.isEmpty()) {
            favoriteItems = emptyList()
            return
        }

        val collected = mutableListOf<PlexMediaItem>()
        collected += recentlyMovies
        collected += recentlyShows
        collected += continueWatching
        collected += mediaItems
        collected += seasonItems
        collected += episodeItems
        val favs = collected
            .filter { keys.contains(mediaItemStableId(it)) }
            .distinctBy { mediaItemStableId(it) }
        favoriteItems = favs

        if (favs.isNotEmpty()) {
            val artwork = loadArtwork(favs)
            artworkUrls = artworkUrls + artwork.first
            backdropUrls = backdropUrls + artwork.second
        }
    }

    fun preloadLibraryInBackground(library: PlexLibrary) {
        // Categories now load only when selected. Focus-based preloading previously retained
        // several complete libraries and could exhaust memory on lower-powered TV devices.
        if (library.key.isBlank()) return
    }


    fun updateContent() {
        categoryLoadJob?.cancel()
        categoryLoadJob = null
        categoryLoadMoreJob?.cancel()
        categoryLoadMoreJob = null
        activeCategoryLibrary = null
        scope.launch {
            loading = true
            showUpdateScreen = true
            showSettings = false
            selectedMode = null
            selectedLibrary = null
            selectedDetailItem = null
            updateVodStatus = "Waiting"
            updateSeriesStatus = "Waiting"
            updateArtworkStatus = "Waiting"
            status = "Updating home content..."

            try {
                if (repo.savedServerBase().isNullOrBlank()) {
                    val server = repo.autoSelectServer()
                    serverName = server.name.ifBlank { "Plex Media Server" }
                } else {
                    serverName = serverName ?: "Plex Media Server"
                }

                val freshLibraries = repo.libraries()
                libraries = freshLibraries
                hiddenKeys = repo.hiddenLibraryKeys()
                val enabled = freshLibraries.filterNot { hiddenKeys.contains(it.key) }

                updateVodStatus = "Updating home row..."
                val movies = repo.recentlyAddedMovies(enabled)
                recentlyMovies = movies
                updateVodStatus = "Completed"

                updateSeriesStatus = "Updating home row..."
                val shows = repo.recentlyAddedShows(enabled)
                recentlyShows = shows
                updateSeriesStatus = "Completed"

                val watching = repo.continueWatching()
                continueWatching = watching

                updateArtworkStatus = "Caching visible artwork..."
                val artwork = loadArtwork((movies + shows + watching).take(120))
                homeArtworkUrls = artwork.first
                homeBackdropUrls = artwork.second
                artworkUrls = artwork.first
                backdropUrls = artwork.second
                updateArtworkStatus = "Visible artwork cached"

                repo.saveHomeCache(freshLibraries, movies, shows)
                refreshFavoritesFromCache()
                cachedAt = repo.cachedUpdatedAt()
                status = "Home updated. Categories load when opened."
                loading = false
                delay(900)
                showUpdateScreen = false
            } catch (e: Throwable) {
                status = e.message ?: "Could not update media contents."
                if (updateVodStatus == "Updating home row...") updateVodStatus = "Failed"
                if (updateSeriesStatus == "Updating home row...") updateSeriesStatus = "Failed"
                if (updateArtworkStatus.startsWith("Caching")) updateArtworkStatus = "Failed"
                loading = false
            }
        }
    }

    fun clearContentCache() {
        scope.launch {
            repo.clearContentCache()
            recentlyMovies = emptyList()
            recentlyShows = emptyList()
            continueWatching = repo.continueWatching()
            refreshFavoritesFromCache()
            showFavoritesScreen = false
            showGlobalSearch = false
            showAdminDownloads = false
            globalSearchItems = emptyList()
            artworkUrls = emptyMap()
            backdropUrls = emptyMap()
            homeArtworkUrls = emptyMap()
            homeBackdropUrls = emptyMap()
            cachedAt = 0L
            status = "Content cache cleared. Run Update Contents to reload."
        }
    }

    fun signOutFirePlex() {
        categoryLoadJob?.cancel()
        categoryLoadJob = null
        categoryLoadMoreJob?.cancel()
        categoryLoadMoreJob = null
        activeCategoryLibrary = null
        scope.launch {
            repo.clearToken()
            linked = false
            pin = null
            needsAppLogin = false
            appLoginName = ""
            appLoginMessage = "Enter your FirePlex username / player name."
            serverName = null
            libraries = emptyList()
            hiddenKeys = emptySet()
            recentlyMovies = emptyList()
            recentlyShows = emptyList()
            continueWatching = emptyList()
            favoriteKeys = emptySet()
            favoriteItems = emptyList()
            showFavoritesScreen = false
            showGlobalSearch = false
            adminDownloadsUnlocked = false
            globalSearchItems = emptyList()
            mediaItems = emptyList()
            categoryMemoryCache = emptyMap()
            preloadingLibraryKeys = emptySet()
            libraryLoadingMore = false
            artworkUrls = emptyMap()
            backdropUrls = emptyMap()
            homeArtworkUrls = emptyMap()
            homeBackdropUrls = emptyMap()
            showSettings = false
            showUpdateScreen = false
            selectedMode = null
            selectedLibrary = null
            selectedShow = null
            selectedSeason = null
            seasonItems = emptyList()
            episodeItems = emptyList()
            selectedDetailItem = null
            clearPlayback()
            status = "Signed out. Press Generate Code to link your Plex account."
        }
    }

    fun refreshHomeInBackground() {
        scope.launch {
            try {
                val freshLibraries = repo.libraries()
                val hidden = repo.hiddenLibraryKeys()
                val enabled = freshLibraries.filterNot { hidden.contains(it.key) }
                val movies = repo.recentlyAddedMovies(enabled)
                val shows = repo.recentlyAddedShows(enabled)
                val watching = repo.continueWatching()
                val artwork = loadArtwork(movies + shows + watching)

                libraries = freshLibraries
                hiddenKeys = hidden
                recentlyMovies = movies
                recentlyShows = shows
                continueWatching = watching
                homeArtworkUrls = artwork.first
                homeBackdropUrls = artwork.second
                if (selectedMode == null && selectedLibrary == null) {
                    artworkUrls = artwork.first
                    backdropUrls = artwork.second
                }
                repo.saveHomeCache(freshLibraries, movies, shows)
                cachedAt = repo.cachedUpdatedAt()
                refreshFavoritesFromCache()
                if (selectedItem == null && !showSettings && !showUpdateScreen) {
                    status = "Content refreshed in the background."
                }
            } catch (_: Throwable) {
                // Cached content remains usable when the server is temporarily unavailable.
            }
        }
    }

    fun loadHome() {
        scope.launch {
            needsAppLogin = false
            loading = true
            status = "Loading FirePlex home..."

            try {
                if (repo.savedServerBase().isNullOrBlank()) {
                    val server = repo.autoSelectServer()
                    serverName = server.name.ifBlank { "Plex Media Server" }
                } else {
                    serverName = serverName ?: "Plex Media Server"
                }
                friendlyName = repo.friendlyDeviceName()
                deviceProfile = DeviceProfile.fromKey(repo.deviceProfile())
                preferredPlayer = PlayerChoice.Exo
                repo.savePreferredPlayer("exo")
                repo.migratePlaybackPreferenceToDirectFirst()
                streamMode = repo.streamMode()
                exoSettings = repo.exoPlayerSettings()

                if (deviceProfile == DeviceProfile.Auto) {
                    val automatic = deviceProfileConfig(DeviceProfile.Auto, activity ?: return@launch)
                    preferredPlayer = automatic.player
                    streamMode = automatic.streamMode
                    exoSettings = automatic.exoSettings
                }

                showSettings = false
                showUpdateScreen = false
                showFavoritesScreen = false
                showGlobalSearch = false
                selectedMode = null
                selectedLibrary = null
                selectedShow = null
                selectedSeason = null
                seasonItems = emptyList()
                episodeItems = emptyList()
                selectedDetailItem = null
                clearPlayback()
                mediaItems = emptyList()

                val loadedCache = loadCachedContent()
                refreshFavoritesFromCache()
                if (!loadedCache) {
                    status = "No cached content yet. Run Update Contents."
                    showUpdateScreen = true
                    updateContent()
                } else {
                    status = "Choose Movies or Series."
                    refreshHomeInBackground()
                }
            } catch (e: Throwable) {
                status = e.message ?: "Could not load home screen."
            }

            loading = false
        }
    }

    fun setLibraryEnabled(library: PlexLibrary, enabled: Boolean) {
        scope.launch {
            repo.setLibraryEnabled(library.key, enabled)
            hiddenKeys = repo.hiddenLibraryKeys()
        }
    }

    fun saveFriendlyName(name: String) {
        scope.launch {
            repo.saveFriendlyDeviceName(name)
            friendlyName = repo.friendlyDeviceName()
            status = "Saved device name."
        }
    }

    fun saveDisplayMode(mode: AppDisplayMode) {
        scope.launch {
            repo.saveAppDisplayMode(if (mode == AppDisplayMode.Mobile) "mobile" else "tv")
            appDisplayMode = mode
            appDisplayModeLoaded = true
            status = if (mode == AppDisplayMode.Mobile) "Saved mobile layout." else "Saved TV layout."
        }
    }




    fun openFavorites() {
        scope.launch {
            loading = true
            showFavoritesScreen = true
            showSettings = false
            showUpdateScreen = false
            selectedMode = null
            selectedLibrary = null
            selectedDetailItem = null
            status = "Loading favourites..."
            refreshFavoritesFromCache()
            status = if (favoriteItems.isEmpty()) "No favourites yet. Hold OK / long press a poster to add one." else "Choose a favourite."
            loading = false
        }
    }

    fun openGlobalSearch() {
        scope.launch {
            loading = true
            showGlobalSearch = true
            showFavoritesScreen = false
            showSettings = false
            showUpdateScreen = false
            selectedMode = null
            selectedLibrary = null
            selectedDetailItem = null
            status = "Preparing global search for Movies + TV Series..."

            val collected = mutableListOf<PlexMediaItem>()
            fun publishSearchItems(message: String) {
                globalSearchItems = collected
                    .filter { it.title.isNotBlank() }
                    .distinctBy { mediaItemStableId(it) }
                    .sortedBy { it.title.lowercase() }
                status = message
            }

            collected += recentlyMovies
            collected += recentlyShows
            collected += continueWatching
            collected += favoriteItems
            collected += mediaItems
            collected += seasonItems
            collected += episodeItems
            publishSearchItems("Loading cached Movies + TV Series...")

            publishSearchItems("Search loaded and recent Movies + TV Series.")
            loading = false
        }
    }

    fun toggleFavorite(item: PlexMediaItem) {
        scope.launch {
            val isFavorite = repo.toggleFavorite(item)
            val key = mediaItemStableId(item)
            favoriteKeys = if (isFavorite) favoriteKeys + key else favoriteKeys - key
            favoriteItems = if (isFavorite) {
                (favoriteItems + item).distinctBy { mediaItemStableId(it) }
            } else {
                favoriteItems.filterNot { mediaItemStableId(it) == key }
            }
            status = if (isFavorite) "Added to favourites." else "Removed from favourites."
            favoriteToast = if (isFavorite) "Added to favourites" else "Removed from favourites"
            delay(1400)
            favoriteToast = null
        }
    }

    fun appLogin() {
        val cleanName = appLoginName.trim()
        if (cleanName.isBlank()) {
            appLoginMessage = "Enter your username / player name first."
            return
        }

        scope.launch {
            appLoginLoading = true
            appLoginMessage = "Checking your FirePlex account..."
            status = "Checking account..."

            try {
                val result = appAuth.login(username = cleanName, deviceId = "fireplex-android")
                if (result.allowed) {
                    repo.saveAppUsername(cleanName)
                    repo.saveFriendlyDeviceName(cleanName)
                    friendlyName = repo.friendlyDeviceName()
                    accountExpiryDate = result.expiryDate
                    accountDaysRemaining = result.daysRemaining
                    needsAppLogin = false
                    appLoginMessage = "Welcome ${result.username ?: cleanName}. Access expires ${result.expiryDate ?: "on the account expiry date"}."
                    status = "Account active. Loading FirePlex home..."
                    loadHome()
                } else {
                    repo.clearAppUsername()
                    accountExpiryDate = result.expiryDate
                    accountDaysRemaining = result.daysRemaining
                    val reasonText = when (result.reason) {
                        "expired" -> "This account expired on ${result.expiryDate ?: "the saved expiry date"}."
                        "not_found" -> "Username not found on the FirePlex panel."
                        else -> result.reason ?: "Account is not active."
                    }
                    appLoginMessage = reasonText
                    status = reasonText
                }
            } catch (e: Throwable) {
                appLoginMessage = e.message ?: "Could not contact FirePlex login server."
                status = appLoginMessage
            }

            appLoginLoading = false
        }
    }

    fun checkSavedAppLogin(savedName: String, reloadHome: Boolean = true) {
        scope.launch {
            appLoginLoading = true
            appLoginName = savedName
            appLoginMessage = "Checking your FirePlex account..."
            status = "Checking account..."

            try {
                val result = appAuth.login(username = savedName, deviceId = "fireplex-android")
                if (result.allowed) {
                    repo.saveAppUsername(savedName)
                    repo.saveFriendlyDeviceName(savedName)
                    friendlyName = repo.friendlyDeviceName()
                    accountExpiryDate = result.expiryDate
                    accountDaysRemaining = result.daysRemaining
                    needsAppLogin = false
                    status = buildString {
                        append("Account active")
                        result.expiryDate?.let { append(" until $it") }
                        result.daysRemaining?.let { append(" ($it days remaining)") }
                        append(".")
                    }
                    if (reloadHome) loadHome()
                } else {
                    repo.clearAppUsername()
                    accountExpiryDate = result.expiryDate
                    accountDaysRemaining = result.daysRemaining
                    needsAppLogin = true
                    val reasonText = when (result.reason) {
                        "expired" -> "This account expired on ${result.expiryDate ?: "the saved expiry date"}."
                        "not_found" -> "Username not found on the FirePlex panel."
                        else -> result.reason ?: "Account is not active."
                    }
                    appLoginMessage = reasonText
                    status = reasonText
                }
            } catch (e: Throwable) {
                needsAppLogin = false
                status = "Could not check account, using saved login."
                if (reloadHome) loadHome()
            }

            appLoginLoading = false
        }
    }

    fun refreshAccount() {
        scope.launch {
            val savedName = repo.savedAppUsername()
            if (savedName.isNullOrBlank()) {
                needsAppLogin = true
                status = "No saved account. Enter your player name again."
            } else {
                checkSavedAppLogin(savedName, reloadHome = false)
            }
        }
    }

    LaunchedEffect(Unit) {
        // FirePlex is now TV-first only. Mobile chooser removed.
        appDisplayMode = AppDisplayMode.Tv
        repo.saveAppDisplayMode("tv")
        appDisplayModeLoaded = true

        val token = repo.savedToken()
        if (!token.isNullOrBlank()) {
            linked = true
            val savedName = repo.savedAppUsername()
            if (!savedName.isNullOrBlank()) {
                needsAppLogin = false
                loadHome()
                checkSavedAppLogin(savedName, reloadHome = false)
            } else {
                needsAppLogin = true
                status = "Enter your FirePlex player name to continue."
            }
        } else {
            status = "Press Generate Code to link your Plex account."
        }
    }

    fun savePlayerChoice(choice: PlayerChoice) {
        scope.launch {
            preferredPlayer = choice
            repo.savePreferredPlayer(
                when (choice) {
                    PlayerChoice.Exo -> "exo"
                }
            )
            status = "Saved player setting: ${playerLabel(choice)}."
        }
    }

    fun saveStreamMode(mode: String) {
        scope.launch {
            streamMode = mode
            repo.saveStreamMode(mode)
            status = "Saved stream type: ${streamModeLabel(mode)}."
        }
    }

    fun saveDeviceProfile(profile: DeviceProfile) {
        scope.launch {
            val config = deviceProfileConfig(profile, activity ?: return@launch)
            deviceProfile = profile
            preferredPlayer = config.player
            streamMode = config.streamMode
            exoSettings = config.exoSettings
            repo.saveDeviceProfile(profile.key)
            repo.savePreferredPlayer("exo")
            repo.saveStreamMode(config.streamMode)
            repo.saveExoPlayerSettings(config.exoSettings)
            status = "Applied ${profile.label}: EXO + ${streamModeLabel(config.streamMode)}."
        }
    }

    fun saveExoSettings(settings: ExoPlayerSettings) {
        scope.launch {
            exoSettings = settings
            repo.saveExoPlayerSettings(settings)
            status = "Saved ExoPlayer settings."
        }
    }

    fun runSpeedTest() {
        scope.launch {
            speedResult = "Testing..."
            try {
                speedResult = repo.speedTest()
            } catch (e: Throwable) {
                speedResult = e.message ?: "Speed test failed."
            }
        }
    }

    fun runInternetSpeedTest() {
        scope.launch {
            internetSpeedResult = "Testing download and upload..."
            try {
                internetSpeedResult = repo.internetSpeedTest()
            } catch (e: Throwable) {
                internetSpeedResult = e.message ?: "Internet speed test failed."
            }
        }
    }

    fun openTvShow(show: PlexMediaItem) {
        scope.launch {
            loading = true
            status = "Loading seasons..."

            try {
                selectedShow = show
                selectedSeason = null
                selectedDetailItem = null
                episodeItems = emptyList()
                seasonItems = repo.tvSeasons(show)

                val artwork = loadArtwork(seasonItems + listOf(show))
                artworkUrls = homeArtworkUrls + artwork.first
                backdropUrls = homeBackdropUrls + artwork.second

                status = if (seasonItems.isEmpty()) {
                    "No seasons found for ${show.title}."
                } else {
                    "Choose a season."
                }
            } catch (e: Throwable) {
                status = e.message ?: "Could not load seasons."
            }

            loading = false
        }
    }

    fun openTvSeason(season: PlexMediaItem) {
        scope.launch {
            loading = true
            status = "Loading episodes..."

            try {
                selectedSeason = season
                selectedDetailItem = null
                episodeItems = repo.seasonEpisodes(season)

                val artwork = loadArtwork(episodeItems + listOf(season))
                artworkUrls = homeArtworkUrls + artwork.first
                backdropUrls = homeBackdropUrls + artwork.second

                status = if (episodeItems.isEmpty()) {
                    "No episodes found in ${season.title}."
                } else {
                    "Choose an episode."
                }
            } catch (e: Throwable) {
                status = e.message ?: "Could not load episodes."
            }

            loading = false
        }
    }

    fun openDetails(item: PlexMediaItem) {
        showGlobalSearch = false
        showAdminDownloads = false
        showFavoritesScreen = false
        showSettings = false
        showUpdateScreen = false
        if (item.type.equals("show", ignoreCase = true) || item.type.equals("tv", ignoreCase = true)) {
            openTvShow(item)
            return
        }

        if (item.type.equals("season", ignoreCase = true)) {
            openTvSeason(item)
            return
        }

        scope.launch {
            loading = true
            status = "Loading details..."

            try {
                val detailed = repo.mediaDetails(item)
                val localPosition = repo.localPlaybackPosition(detailed.ratingKey)
                selectedDetailItem = detailed.copy(viewOffsetMs = localPosition)

                val artwork = loadArtwork(listOf(detailed))
                artworkUrls = artworkUrls + artwork.first
                backdropUrls = backdropUrls + artwork.second

                status = "Choose playback options."
            } catch (e: Throwable) {
                selectedDetailItem = item
                status = e.message ?: "Could not load full details."
            }

            loading = false
        }
    }

    fun openPlayer(item: PlexMediaItem, subtitle: PlexSubtitleTrack?, fromStart: Boolean = false) {
        selectedItem = item
        offlinePlayback = false
        selectedSubtitle = subtitle
        playUrl = null
        selectedSubtitleUrl = null
        externalSubtitleUrl = null
        externalSubtitleName = null
        currentPlaybackPositionMs = 0L
        playerError = null
        playerRetryInProgress = false
        playbackAttemptMode = if (streamMode == "auto") "direct_play" else streamMode

        scope.launch {
            loading = true
            status = "Opening ${item.title}..."

            try {
                currentPlaybackPositionMs = if (fromStart) 0L else repo.localPlaybackPosition(item.ratingKey)
                playUrl = repo.streamUrl(item, playbackAttemptMode)
                selectedSubtitleUrl = subtitle?.let { repo.subtitleUrl(it) }?.takeIf { it.isNotBlank() }
                status = "Playing ${item.title} - ${streamModeLabel(playbackAttemptMode)}"
                sendPlayback(item, "playing", currentPlaybackPositionMs, item.durationMs, saveLocalHistory = false)
            } catch (e: Throwable) {
                status = e.message ?: "Could not open video player."
            }

            loading = false
        }
    }

    fun openOfflinePlayer(download: OfflineDownloadItem) {
        selectedItem = PlexMediaItem(
            ratingKey = "offline-${download.id}",
            key = download.path,
            title = download.title,
            type = download.type.ifBlank { "video" },
            summary = download.summary,
            thumb = "",
            art = "",
            year = download.year,
            contentRating = "",
            durationMs = 0L,
            viewOffsetMs = 0L,
            addedAt = download.downloadedAt,
            partKey = download.path,
            subtitles = emptyList()
        )
        offlinePlayback = true
        selectedSubtitle = null
        playUrl = OfflineDownloadRepository(context).playUri(download)
        selectedSubtitleUrl = null
        externalSubtitleUrl = null
        externalSubtitleName = null
        currentPlaybackPositionMs = 0L
        playerError = null
        playerRetryInProgress = false
        status = "Playing downloaded copy: ${download.title}"
    }

    fun downloadMediaFromDetails(item: PlexMediaItem) {
        if (!adminDownloadsUnlocked) {
            status = "Unlock Admin first to download media."
            favoriteToast = "Unlock Admin first"
            scope.launch {
                delay(1400)
                favoriteToast = null
            }
            return
        }

        favoriteToast = "Download started: ${item.title}"
        scope.launch {
            delay(1400)
            if (favoriteToast?.startsWith("Download started:") == true) {
                favoriteToast = null
            }
        }

        scope.launch {
            loading = true
            status = "Preparing download..."
            try {
                val detailed = if (item.partKey.isBlank()) repo.mediaDetails(item) else item
                val stream = repo.streamUrl(detailed, "direct_play")
                val downloaded = OfflineDownloadRepository(context).download(detailed, stream) { percent, done, total ->
                    status = if (total > 0L) {
                        "Downloading ${detailed.title}: $percent% (${formatBytes(done)} / ${formatBytes(total)})"
                    } else {
                        "Downloading ${detailed.title}: ${formatBytes(done)}"
                    }
                }
                status = "Downloaded ${downloaded.title} for offline play."
                favoriteToast = "Downloaded ${downloaded.title}"
                delay(1600)
                favoriteToast = null
            } catch (e: Throwable) {
                status = e.message ?: "Download failed."
                favoriteToast = "Download failed"
                delay(1600)
                favoriteToast = null
            } finally {
                loading = false
            }
        }
    }

    fun retryWithNextStreamMode(lastError: String? = null) {
        val item = selectedItem ?: return
        if (playerRetryInProgress) return

        val nextMode = when (playbackAttemptMode) {
            "direct_play" -> "direct_stream"
            "direct_stream" -> "transcode"
            else -> null
        }

        if (nextMode == null) {
            playerError = "Player failed${lastError?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}"
            return
        }

        playerRetryInProgress = true
        scope.launch {
            loading = true
            playerError = null
            preferredPlayer = PlayerChoice.Exo
            playbackAttemptMode = nextMode
            status = "${streamModeLabel(nextMode)} fallback for ${item.title}..."
            try {
                playUrl = repo.streamUrl(item, nextMode)
            } catch (e: Throwable) {
                playerRetryInProgress = false
                loading = false
                retryWithNextStreamMode(e.message)
                return@launch
            } finally {
                loading = false
                playerRetryInProgress = false
            }
        }
    }

    fun putSafeCategoryCache(libraryKey: String, items: List<PlexMediaItem>) {
        if (libraryKey.isBlank() || items.isEmpty()) return
        // Room owns persistence. RAM keeps only a small preview of the active category.
        categoryMemoryCache = mapOf(libraryKey to items.take(160))
    }

    fun loadLibraryItems(library: PlexLibrary) {
        val pageSize = 80
        categoryLoadJob?.cancel()
        categoryLoadMoreJob?.cancel()
        categoryLoadMoreJob = null
        activeCategoryLibrary = library
        categoryNextOffset = 0
        categoryHasMore = false
        categoryLoadJob = scope.launch {
            loading = true
            libraryLoadingMore = false
            status = "Opening ${library.title}..."

            try {
                selectedLibrary = null
                selectedShow = null
                selectedSeason = null
                seasonItems = emptyList()
                episodeItems = emptyList()
                selectedDetailItem = null
                clearPlayback()
                showSettings = false
                showUpdateScreen = false
                seasonItems = emptyList()
                episodeItems = emptyList()
                artworkUrls = homeArtworkUrls
                backdropUrls = homeBackdropUrls
                repo.clearLibraryCaches()

                val cachedItems = repo.cachedCategoryPage(library.key, offset = 0, limit = pageSize)
                if (cachedItems.isNotEmpty()) {
                    mediaItems = cachedItems
                    putSafeCategoryCache(library.key, cachedItems)
                    val cachedArtwork = loadArtwork(cachedItems)
                    artworkUrls = homeArtworkUrls + cachedArtwork.first
                    backdropUrls = homeBackdropUrls + cachedArtwork.second
                    status = "Showing saved ${library.title}. Checking Plex for updates..."
                    loading = false
                } else {
                    mediaItems = emptyList()
                    status = "Loading ${library.title}..."
                }

                val firstPage = repo.libraryItemsPage(library, start = 0, size = pageSize)
                if (activeCategoryLibrary?.key != library.key) return@launch

                if (firstPage.isNotEmpty()) {
                    mediaItems = firstPage
                    putSafeCategoryCache(library.key, firstPage)
                    repo.replaceCachedCategory(library.key, firstPage)
                    val firstArtwork = loadArtwork(firstPage)
                    artworkUrls = homeArtworkUrls + firstArtwork.first
                    backdropUrls = homeBackdropUrls + firstArtwork.second
                    refreshFavoritesFromCache()
                }

                categoryNextOffset = firstPage.size
                categoryHasMore = firstPage.size == pageSize
                status = when {
                    firstPage.isEmpty() && cachedItems.isEmpty() -> "No videos found in ${library.title}."
                    categoryHasMore -> "Choose something to watch. More titles load as you browse."
                    else -> "Choose something to watch."
                }
                loading = false
                libraryLoadingMore = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                status = e.message ?: "Could not load category content."
                loading = false
                libraryLoadingMore = false
            }
        }
    }

    fun loadNextCategoryPage() {
        val library = activeCategoryLibrary ?: return
        if (!categoryHasMore || libraryLoadingMore || loading || categoryLoadMoreJob?.isActive == true) return

        val pageSize = 80
        val start = categoryNextOffset
        categoryLoadMoreJob = scope.launch {
            libraryLoadingMore = true
            try {
                val nextPage = repo.libraryItemsPage(library, start = start, size = pageSize)
                if (activeCategoryLibrary?.key != library.key) return@launch

                val existingKeys = mediaItems.mapTo(HashSet()) { mediaItemStableId(it) }
                val uniquePage = nextPage.filter { existingKeys.add(mediaItemStableId(it)) }
                if (uniquePage.isNotEmpty()) {
                    mediaItems = mediaItems + uniquePage
                    putSafeCategoryCache(library.key, mediaItems)
                    repo.appendCachedCategory(library.key, start, uniquePage)
                    val pageArtwork = loadArtwork(uniquePage)
                    artworkUrls = artworkUrls + pageArtwork.first
                    backdropUrls = backdropUrls + pageArtwork.second
                }

                categoryNextOffset = start + nextPage.size
                categoryHasMore = nextPage.size == pageSize
                status = if (categoryHasMore) "More titles load as you browse." else "All titles loaded."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val cachedPage = repo.cachedCategoryPage(library.key, offset = start, limit = pageSize)
                if (activeCategoryLibrary?.key == library.key && cachedPage.isNotEmpty()) {
                    val existingKeys = mediaItems.mapTo(HashSet()) { mediaItemStableId(it) }
                    val uniqueCached = cachedPage.filter { existingKeys.add(mediaItemStableId(it)) }
                    mediaItems = mediaItems + uniqueCached
                    categoryNextOffset = start + cachedPage.size
                    categoryHasMore = cachedPage.size == pageSize
                    val cachedArtwork = loadArtwork(uniqueCached)
                    artworkUrls = artworkUrls + cachedArtwork.first
                    backdropUrls = backdropUrls + cachedArtwork.second
                    status = "Showing saved titles. Plex is currently unavailable."
                } else {
                    status = e.message ?: "Could not load more titles."
                }
            } finally {
                libraryLoadingMore = false
            }
        }
    }


    fun checkCurrentPinNow() {
        val currentPin = pin
        if (currentPin == null) {
            status = "Generate a Plex code first."
            return
        }

        scope.launch {
            loading = true
            status = "Checking Plex link..."

            try {
                val latest = repo.checkPin(currentPin.id)

                if (!latest.authToken.isNullOrBlank()) {
                    repo.saveToken(latest.authToken)
                    linked = true
                    val savedName = repo.savedAppUsername()
                    if (!savedName.isNullOrBlank()) {
                        needsAppLogin = false
                        status = "Plex linked. Checking account..."
                        loading = false
                        checkSavedAppLogin(savedName)
                    } else {
                        needsAppLogin = true
                        status = "Plex linked. Enter your FirePlex player name."
                        loading = false
                    }
                } else {
                    status = "Not linked yet. Enter the code on plex.tv/link then press Check Link."
                    loading = false
                }
            } catch (e: Throwable) {
                loading = false
                status = e.message ?: "Could not check Plex link."
            }
        }
    }

    fun startLink() {
        categoryLoadJob?.cancel()
        categoryLoadJob = null
        categoryLoadMoreJob?.cancel()
        categoryLoadMoreJob = null
        activeCategoryLibrary = null
        scope.launch {
            loading = true
            status = "Getting Plex link code..."
            linked = false
            pin = null
            serverName = null
            libraries = emptyList()
            hiddenKeys = emptySet()
            recentlyMovies = emptyList()
            recentlyShows = emptyList()
            continueWatching = emptyList()
            artworkUrls = emptyMap()
            backdropUrls = emptyMap()
            showSettings = false
            showUpdateScreen = false
            selectedMode = null
            selectedLibrary = null
            selectedShow = null
            selectedSeason = null
            seasonItems = emptyList()
            episodeItems = emptyList()
            selectedDetailItem = null
            mediaItems = emptyList()
            clearPlayback()
            needsAppLogin = false
            appLoginName = ""
            appLoginMessage = "Enter your FirePlex username / player name."

            try {
                val created = repo.createPin()
                pin = created
                val code = created.code.orEmpty()

                status = if (code.isBlank()) "Plex returned no code." else "Enter this code at plex.tv/link"
                loading = false

                repeat(240) {
                    delay(2000)
                    val latest = repo.checkPin(created.id)

                    if (!latest.authToken.isNullOrBlank()) {
                        repo.saveToken(latest.authToken)
                        linked = true
                        val savedName = repo.savedAppUsername()
                        if (!savedName.isNullOrBlank()) {
                            needsAppLogin = false
                            status = "Plex linked. Checking account..."
                            loading = false
                            checkSavedAppLogin(savedName)
                        } else {
                            needsAppLogin = true
                            status = "Plex linked. Enter your FirePlex player name."
                            loading = false
                        }
                        return@launch
                    }
                }

                status = "Code expired. Press Generate Code again."
            } catch (e: Throwable) {
                loading = false
                status = e.message ?: "Failed to create Plex link code."
            }
        }
    }

    // Keep the main menus clean and fast: no rotating video/backdrop backgrounds.
    val backgroundArt = ""

    val systemDensity = LocalDensity.current
    val compactScale = 0.82f
    val compactDensity = remember(systemDensity.density, systemDensity.fontScale, compactScale) {
        Density(systemDensity.density * compactScale, systemDensity.fontScale)
    }

    CompositionLocalProvider(LocalDensity provides compactDensity) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00E676))) {
        if (!appDisplayModeLoaded) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("Loading FirePlex layout...", color = Color.White, fontSize = 20.sp)
            }
            return@MaterialTheme
        }

        // TV mode only. The old TV/Mobile choice page has been removed.
        val mobileMode = false

        if (selectedItem != null) {
            SelectedVideoScreen(
                item = selectedItem!!,
                playUrl = playUrl,
                subtitleTracks = selectedItem!!.subtitles,
                selectedSubtitle = selectedSubtitle,
                subtitleUrl = selectedSubtitleUrl ?: externalSubtitleUrl,
                externalSubtitleName = externalSubtitleName,
                playerChoice = preferredPlayer,
                exoSettings = exoSettings,
                startPositionMs = currentPlaybackPositionMs,
                loading = loading,
                status = status,
                errorMessage = playerError,
                onSubtitleSelected = { track ->
                    selectedSubtitle = track
                    scope.launch {
                        selectedSubtitleUrl = track?.let { repo.subtitleUrl(it) }?.takeIf { it.isNotBlank() }
                        externalSubtitleUrl = null
                        externalSubtitleName = null
                    }
                },
                onSearchOpenSubtitles = { query, language ->
                    openSubtitles.search(selectedItem!!, query, language)
                },
                onOpenSubtitleSelected = { result ->
                    loading = true
                    status = "Downloading subtitles..."
                    try {
                        externalSubtitleUrl = openSubtitles.downloadSubtitleUrl(result.fileId)
                        externalSubtitleName = result.displayName
                        selectedSubtitle = null
                        selectedSubtitleUrl = null
                        status = "Using subtitle: ${result.displayName}"
                    } finally {
                        loading = false
                    }
                },
                onPlayerError = { message ->
                    retryWithNextStreamMode(message)
                },
                onRetryExoTranscode = { retryWithNextStreamMode(playerError) },
                onPlayback = { state, time, duration ->
                    if (time > 0L) currentPlaybackPositionMs = time
                    if (!offlinePlayback) {
                        selectedItem?.let { sendPlayback(it, state, time, duration) }
                    }
                }
            )
            return@MaterialTheme
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050608))) {
            Image(
                painter = painterResource(id = R.drawable.fireplex_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
            )

            if (false && backgroundArt.isNotBlank() && !needsAppLogin) {
                AsyncImage(
                    model = cachedImageModel(backgroundArt),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color(0xF7050608), Color(0xE6050608), Color(0xB3050608))))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x66050608), Color(0xF2050608))))
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(if (mobileMode) 10.dp else 22.dp)) {
                when {
                    needsAppLogin -> {
                        AppLoginScreen(
                            playerName = appLoginName,
                            message = appLoginMessage,
                            loading = appLoginLoading,
                            onPlayerNameChange = { appLoginName = it },
                            onContinue = { appLogin() },
                            onRelinkPlex = {
                                startLink()
                            }
                        )
                    }

                    showAdminDownloads -> {
                        AdminDownloadsScreen(
                            libraries = libraries,
                            hiddenKeys = hiddenKeys,
                            artworkUrls = artworkUrls,
                            unlocked = adminDownloadsUnlocked,
                            onBack = {
                                showAdminDownloads = false
                                showSettings = true
                            },
                            onUnlocked = { adminDownloadsUnlocked = true },
                            onSearch = { query, enabledLibraries ->
                                repo.searchDownloadableVideos(query, enabledLibraries)
                            },
                            onStreamUrl = { item ->
                                val detailed = if (item.partKey.isBlank()) repo.mediaDetails(item) else item
                                repo.streamUrl(detailed, "direct_play")
                            },
                            onPlayOffline = { openOfflinePlayer(it) }
                        )
                    }

                    showGlobalSearch -> {
                        GlobalSearchScreen(
                            items = globalSearchItems,
                            artworkUrls = artworkUrls,
                            favoriteKeys = favoriteKeys,
                            loading = loading,
                            status = status,
                            onSearchRemote = { query ->
                                repo.searchMoviesAndShows(query, libraries.filterNot { hiddenKeys.contains(it.key) })
                            },
                            onSelectDetails = { openDetails(it) },
                            onToggleFavorite = { toggleFavorite(it) }
                        )
                    }

                    showFavoritesScreen -> {
                        FavoritesScreen(
                            items = favoriteItems,
                            artworkUrls = artworkUrls,
                            favoriteKeys = favoriteKeys,
                            loading = loading,
                            status = status,
                            mobileMode = mobileMode,
                            onSelectDetails = { openDetails(it) },
                            onToggleFavorite = { toggleFavorite(it) }
                        )
                    }

                    selectedDetailItem != null -> {
                        MediaDetailsScreen(
                            item = selectedDetailItem!!,
                            artworkUrl = artworkUrls[selectedDetailItem!!.ratingKey].orEmpty(),
                            backdropUrl = backdropUrls[selectedDetailItem!!.ratingKey].orEmpty(),
                            playerChoice = preferredPlayer,
                            onPlay = { item, subtitle, fromStart -> openPlayer(item, subtitle, fromStart) },
                            showDownloadButton = adminDownloadsUnlocked,
                            onDownload = { downloadMediaFromDetails(it) }
                        )
                    }

                    showSettings -> {
                        SettingsScreen(
                            libraries = libraries,
                            hiddenKeys = hiddenKeys,
                            friendlyName = friendlyName,
                            playerChoice = preferredPlayer,
                            streamMode = streamMode,
                            deviceProfile = deviceProfile,
                            exoSettings = exoSettings,
                            speedResult = speedResult,
                            internetSpeedResult = internetSpeedResult,
                            status = status,
                            cachedAt = cachedAt,
                            accountExpiryDate = accountExpiryDate,
                            accountDaysRemaining = accountDaysRemaining,
                            appDisplayMode = appDisplayMode ?: AppDisplayMode.Tv,
                            onSaveAppDisplayMode = { saveDisplayMode(it) },
                            onSaveFriendlyName = { saveFriendlyName(it) },
                            onSetLibraryEnabled = { library, enabled -> setLibraryEnabled(library, enabled) },
                            onSavePlayerChoice = { savePlayerChoice(it) },
                            onSaveStreamMode = { saveStreamMode(it) },
                            onSaveDeviceProfile = { saveDeviceProfile(it) },
                            onSaveExoSettings = { saveExoSettings(it) },
                            onRunSpeedTest = { runSpeedTest() },
                            onRunInternetSpeedTest = { runInternetSpeedTest() },
                            onOpenUpdate = {
                                showSettings = false
                                showUpdateScreen = true
                            },
                            onOpenAdminDownloads = {
                                showSettings = false
                                showAdminDownloads = true
                            },
                            onClearCache = { clearContentCache() },
                            onRefreshAccount = { refreshAccount() },
                            onSignOut = { signOutFirePlex() },
                            onCloseSettings = { showSettings = false }
                        )
                    }

                    showUpdateScreen -> {
                        UpdateContentsScreen(
                            loading = loading,
                            status = status,
                            vodStatus = updateVodStatus,
                            seriesStatus = updateSeriesStatus,
                            artworkStatus = updateArtworkStatus,
                            cachedAt = cachedAt,
                            onStartUpdate = { updateContent() },
                            onClearCache = { clearContentCache() },
                            onBack = {
                                showUpdateScreen = false
                                showSettings = true
                            }
                        )
                    }

                    selectedSeason != null -> {
                        TvEpisodesScreen(
                            show = selectedShow,
                            season = selectedSeason!!,
                            episodes = episodeItems,
                            artworkUrls = artworkUrls,
                            status = status,
                            loading = loading,
                            onSelectEpisode = { openDetails(it) }
                        )
                    }

                    selectedShow != null -> {
                        TvSeasonsScreen(
                            show = selectedShow!!,
                            seasons = seasonItems,
                            artworkUrls = artworkUrls,
                            status = status,
                            loading = loading,
                            onSelectSeason = { openTvSeason(it) }
                        )
                    }

                    selectedLibrary != null -> {
                        LibraryContentScreen(
                            library = selectedLibrary!!,
                            mediaItems = mediaItems,
                            artworkUrls = artworkUrls,
                            status = status,
                            loading = loading,
                            onSelectDetails = { openDetails(it) }
                        )
                    }

                    selectedMode != null -> {
                        val enabledLibraries = libraries.filterNot { hiddenKeys.contains(it.key) }
                        if (mobileMode) {
                            MobileContentBrowseScreen(
                                mode = selectedMode!!,
                                libraries = enabledLibraries,
                                recentlyMovies = recentlyMovies,
                                recentlyShows = recentlyShows,
                                continueWatching = continueWatching,
                                favoriteItems = favoriteItems,
                                favoriteKeys = favoriteKeys,
                                categoryItems = mediaItems,
                                artworkUrls = artworkUrls,
                                status = status,
                                loading = loading,
                                cachedAt = cachedAt,
                                loadingMore = libraryLoadingMore,
                                onOpenLibrary = { loadLibraryItems(it) },
                                onLoadMore = { loadNextCategoryPage() },
                                onPreloadLibrary = { preloadLibraryInBackground(it) },
                                onToggleFavorite = { toggleFavorite(it) },
                                onOpenSettings = { showSettings = true },
                                onOpenUpdate = { showUpdateScreen = true },
                                onSelectDetails = { openDetails(it) }
                            )
                        } else {
                            ContentBrowseScreen(
                                mode = selectedMode!!,
                                libraries = enabledLibraries,
                                recentlyMovies = recentlyMovies,
                                recentlyShows = recentlyShows,
                                continueWatching = continueWatching,
                                favoriteItems = favoriteItems,
                                favoriteKeys = favoriteKeys,
                                categoryItems = mediaItems,
                                artworkUrls = artworkUrls,
                                status = status,
                                loading = loading,
                                cachedAt = cachedAt,
                                loadingMore = libraryLoadingMore,
                                onOpenLibrary = { loadLibraryItems(it) },
                                onLoadMore = { loadNextCategoryPage() },
                                onPreloadLibrary = { preloadLibraryInBackground(it) },
                                onToggleFavorite = { toggleFavorite(it) },
                                onOpenSettings = { showSettings = true },
                                onOpenUpdate = { showUpdateScreen = true },
                                onSelectDetails = { openDetails(it) }
                            )
                        }
                    }

                    libraries.isNotEmpty() -> {
                        val enabledLibraries = libraries.filterNot { hiddenKeys.contains(it.key) }

                        if (mobileMode) {
                            MobileLobbyScreen(
                                serverName = serverName ?: "Plex Media Server",
                                friendlyName = friendlyName,
                                libraries = enabledLibraries,
                                allLibrariesHidden = libraries.isNotEmpty() && enabledLibraries.isEmpty(),
                                status = status,
                                loading = loading,
                                cachedAt = cachedAt,
                                onOpenVod = { selectedMode = ContentMode.Vod },
                                onOpenSeries = { selectedMode = ContentMode.Series },
                                onOpenFavorites = { openFavorites() },
                                onOpenSearch = { openGlobalSearch() },
                                onOpenSettings = { showSettings = true },
                                onOpenUpdate = { showUpdateScreen = true }
                            )
                        } else {
                            TvHomeScreen(
                                serverName = serverName ?: "Plex Media Server",
                                friendlyName = friendlyName,
                                libraries = enabledLibraries,
                                allLibrariesHidden = libraries.isNotEmpty() && enabledLibraries.isEmpty(),
                                recentlyMovies = recentlyMovies,
                                recentlyShows = recentlyShows,
                                continueWatching = continueWatching,
                            favoriteItems = favoriteItems,
                            favoriteKeys = favoriteKeys,
                            artworkUrls = artworkUrls,
                            backdropUrls = backdropUrls,
                                status = status,
                                loading = loading,
                                cachedAt = cachedAt,
                                onOpenVod = { selectedMode = ContentMode.Vod },
                                onOpenSeries = { selectedMode = ContentMode.Series },
                                onOpenFavorites = { openFavorites() },
                                onOpenSearch = { openGlobalSearch() },
                                onOpenSettings = { showSettings = true },
                                onOpenUpdate = { showUpdateScreen = true },
                                onSelectDetails = { openDetails(it) },
                                onToggleFavorite = { toggleFavorite(it) }
                            )
                        }
                    }

                    else -> {
                        LinkScreen(
                            pin = pin,
                            status = status,
                            linked = linked,
                            loading = loading,
                            onGenerate = { startLink() },
                            onCheckLink = { checkCurrentPinNow() },
                            onLoadHome = { loadHome() }
                        )
                    }
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("Exit FirePlex?") },
                        text = { Text("Press Exit to close the app, or Cancel to stay here.") },
                        confirmButton = {
                            FocusActionButton("EXIT", Modifier.width(120.dp), Color(0xFF8E0031)) {
                                showExitDialog = false
                                activity?.finish()
                            }
                        },
                        dismissButton = {
                            FocusActionButton("CANCEL", Modifier.width(120.dp), Color(0xFF203040)) { showExitDialog = false }
                        }
                    )
                }

                favoriteToast?.let { message ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                            .background(Color(0xEE111820), RoundedCornerShape(18.dp))
                            .padding(horizontal = 28.dp, vertical = 14.dp)
                    ) {
                        Text(message, color = Color(0xFFFFD54F), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

}

