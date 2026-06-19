package com.fireflicker.fireplex2.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val Context.dataStore by preferencesDataStore("fireplex2_clean")

class PlexRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaCache = FirePlexDatabase.get(context).cachedMediaDao()

    private val clientIdKey = stringPreferencesKey("client_id")
    private val tokenKey = stringPreferencesKey("plex_token")
    private val serverBaseKey = stringPreferencesKey("server_base")
    private val hiddenLibrariesKey = stringPreferencesKey("hidden_libraries")
    private val friendlyDeviceNameKey = stringPreferencesKey("friendly_device_name")
    private val appUsernameKey = stringPreferencesKey("app_username")
    private val preferredPlayerKey = stringPreferencesKey("preferred_player")
    private val streamModeKey = stringPreferencesKey("stream_mode")
    private val appDisplayModeKey = stringPreferencesKey("app_display_mode")
    private val deviceProfileKey = stringPreferencesKey("device_profile")
    private val exoPlayerSettingsKey = stringPreferencesKey("exo_player_settings")
    private val favoriteItemsKey = stringPreferencesKey("favorite_items")
    private val cachedLibrariesKey = stringPreferencesKey("cached_libraries")
    private val cachedRecentMoviesKey = stringPreferencesKey("cached_recent_movies")
    private val cachedRecentShowsKey = stringPreferencesKey("cached_recent_shows")
    private val cachedUpdatedAtKey = longPreferencesKey("cached_updated_at")

    suspend fun savedToken(): String? = context.dataStore.data.first()[tokenKey]

    suspend fun savedServerBase(): String? {
        return context.dataStore.data.first()[serverBaseKey]?.trimEnd('/')?.takeIf { it.isNotBlank() }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[tokenKey] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(serverBaseKey)
            it.remove(hiddenLibrariesKey)
            it.remove(appUsernameKey)
            it.remove(cachedLibrariesKey)
            it.remove(cachedRecentMoviesKey)
            it.remove(cachedRecentShowsKey)
            it.remove(cachedUpdatedAtKey)
        }
        mediaCache.clear()
    }

    suspend fun cachedUpdatedAt(): Long {
        return context.dataStore.data.first()[cachedUpdatedAtKey] ?: 0L
    }

    suspend fun cachedLibraries(): List<PlexLibrary> {
        val raw = context.dataStore.data.first()[cachedLibrariesKey].orEmpty()
        return decodeLibraries(raw)
    }

    suspend fun cachedRecentlyAddedMovies(): List<PlexMediaItem> {
        val raw = context.dataStore.data.first()[cachedRecentMoviesKey].orEmpty()
        return decodeMediaItems(raw)
    }

    suspend fun cachedRecentlyAddedShows(): List<PlexMediaItem> {
        val raw = context.dataStore.data.first()[cachedRecentShowsKey].orEmpty()
        return decodeMediaItems(raw)
    }

    suspend fun cachedLibraryItems(libraryKey: String): List<PlexMediaItem> {
        val raw = context.dataStore.data.first()[cachedLibraryItemsKey(libraryKey)].orEmpty()
        return decodeMediaItems(raw)
    }

    suspend fun saveHomeCache(
        libraries: List<PlexLibrary>,
        recentMovies: List<PlexMediaItem>,
        recentShows: List<PlexMediaItem>
    ) {
        context.dataStore.edit { prefs ->
            prefs[cachedLibrariesKey] = encodeLibraries(libraries)
            prefs[cachedRecentMoviesKey] = encodeMediaItems(recentMovies)
            prefs[cachedRecentShowsKey] = encodeMediaItems(recentShows)
            prefs[cachedUpdatedAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun saveLibraryCache(libraryKey: String, items: List<PlexMediaItem>) {
        if (libraryKey.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[cachedLibraryItemsKey(libraryKey)] = encodeMediaItems(items)
            prefs[cachedUpdatedAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun clearContentCache() {
        val prefs = context.dataStore.data.first()
        val cachedLibraryKeys = prefs.asMap().keys.filter { it.name.startsWith("cached_library_items_") }
        context.dataStore.edit { edit ->
            edit.remove(cachedLibrariesKey)
            edit.remove(cachedRecentMoviesKey)
            edit.remove(cachedRecentShowsKey)
            edit.remove(cachedUpdatedAtKey)
            cachedLibraryKeys.forEach { key ->
                @Suppress("UNCHECKED_CAST")
                edit.remove(key as Preferences.Key<String>)
            }
        }
        mediaCache.clear()
    }

    suspend fun cachedCategoryPage(categoryKey: String, offset: Int = 0, limit: Int = 80): List<PlexMediaItem> {
        if (categoryKey.isBlank()) return emptyList()
        return mediaCache.page(categoryKey, offset.coerceAtLeast(0), limit.coerceIn(1, 200)).map { it.toMediaItem() }
    }

    suspend fun replaceCachedCategory(categoryKey: String, items: List<PlexMediaItem>) {
        if (categoryKey.isBlank()) return
        val entities = items.mapIndexed { index, item -> CachedMediaEntity.from(categoryKey, index, item) }
        mediaCache.replaceFirstPage(categoryKey, entities)
    }

    suspend fun appendCachedCategory(categoryKey: String, startPosition: Int, items: List<PlexMediaItem>) {
        if (categoryKey.isBlank() || items.isEmpty()) return
        val entities = items.mapIndexed { index, item -> CachedMediaEntity.from(categoryKey, startPosition + index, item) }
        mediaCache.insert(entities)
    }

    suspend fun clearLibraryCaches() {
        val prefs = context.dataStore.data.first()
        val cachedLibraryKeys = prefs.asMap().keys.filter { it.name.startsWith("cached_library_items_") }
        if (cachedLibraryKeys.isEmpty()) return

        context.dataStore.edit { edit ->
            cachedLibraryKeys.forEach { key ->
                @Suppress("UNCHECKED_CAST")
                edit.remove(key as Preferences.Key<String>)
            }
        }
    }

    suspend fun friendlyDeviceName(): String {
        return context.dataStore.data.first()[friendlyDeviceNameKey]
            ?.takeIf { it.isNotBlank() }
            ?: "FirePlex3.0"
    }

    suspend fun saveFriendlyDeviceName(name: String) {
        context.dataStore.edit {
            it[friendlyDeviceNameKey] = name.trim().ifBlank { "FirePlex3.0" }
        }
    }

    suspend fun savedAppUsername(): String? {
        return context.dataStore.data.first()[appUsernameKey]?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun saveAppUsername(username: String) {
        val clean = username.trim()
        context.dataStore.edit { prefs ->
            if (clean.isBlank()) {
                prefs.remove(appUsernameKey)
            } else {
                prefs[appUsernameKey] = clean
            }
        }
    }

    suspend fun clearAppUsername() {
        context.dataStore.edit { prefs -> prefs.remove(appUsernameKey) }
    }

    suspend fun preferredPlayer(): String {
        return "exo"
    }

    suspend fun savePreferredPlayer(player: String) {
        context.dataStore.edit {
            it[preferredPlayerKey] = "exo"
        }
    }

    suspend fun streamMode(): String {
        return context.dataStore.data.first()[streamModeKey]
            ?.takeIf { it == "auto" || it == "direct_play" || it == "direct_stream" || it == "transcode" }
            ?: "auto"
    }

    suspend fun saveStreamMode(mode: String) {
        val clean = mode.lowercase().takeIf { it == "auto" || it == "direct_play" || it == "direct_stream" || it == "transcode" } ?: "auto"
        context.dataStore.edit { prefs ->
            prefs[streamModeKey] = clean
        }
    }

    suspend fun appDisplayMode(): String? {
        return context.dataStore.data.first()[appDisplayModeKey]
            ?.lowercase()
            ?.takeIf { it == "tv" || it == "mobile" }
    }

    suspend fun saveAppDisplayMode(mode: String) {
        val clean = mode.lowercase().takeIf { it == "tv" || it == "mobile" } ?: "tv"
        context.dataStore.edit { prefs ->
            prefs[appDisplayModeKey] = clean
        }
    }

    suspend fun deviceProfile(): String {
        return context.dataStore.data.first()[deviceProfileKey].orEmpty().ifBlank { "auto" }
    }

    suspend fun saveDeviceProfile(profile: String) {
        context.dataStore.edit { prefs -> prefs[deviceProfileKey] = profile.ifBlank { "auto" } }
    }

    suspend fun exoPlayerSettings(): ExoPlayerSettings {
        val raw = context.dataStore.data.first()[exoPlayerSettingsKey]
        return if (raw.isNullOrBlank()) {
            ExoPlayerSettings()
        } else {
            runCatching { json.decodeFromString(ExoPlayerSettings.serializer(), raw) }
                .getOrDefault(ExoPlayerSettings())
        }
    }

    suspend fun saveExoPlayerSettings(settings: ExoPlayerSettings) {
        val encoded = json.encodeToString(ExoPlayerSettings.serializer(), settings)
        context.dataStore.edit { prefs ->
            prefs[exoPlayerSettingsKey] = encoded
        }
    }

    suspend fun hiddenLibraryKeys(): Set<String> {
        return context.dataStore.data.first()[hiddenLibrariesKey].orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun setLibraryEnabled(key: String, enabled: Boolean) {
        if (key.isBlank()) return

        val hidden = hiddenLibraryKeys().toMutableSet()
        if (enabled) hidden.remove(key) else hidden.add(key)

        context.dataStore.edit {
            it[hiddenLibrariesKey] = hidden.joinToString("|")
        }
    }


    suspend fun favoriteKeys(): Set<String> {
        return context.dataStore.data.first()[favoriteItemsKey].orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun setFavoriteItem(item: PlexMediaItem, favorite: Boolean): Boolean {
        val key = item.ratingKey.ifBlank { item.key }.trim()
        if (key.isBlank()) return false

        val current = favoriteKeys().toMutableSet()
        if (favorite) current.add(key) else current.remove(key)
        context.dataStore.edit { prefs ->
            prefs[favoriteItemsKey] = current.joinToString("|")
        }
        return current.contains(key)
    }

    suspend fun toggleFavorite(item: PlexMediaItem): Boolean {
        val key = item.ratingKey.ifBlank { item.key }.trim()
        if (key.isBlank()) return false

        val current = favoriteKeys().toMutableSet()
        val nowFavorite = if (current.contains(key)) {
            current.remove(key)
            false
        } else {
            current.add(key)
            true
        }
        context.dataStore.edit { prefs ->
            prefs[favoriteItemsKey] = current.joinToString("|")
        }
        return nowFavorite
    }

    private suspend fun clientId(): String {
        val prefs = context.dataStore.data.first()
        prefs[clientIdKey]?.let { return it }

        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[clientIdKey] = id }
        return id
    }

    suspend fun createPin(): PlexPin = withContext(Dispatchers.IO) {
        json.decodeFromString<PlexPin>(
            request(url = "https://plex.tv/api/v2/pins", method = "POST")
        )
    }

    suspend fun checkPin(id: Int): PlexPin = withContext(Dispatchers.IO) {
        json.decodeFromString<PlexPin>(
            request("https://plex.tv/api/v2/pins/$id")
        )
    }

    suspend fun servers(): List<PlexServer> = withContext(Dispatchers.IO) {
        val token = savedToken() ?: error("Not linked")

        val builder = Request.Builder()
            .url("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1")
            .header("Accept", "application/json")
            .header("X-Plex-Token", token)
            .plexHeaders()

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                error("Failed loading servers: Plex HTTP ${response.code}: ${response.message}\n$body")
            }

            json.decodeFromString(
                ListSerializer(PlexServer.serializer()),
                body
            ).filter { it.isServer() }
        }
    }

    suspend fun autoSelectServer(): PlexServer {
        val server = servers().firstOrNull()
            ?: error("No Plex Media Server found")

        val connection = chooseBestConnection(server.connections)
            ?: error("No Plex server connection found")

        context.dataStore.edit {
            it[serverBaseKey] = connection.uri.trimEnd('/')
        }

        return server
    }

    private fun chooseBestConnection(connections: List<PlexConnection>): PlexConnection? {
        return connections
            .filter { it.uri.isNotBlank() }
            .sortedWith(
                compareBy<PlexConnection> { connectionRank(it) }
                    .thenBy { it.relay }
            )
            .firstOrNull()
    }

    private fun connectionRank(connection: PlexConnection): Int {
        val privateUri = isPrivateUri(connection.uri)
        val https = connection.uri.startsWith("https://", ignoreCase = true)

        return when {
            https && !connection.local && !privateUri && !connection.relay -> 0
            !connection.local && !privateUri && !connection.relay -> 1
            https && !privateUri -> 2
            !privateUri -> 3
            connection.relay -> 4
            else -> 9
        }
    }

    private fun uriHost(uri: String): String {
        return try {
            URI(uri).host.orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun isPrivateUri(uri: String): Boolean {
        val host = uriHost(uri).lowercase()
        val full = uri.lowercase()

        return isPrivateHost(host) || hasDashedPrivateIp(host) || hasDashedPrivateIp(full)
    }

    private fun isPrivateHost(host: String): Boolean {
        val clean = host.lowercase()

        return clean == "localhost" ||
            clean.startsWith("127.") ||
            clean.startsWith("10.") ||
            clean.startsWith("192.168.") ||
            clean.startsWith("172.16.") ||
            clean.startsWith("172.17.") ||
            clean.startsWith("172.18.") ||
            clean.startsWith("172.19.") ||
            clean.startsWith("172.20.") ||
            clean.startsWith("172.21.") ||
            clean.startsWith("172.22.") ||
            clean.startsWith("172.23.") ||
            clean.startsWith("172.24.") ||
            clean.startsWith("172.25.") ||
            clean.startsWith("172.26.") ||
            clean.startsWith("172.27.") ||
            clean.startsWith("172.28.") ||
            clean.startsWith("172.29.") ||
            clean.startsWith("172.30.") ||
            clean.startsWith("172.31.")
    }

    private fun hasDashedPrivateIp(value: String): Boolean {
        return value.contains("192-168-") ||
            value.contains("127-0-0-") ||
            value.contains("10-") ||
            value.contains("172-16-") ||
            value.contains("172-17-") ||
            value.contains("172-18-") ||
            value.contains("172-19-") ||
            value.contains("172-20-") ||
            value.contains("172-21-") ||
            value.contains("172-22-") ||
            value.contains("172-23-") ||
            value.contains("172-24-") ||
            value.contains("172-25-") ||
            value.contains("172-26-") ||
            value.contains("172-27-") ||
            value.contains("172-28-") ||
            value.contains("172-29-") ||
            value.contains("172-30-") ||
            value.contains("172-31-")
    }

    suspend fun libraries(): List<PlexLibrary> = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")

        PlexXmlParser.libraries(
            serverRequest("${base.trimEnd('/')}/library/sections", token)
        )
    }

    suspend fun libraryItems(library: PlexLibrary): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        libraryItemsInternal(library, start = null, size = null)
    }

    suspend fun libraryItemsPage(library: PlexLibrary, start: Int = 0, size: Int = 60): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        libraryItemsInternal(library, start = start.coerceAtLeast(0), size = size.coerceIn(1, 250))
    }

    private suspend fun libraryItemsInternal(library: PlexLibrary, start: Int?, size: Int?): List<PlexMediaItem> {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")
        val baseUrl = if (library.type.equals("show", ignoreCase = true) || library.type.equals("tv", ignoreCase = true)) {
            "${base.trimEnd('/')}/library/sections/${library.key}/all?type=2&sort=titleSort"
        } else {
            "${base.trimEnd('/')}/library/sections/${library.key}/all?sort=titleSort"
        }
        val pagedUrl = if (start != null && size != null) {
            "$baseUrl&X-Plex-Container-Start=$start&X-Plex-Container-Size=$size"
        } else {
            baseUrl
        }

        return PlexXmlParser.parseMediaItems(serverRequest(pagedUrl, token))
    }

    suspend fun mediaDetails(item: PlexMediaItem): PlexMediaItem = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")
        val ratingKey = item.ratingKey.ifBlank { error("No media rating key found.") }

        val body = serverRequest("${base.trimEnd('/')}/library/metadata/$ratingKey", token)
        PlexXmlParser.parseMediaItems(body).firstOrNull() ?: item
    }

    suspend fun tvSeasons(show: PlexMediaItem): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")
        val ratingKey = when {
            show.type.equals("episode", ignoreCase = true) && show.grandparentRatingKey.isNotBlank() -> show.grandparentRatingKey
            show.type.equals("season", ignoreCase = true) && show.parentRatingKey.isNotBlank() -> show.parentRatingKey
            show.grandparentRatingKey.isNotBlank() && !show.type.equals("show", ignoreCase = true) -> show.grandparentRatingKey
            show.parentRatingKey.isNotBlank() && show.type.equals("season", ignoreCase = true) -> show.parentRatingKey
            else -> show.ratingKey
        }.ifBlank { error("No TV series rating key found.") }

        PlexXmlParser.parseMediaItems(
            serverRequest("${base.trimEnd('/')}/library/metadata/$ratingKey/children", token)
        )
    }

    suspend fun seasonEpisodes(season: PlexMediaItem): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")
        val ratingKey = when {
            season.type.equals("episode", ignoreCase = true) && season.parentRatingKey.isNotBlank() -> season.parentRatingKey
            else -> season.ratingKey
        }.ifBlank { error("No season rating key found.") }

        PlexXmlParser.parseMediaItems(
            serverRequest("${base.trimEnd('/')}/library/metadata/$ratingKey/children", token)
        ).sortedWith(compareBy<PlexMediaItem> { it.addedAt }.thenBy { it.title.lowercase() })
    }

    suspend fun recentlyAdded(library: PlexLibrary): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")

        PlexXmlParser.parseMediaItems(
            serverRequest("${base.trimEnd('/')}/library/sections/${library.key}/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=20", token)
        ).sortedByNewest()
    }

    suspend fun recentlyAddedMovies(libraries: List<PlexLibrary>): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        val movies = libraries.filter { it.type.equals("movie", ignoreCase = true) }
        if (movies.isEmpty()) return@withContext emptyList()
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")
        val globalRecent = runCatching {
            PlexXmlParser.parseMediaItems(
                serverRequest("${base.trimEnd('/')}/library/recentlyAdded?type=1&X-Plex-Container-Start=0&X-Plex-Container-Size=30", token)
            )
        }.getOrDefault(emptyList())

        (if (globalRecent.isNotEmpty()) globalRecent else movies.flatMap { recentlyAdded(it) })
            .distinctBy { it.ratingKey }
            .sortedByNewest()
            .take(30)
    }

    suspend fun recentlyAddedShows(libraries: List<PlexLibrary>): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        val shows = libraries.filter { it.type.equals("show", ignoreCase = true) || it.type.equals("tv", ignoreCase = true) }
        if (shows.isEmpty()) return@withContext emptyList()

        val base = serverBase()
        val token = savedToken() ?: error("Not linked")

        val globalRecent = runCatching {
            PlexXmlParser.parseMediaItems(
                serverRequest("${base.trimEnd('/')}/library/recentlyAdded?type=4&X-Plex-Container-Start=0&X-Plex-Container-Size=60", token)
            )
        }.getOrDefault(emptyList())

        val recentItems = (if (globalRecent.isNotEmpty()) {
            globalRecent
        } else {
            shows.flatMap { library ->
                val recentUrl = "${base.trimEnd('/')}/library/sections/${library.key}/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=12"
                runCatching { PlexXmlParser.parseMediaItems(serverRequest(recentUrl, token)) }.getOrDefault(emptyList())
            }
        }).sortedByNewest()

        recentItems.mapNotNull { item ->
            when {
                item.type.equals("show", ignoreCase = true) -> item
                item.grandparentRatingKey.isNotBlank() -> item.copy(
                    ratingKey = item.grandparentRatingKey,
                    parentRatingKey = "",
                    grandparentRatingKey = "",
                    key = "/library/metadata/${item.grandparentRatingKey}",
                    title = item.seriesTitle.ifBlank { item.title },
                    type = "show",
                    summary = "New episode available. Open the series to choose the season and episode.",
                    durationMs = 0L,
                    viewOffsetMs = 0L,
                    partKey = "",
                    subtitles = emptyList()
                )
                else -> null
            }
        }
            .distinctBy { it.ratingKey.ifBlank { it.title.lowercase() } }
            .sortedByNewest()
            .take(30)
    }

    suspend fun continueWatching(): List<PlexMediaItem> = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")

        val onDeck = runCatching {
            PlexXmlParser.parseMediaItems(serverRequest("${base.trimEnd('/')}/library/onDeck", token))
        }.getOrDefault(emptyList())

        onDeck
            .filter { it.ratingKey.isNotBlank() }
            .distinctBy { it.ratingKey }
            .sortedWith(compareByDescending<PlexMediaItem> { it.viewOffsetMs }.thenByDescending { it.addedAt })
            .take(50)
    }

    private fun List<PlexMediaItem>.sortedByNewest(): List<PlexMediaItem> {
        return sortedWith(compareByDescending<PlexMediaItem> { it.addedAt }.thenBy { it.title.lowercase() })
    }

    suspend fun streamUrl(item: PlexMediaItem, mode: String = "direct_play"): String = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")

        if (mode == "direct_play" && item.partKey.isBlank()) {
            error("This item has no direct playable video file.")
        }

        when (mode) {
            "transcode" -> plexTranscodeUrl(base, token, item, directStream = false)
            "direct_stream" -> plexTranscodeUrl(base, token, item, directStream = true)
            "auto" -> {
                // Auto mode is tuned for Google TV / Fire TV.
                // Use Plex universal HLS with direct stream first instead of raw file direct play.
                // This keeps video mostly direct while allowing Plex/Exo to avoid unsupported containers/audio.
                plexTranscodeUrl(base, token, item, directStream = true)
            }
            else -> "${base.trimEnd('/')}${item.partKey}?X-Plex-Token=${encode(token)}"
        }
    }

    private suspend fun plexTranscodeUrl(base: String, token: String, item: PlexMediaItem, directStream: Boolean): String {
        val metadataPath = item.key.takeIf { it.isNotBlank() } ?: "/library/metadata/${item.ratingKey}"
        val clientIdentifier = clientId()
        val sessionIdentifier = UUID.randomUUID().toString()

        // Google TV / Fire Stick safe playback:
        // - Always use HLS for ExoPlayer.
        // - Auto/direct_stream keeps video as direct as possible but asks Plex for AAC stereo audio.
        // - Full transcode forces H264 + AAC when direct stream still fails.
        // This avoids common no-sound/player-closes issues caused by DTS, TrueHD, DTS-HD, or Atmos.
        val directPlay = "0"
        val directStreamValue = if (directStream) "1" else "0"

        return buildString {
            append(base.trimEnd('/'))
            append("/video/:/transcode/universal/start.m3u8")
            append("?path=${encode(metadataPath)}")
            append("&mediaIndex=0")
            append("&partIndex=0")
            append("&protocol=hls")
            append("&container=mpegts")
            append("&fastSeek=1")
            append("&directPlay=$directPlay")
            append("&directStream=$directStreamValue")
            append("&audioCodec=aac")
            append("&maxAudioChannels=2")
            if (!directStream) {
                append("&videoCodec=h264")
            }
            append("&audioBoost=100")
            append("&videoQuality=80")
            append("&maxVideoBitrate=12000")
            append("&X-Plex-Product=${encode("FirePlex")}")
            append("&X-Plex-Version=${encode("1.4")}")
            append("&X-Plex-Platform=${encode("Android")}")
            append("&X-Plex-Device=${encode("Android TV")}")
            append("&X-Plex-Client-Identifier=${encode(clientIdentifier)}")
            append("&X-Plex-Session-Identifier=${encode(sessionIdentifier)}")
            append("&X-Plex-Token=${encode(token)}")
        }
    }

    suspend fun imageUrl(path: String, width: Int = 300, height: Int = 450): String = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")

        if (path.isBlank()) {
            ""
        } else {
            // IMPORTANT: use the original Plex image path directly.
            // Do NOT use /photo/:/transcode here because that creates PhotoTranscoder cache writes
            // on the Plex server and can cause "protocol is shutdown" SSL errors in Plex logs.
            val rawUrl = if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
                path
            } else {
                "${base.trimEnd('/')}${if (path.startsWith("/")) path else "/$path"}"
            }

            val joiner = if (rawUrl.contains("?")) "&" else "?"
            "$rawUrl${joiner}X-Plex-Token=${encode(token)}"
        }
    }

    suspend fun subtitleUrl(track: PlexSubtitleTrack): String = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")

        if (track.key.isBlank()) "" else "${base.trimEnd('/')}${track.key}?X-Plex-Token=$token"
    }

    suspend fun speedTest(): String = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")
        val start = System.nanoTime()

        val body = serverRequest("${base.trimEnd('/')}/library/sections", token)
        val elapsedSeconds = ((System.nanoTime() - start).toDouble() / 1_000_000_000.0).coerceAtLeast(0.001)
        val kb = body.toByteArray().size / 1024.0
        val kbps = kb / elapsedSeconds

        "${kbps.roundToInt()} KB/s to Plex server"
    }

    suspend fun reportPlayback(
        item: PlexMediaItem,
        state: String,
        timeMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        val base = serverBase()
        val token = savedToken() ?: error("Not linked")
        if (item.ratingKey.isBlank()) return@withContext

        val safeState = when (state) {
            "playing", "paused", "stopped" -> state
            else -> "playing"
        }

        val url = buildString {
            append(base.trimEnd('/'))
            append("/:/timeline")
            append("?ratingKey=${encode(item.ratingKey)}")
            append("&key=${encode(item.key.ifBlank { "/library/metadata/${item.ratingKey}" })}")
            append("&state=${encode(safeState)}")
            append("&time=${timeMs.coerceAtLeast(0L)}")
            append("&duration=${durationMs.coerceAtLeast(0L)}")
            append("&type=${encode(item.type.ifBlank { "video" })}")
            append("&X-Plex-Token=${encode(token)}")
        }

        timelineRequest(url)
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun cachedLibraryItemsKey(libraryKey: String) = stringPreferencesKey("cached_library_items_$libraryKey")

    private fun encodeLibraries(libraries: List<PlexLibrary>): String {
        return libraries.joinToString("\n") { library ->
            listOf(library.key, library.title, library.type).joinToString("\t") { b64(it) }
        }
    }

    private fun decodeLibraries(raw: String): List<PlexLibrary> {
        if (raw.isBlank()) return emptyList()

        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 3) {
                    null
                } else {
                    PlexLibrary(
                        key = unb64(parts[0]),
                        title = unb64(parts[1]),
                        type = unb64(parts[2])
                    )
                }
            }
            .toList()
    }

    private fun encodeMediaItems(items: List<PlexMediaItem>): String {
        return items.joinToString("\n") { item ->
            listOf(
                item.ratingKey,
                item.parentRatingKey,
                item.grandparentRatingKey,
                item.key,
                item.title,
                item.type,
                item.summary,
                item.thumb,
                item.art,
                item.year,
                item.contentRating,
                item.durationMs.toString(),
                item.viewOffsetMs.toString(),
                item.addedAt.toString(),
                item.partKey,
                item.rating,
                item.audienceRating,
                item.tagline
            ).joinToString("\t") { b64(it) }
        }
    }

    private fun decodeMediaItems(raw: String): List<PlexMediaItem> {
        if (raw.isBlank()) return emptyList()

        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 13) {
                    null
                } else {
                    val hasParentKeys = parts.size >= 15
                    val offset = if (hasParentKeys) 2 else 0
                    PlexMediaItem(
                        ratingKey = unb64(parts[0]),
                        parentRatingKey = if (hasParentKeys) unb64(parts[1]) else "",
                        grandparentRatingKey = if (hasParentKeys) unb64(parts[2]) else "",
                        key = unb64(parts[1 + offset]),
                        title = unb64(parts[2 + offset]),
                        type = unb64(parts[3 + offset]),
                        summary = unb64(parts[4 + offset]),
                        thumb = unb64(parts[5 + offset]),
                        art = unb64(parts[6 + offset]),
                        year = unb64(parts[7 + offset]),
                        contentRating = unb64(parts[8 + offset]),
                        durationMs = unb64(parts[9 + offset]).toLongOrNull() ?: 0L,
                        viewOffsetMs = unb64(parts[10 + offset]).toLongOrNull() ?: 0L,
                        addedAt = unb64(parts[11 + offset]).toLongOrNull() ?: 0L,
                        partKey = unb64(parts[12 + offset]),
                        subtitles = emptyList(),
                        rating = parts.getOrNull(13 + offset)?.let { unb64(it) }.orEmpty(),
                        audienceRating = parts.getOrNull(14 + offset)?.let { unb64(it) }.orEmpty(),
                        tagline = parts.getOrNull(15 + offset)?.let { unb64(it) }.orEmpty()
                    )
                }
            }
            .toList()
    }

    private fun b64(value: String): String {
        return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun unb64(value: String): String {
        return runCatching {
            String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private suspend fun serverBase(): String {
        val savedBase = context.dataStore.data.first()[serverBaseKey]

        if (!savedBase.isNullOrBlank()) {
            return savedBase.trimEnd('/')
        }

        autoSelectServer()

        return context.dataStore.data.first()[serverBaseKey]
            ?.trimEnd('/')
            ?: error("No server selected")
    }

    private suspend fun serverRequest(url: String, token: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/xml")
            .header("X-Plex-Token", token)
            .plexHeaders()

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                error("Server HTTP ${response.code}: ${response.message}\n$body")
            }

            return body
        }
    }

    private suspend fun timelineRequest(url: String) {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/xml")
            .plexHeaders()

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                error("Timeline HTTP ${response.code}: ${response.message}\n$body")
            }
        }
    }

    private suspend fun request(url: String, method: String = "GET"): String {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .plexHeaders()

        if (method == "POST") {
            builder.post("{}".toRequestBody("application/json".toMediaType()))
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                error("Plex HTTP ${response.code}: ${response.message}\n$body")
            }

            return body
        }
    }

    private suspend fun Request.Builder.plexHeaders(): Request.Builder {
        return this
            .header("X-Plex-Product", "FirePlex")
            .header("X-Plex-Version", "1.4")
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Device", "Android TV")
            .header("X-Plex-Device-Name", friendlyDeviceName())
            .header("X-Plex-Client-Identifier", clientId())
    }
}
