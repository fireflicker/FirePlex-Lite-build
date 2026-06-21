package com.fireflicker.fireplex2

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.fireflicker.fireplex2.data.PlexLibrary
import com.fireflicker.fireplex2.data.PlexMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class OfflineDownloadItem(
    val id: String,
    val title: String,
    val type: String,
    val year: String,
    val summary: String,
    val path: String,
    val sizeBytes: Long,
    val downloadedAt: Long
)

class OfflineDownloadRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val downloadsDir: File
        get() = File(context.filesDir, "offline_downloads").apply { mkdirs() }

    private val indexFile: File
        get() = File(downloadsDir, "downloads.json")

    suspend fun list(): List<OfflineDownloadItem> = withContext(Dispatchers.IO) {
        val array = runCatching { JSONArray(indexFile.readText()) }.getOrDefault(JSONArray())
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val item = OfflineDownloadItem(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    type = obj.optString("type"),
                    year = obj.optString("year"),
                    summary = obj.optString("summary"),
                    path = obj.optString("path"),
                    sizeBytes = obj.optLong("sizeBytes"),
                    downloadedAt = obj.optLong("downloadedAt")
                )
                if (item.id.isNotBlank() && File(item.path).exists()) add(item)
            }
        }.sortedByDescending { it.downloadedAt }
    }

    suspend fun repairIndex(): List<OfflineDownloadItem> = withContext(Dispatchers.IO) {
        val indexed = list()
        val indexedPaths = indexed.map { it.path }.toSet()
        val recovered = downloadsDir
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name != indexFile.name &&
                    file.length() > 0L &&
                    !indexedPaths.contains(file.absolutePath)
            }
            .map { file ->
                val cleanTitle = file.nameWithoutExtension
                    .replace(Regex("-[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$"), "")
                    .replace('_', ' ')
                    .trim()
                    .ifBlank { "Recovered download" }
                OfflineDownloadItem(
                    id = "recovered-${file.name}-${file.length()}",
                    title = cleanTitle,
                    type = "video",
                    year = "",
                    summary = "Recovered from FirePlex offline storage.",
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    downloadedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            }

        val repaired = (indexed + recovered).distinctBy { it.path }.sortedByDescending { it.downloadedAt }
        save(repaired)
        repaired
    }

    suspend fun delete(item: OfflineDownloadItem) = withContext(Dispatchers.IO) {
        runCatching { File(item.path).delete() }
        save(list().filterNot { it.id == item.id })
    }

    suspend fun clearMissing() = withContext(Dispatchers.IO) {
        save(list())
    }

    suspend fun storageSummary(): String = withContext(Dispatchers.IO) {
        val used = list().sumOf { it.sizeBytes }
        val free = downloadsDir.usableSpace
        "Downloads ${formatBytes(used)} used - ${formatBytes(free)} free"
    }

    suspend fun download(
        item: PlexMediaItem,
        streamUrl: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): OfflineDownloadItem = withContext(Dispatchers.IO) {
        val safeTitle = item.title.ifBlank { "video" }
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .take(80)
            .trim()
            .ifBlank { "video" }
        val extension = streamUrl.substringBefore('?').substringAfterLast('.', "mp4").take(5).ifBlank { "mp4" }
        val id = UUID.randomUUID().toString()
        val output = File(downloadsDir, "$safeTitle-$id.$extension")

        try {
            val request = Request.Builder()
                .url(streamUrl)
                .get()
                .header("User-Agent", "FirePlex Download Manager")
                .header("X-Plex-Product", "FirePlex")
                .header("X-Plex-Platform", "Android TV")
                .header("X-Plex-Device", "Android TV")
                .header("X-Plex-Device-Name", "FirePlex Download")
                .header("X-Plex-Client-Identifier", "fireplex-download-manager")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                val body = response.body ?: error("Download had no body.")
                output.outputStream().use { fileOut ->
                    body.byteStream().use { input ->
                        val total = body.contentLength().coerceAtLeast(0L)
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var lastPercent = -1

                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            fileOut.write(buffer, 0, read)
                            downloaded += read

                            val percent = if (total > 0L) {
                                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }

                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, downloaded, total)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            runCatching { output.delete() }
            throw e
        }

        if (!output.exists() || output.length() <= 0L) {
            runCatching { output.delete() }
            error("Download failed: file was empty.")
        }

        val downloaded = OfflineDownloadItem(
            id = id,
            title = item.title.ifBlank { "Untitled" },
            type = item.type.ifBlank { "video" },
            year = item.year,
            summary = item.summary,
            path = output.absolutePath,
            sizeBytes = output.length(),
            downloadedAt = System.currentTimeMillis()
        )
        save((list() + downloaded).distinctBy { it.id })
        onProgress(100, downloaded.sizeBytes, downloaded.sizeBytes)
        downloaded
    }

    fun playUri(item: OfflineDownloadItem): String {
        val file = File(item.path)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        ).toString()
    }

    private fun save(items: List<OfflineDownloadItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("type", item.type)
                    .put("year", item.year)
                    .put("summary", item.summary)
                    .put("path", item.path)
                    .put("sizeBytes", item.sizeBytes)
                    .put("downloadedAt", item.downloadedAt)
            )
        }
        indexFile.writeText(array.toString())
    }
}

