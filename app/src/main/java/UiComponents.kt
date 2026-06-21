package com.fireflicker.fireplex2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fireflicker.fireplex2.data.PlexLibrary
import com.fireflicker.fireplex2.data.PlexMediaItem
@Composable
fun LobbyCircleTile(label: String, icon: String, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> Color(0xFF00E676)
        enabled -> Color(0x99FFFFFF)
        else -> Color(0x44FFFFFF)
    }
    val contentColor = if (enabled) Color.White else Color(0xFF6D7784)

    Surface(
        modifier = Modifier
            .size(if (focused) 178.dp else 164.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .tvRemoteClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(90.dp),
        color = Color(0x33111820),
        border = BorderStroke(if (focused) 4.dp else 2.dp, borderColor)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, color = contentColor, fontSize = 54.sp, fontWeight = FontWeight.Light)
            Text(label, color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
fun LobbySmallButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (focused) Color(0xFF00E676) else Color.Transparent,
        border = BorderStroke(1.dp, if (focused) Color(0xFF00E676) else Color.White)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = if (focused) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ContentSideRail(
    title: String,
    libraries: List<PlexLibrary>,
    onOpenLibrary: (PlexLibrary) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    Card(modifier = Modifier.width(250.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xE6111820)), shape = RoundedCornerShape(8.dp)) {
        LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            item { Text("Categories", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            item { MenuButton("Recently Added") { } }
            items(libraries, key = { it.key }) { library ->
                MenuButton(library.title.ifBlank { "Library" }) { onOpenLibrary(library) }
            }
            item { Spacer(Modifier.height(10.dp)) }
            item { MenuButton("Update Contents", onOpenUpdate) }
            item { MenuButton("Settings", onOpenSettings) }
        }
    }
}

@Composable
fun ModeChip(text: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (selected) Color(0xFF00E676) else Color(0x66111820),
        border = BorderStroke(1.dp, if (selected) Color(0xFF00E676) else Color(0x99FFFFFF))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun UpdateStatusTile(title: String, status: String, modifier: Modifier = Modifier) {
    val statusColor = when (status.lowercase()) {
        "completed" -> Color(0xFF4DFF9B)
        "updating..." -> Color(0xFF74F3F0)
        "failed" -> Color(0xFFFF6B6B)
        else -> Color.White
    }

    Card(modifier = modifier.height(126.dp), colors = CardDefaults.cardColors(containerColor = Color(0xC9111820)), shape = RoundedCornerShape(4.dp)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, color = Color(0xFFBFEFF2), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(status, color = statusColor, fontSize = 22.sp)
        }
    }
}

@Composable
fun LeftMenu(
    libraries: List<PlexLibrary>,
    onToggleMenu: () -> Unit,
    onOpenLibrary: (PlexLibrary) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.width(230.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xF2111820)), shape = RoundedCornerShape(18.dp)) {
        LazyColumn(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Menu", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            item { MenuButton("Hide Menu", onToggleMenu) }
            item { MenuButton("Refresh", onRefresh) }
            item { MenuButton("Settings", onOpenSettings) }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Categories", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            items(libraries, key = { it.key }) { library ->
                MenuButton(library.title.ifBlank { "Library" }) { onOpenLibrary(library) }
            }
        }
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Color(0x3329D3FF) else Color.Transparent
    val fg = if (focused) Color(0xFF00E676) else Color.White

    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick)
            .padding(10.dp),
        color = fg,
        fontSize = 15.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

fun playerLabel(choice: PlayerChoice): String {
    return when (choice) {
        PlayerChoice.Exo -> "EXO (built in)"
    }
}

fun streamModeLabel(mode: String): String {
    return when (mode) {
        "transcode" -> "Transcode"
        "direct_stream" -> "Direct Stream"
        else -> "Direct Play"
    }
}

@Composable
fun FocusActionButton(text: String, modifier: Modifier = Modifier, color: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val label = text.uppercase()
    val isCancelAction = listOf("CANCEL", "BACK", "CLOSE", "RESET", "CLEAR", "SIGN OUT", "REMOVE", "DELETE", "EXIT").any { label.contains(it) }
    val normalColor = color
    val focusedColor = if (isCancelAction) Color(0xFFFFB000) else Color(0xFF00FF66)

    Surface(
        modifier = modifier
            .height(if (focused) 56.dp else 52.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = if (focused) focusedColor else normalColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (focused) 4.dp else 1.dp, if (focused) Color.White else Color(0x55566678))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun EmptyPanel(text: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xF2111820)), shape = RoundedCornerShape(16.dp)) {
        Text(text = text, modifier = Modifier.padding(18.dp), color = Color(0xFFB7C7D8), textAlign = TextAlign.Center)
    }
}

@Composable
fun LibraryPosterCard(library: PlexLibrary, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(if (focused) 178.dp else 170.dp)
            .height(if (focused) 240.dp else 230.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF17222D) else Color(0xF2111820)),
        border = if (focused) BorderStroke(3.dp, Color(0xFF00E676)) else null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF4A3208), Color(0xFF111820), Color(0xFF050608)))).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(library.title.ifBlank { "Library" }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(library.type.ifBlank { "category" }, color = Color(0xFF00E676), fontSize = 12.sp)
            }
            Text(if (focused) "Press OK" else "Open", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
fun MediaWideRow(item: PlexMediaItem, artworkUrl: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        colors = CardDefaults.cardColors(containerColor = if (focused) Color(0xFF17222D) else Color(0xF2111820)),
        border = if (focused) BorderStroke(3.dp, Color(0xFF00E676)) else null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(if (focused) 18.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(width = 116.dp, height = 78.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A2028)), contentAlignment = Alignment.Center) {
                if (artworkUrl.isNotBlank()) {
                    AsyncImage(model = cachedImageModel(artworkUrl), contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(item.type.take(1).uppercase().ifBlank { "V" }, color = Color(0xFF00E676), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title.ifBlank { "Untitled" }, color = Color.White, fontSize = if (focused) 21.sp else 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(mediaMetaLine(item), color = Color(0xFF00E676), fontSize = 13.sp)

                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(item.summary, color = Color(0xFFB7C7D8), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (focused) {
                Spacer(Modifier.width(12.dp))
                Text("OK", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFF00E676), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}
fun mediaMetaLine(item: PlexMediaItem): String {
    return listOf(
        item.year,
        item.contentRating,
        item.type.ifBlank { "video" },
        durationLabel(item.durationMs)
    ).filter { it.isNotBlank() }.joinToString(" - ")
}

fun durationLabel(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val minutes = durationMs / 60000L
    val hours = minutes / 60L
    val mins = minutes % 60L
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

fun cacheLabel(cachedAt: Long): String {
    if (cachedAt <= 0L) return "No saved content yet"
    val ageMinutes = ((System.currentTimeMillis() - cachedAt).coerceAtLeast(0L) / 60000L).coerceAtLeast(0L)
    return when {
        ageMinutes < 1L -> "Updated just now"
        ageMinutes < 60L -> "Updated ${ageMinutes}m ago"
        ageMinutes < 1440L -> "Updated ${ageMinutes / 60L}h ago"
        else -> "Updated ${ageMinutes / 1440L}d ago"
    }
}