@Composable
fun AdminDownloadsScreen(
    libraries: List<PlexLibrary>,
    hiddenKeys: Set<String>,
    artworkUrls: Map<String, String>,
    unlocked: Boolean,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    onSearch: suspend (String, List<PlexLibrary>) -> List<PlexMediaItem>,
    onStreamUrl: suspend (PlexMediaItem) -> String,
    onPlayOffline: (OfflineDownloadItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads = remember(context) { OfflineDownloadRepository(context) }

    var pin by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter admin PIN to manage offline downloads.") }
    var results by remember { mutableStateOf<List<PlexMediaItem>>(emptyList()) }
    var offlineItems by remember { mutableStateOf<List<OfflineDownloadItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var storageText by remember { mutableStateOf("Checking storage...") }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadTitle by remember { mutableStateOf("") }
    var downloadPercent by remember { mutableStateOf(0) }
    var downloadBytes by remember { mutableStateOf("") }

    BackHandler { onBack() }

    LaunchedEffect(unlocked) {
        if (unlocked) {
            offlineItems = downloads.repairIndex()
            storageText = downloads.storageSummary()
            status = "Admin downloads unlocked. Offline files are stored inside FirePlex app storage."
        }
    }

    LaunchedEffect(query, unlocked) {
        if (!unlocked) return@LaunchedEffect
        val clean = query.trim()
        results = emptyList()
        if (clean.length < 2) return@LaunchedEffect
        delay(350)
        busy = true
        status = "Searching downloadable movies and TV episodes..."
        try {
            results = onSearch(clean, libraries.filterNot { hiddenKeys.contains(it.key) })
            status = if (results.isEmpty()) "No downloadable files found." else "Found ${results.size} file(s)."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            status = e.message ?: "Search failed."
        } finally {
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ADMIN", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp)
        Text(status, color = Color(0xFF00E676), fontSize = 13.sp)

        if (!unlocked) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xE8111820)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Admin PIN", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = pin, onValueChange = { pin = it.take(6) }, label = { Text("PIN") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        FocusActionButton("UNLOCK", Modifier.weight(1f), Color(0xFF203040)) {
                            if (pin == "726965") {
                                onUnlocked()
                                status = "Admin downloads unlocked."
                            } else {
                                status = "Wrong PIN."
                            }
                        }
                        FocusActionButton("BACK", Modifier.weight(1f), Color(0xFF203040), onBack)
                    }
                }
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search movies or TV episodes") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FocusActionButton("BACK", Modifier.width(130.dp), Color(0xFF203040), onBack)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(storageText, color = Color(0xFFB7C7D8), fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (downloadJob?.isActive == true) {
                Text(
                    "$downloadTitle - $downloadPercent% $downloadBytes",
                    color = Color(0xFFFFD54F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FocusActionButton("CANCEL DOWNLOAD", Modifier.width(190.dp), Color(0xFF7D1731)) {
                    downloadJob?.cancel()
                    busy = false
                    status = "Download cancelled."
                }
            } else {
                FocusActionButton("SCAN STORAGE", Modifier.width(160.dp), Color(0xFF203040)) {
                    scope.launch {
                        offlineItems = downloads.repairIndex()
                        storageText = downloads.storageSummary()
                        status = "Offline storage scanned. Found ${offlineItems.size} download(s)."
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            DownloadColumn(
                title = "Server Results",
                modifier = Modifier.weight(1f),
                empty = if (busy) "Searching..." else "Search for a movie or TV episode.",
                content = {
                    items(results, key = { it.ratingKey.ifBlank { it.key } }) { item ->
                        DownloadSearchRow(
                            item = item,
                            artworkUrl = artworkUrls[item.ratingKey].orEmpty()
                        ) {
                            if (downloadJob?.isActive == true) {
                                status = "Wait for the current download to finish or cancel it first."
                            } else {
                                downloadJob = scope.launch {
                                    busy = true
                                    downloadTitle = item.title.ifBlank { "Download" }
                                    downloadPercent = 0
                                    downloadBytes = ""
                                    status = "Starting download: ${item.title}"
                                    try {
                                        val stream = onStreamUrl(item)
                                        val downloaded = downloads.download(item, stream) { percent, done, total ->
                                            downloadPercent = percent
                                            downloadBytes = if (total > 0L) {
                                                "${formatBytes(done)} / ${formatBytes(total)}"
                                            } else {
                                                formatBytes(done)
                                            }
                                        }
                                        offlineItems = downloads.repairIndex()
                                        storageText = downloads.storageSummary()
                                        status = "Downloaded ${downloaded.title}."
                                    } catch (e: CancellationException) {
                                        status = "Download cancelled."
                                    } catch (e: Throwable) {
                                        status = e.message ?: "Download failed."
                                    } finally {
                                        busy = false
                                        downloadTitle = ""
                                        downloadPercent = 0
                                        downloadBytes = ""
                                    }
                                }
                            }
                        }
                    }
                }
            )

            DownloadColumn(
                title = "Offline Downloads",
                modifier = Modifier.weight(1f),
                empty = "No offline downloads yet.",
                content = {
                    items(offlineItems, key = { it.id }) { item ->
                        OfflineDownloadRow(
                            item = item,
                            onPlay = { onPlayOffline(item) },
                            onDelete = {
                                scope.launch {
                                    downloads.delete(item)
                                    offlineItems = downloads.list()
                                    storageText = downloads.storageSummary()
                                    status = "Deleted ${item.title}."
                                }
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun DownloadColumn(
    title: String,
    modifier: Modifier,
    empty: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0x99111820), RoundedCornerShape(12.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
            item {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}

@Composable
private fun DownloadSearchRow(item: PlexMediaItem, artworkUrl: String, onDownload: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onDownload)
            .clickable(onClick = onDownload),
        color = if (focused) Color(0xFF17331F) else Color(0xDD111820),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF00E676) else Color(0x44566678))
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(width = 72.dp, height = 52.dp).background(Color(0xFF1A2028), RoundedCornerShape(8.dp))) {
                if (artworkUrl.isNotBlank()) AsyncImage(model = cachedImageModel(artworkUrl), contentDescription = item.title, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mediaMetaLine(item), color = Color(0xFF00E676), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("DOWNLOAD", color = if (focused) Color(0xFF00E676) else Color(0xFFB7C7D8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OfflineDownloadRow(item: OfflineDownloadItem, onPlay: () -> Unit, onDelete: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onPlay, onLongClick = onDelete)
            .clickable(onClick = onPlay),
        color = if (focused) Color(0xFF17331F) else Color(0xDD111820),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF00E676) else Color(0x44566678))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.type} ${item.year} - ${formatBytes(item.sizeBytes)} - OK play, Menu delete", color = Color(0xFFB7C7D8), fontSize = 11.sp)
            }
            FocusActionButton("DELETE", Modifier.width(100.dp), Color(0xFF203040), onDelete)
        }
    }
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
