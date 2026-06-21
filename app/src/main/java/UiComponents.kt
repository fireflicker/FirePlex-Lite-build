package com.fireflicker.fireplex2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
        focused -> FirePlexColors.Accent
        enabled -> Color(0x99FFFFFF)
        else -> Color(0x44FFFFFF)
    }
    val contentColor = if (enabled) FirePlexColors.Text else Color(0xFF6D7784)

    Surface(
        modifier = Modifier
            .size(if (focused) 168.dp else 156.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .tvRemoteClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(90.dp),
        color = Color(0x33111820),
        border = BorderStroke(if (focused) FirePlexDimens.FocusBorder else 2.dp, borderColor)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, color = contentColor, fontSize = 48.sp, fontWeight = FontWeight.Light)
            Text(label, color = contentColor, fontSize = 21.sp, fontWeight = FontWeight.Light)
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
        color = if (focused) FirePlexColors.Accent else Color.Transparent,
        border = BorderStroke(FirePlexDimens.ThinBorder, if (focused) FirePlexColors.Accent else Color.White)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = if (focused) Color.Black else FirePlexColors.Text,
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
    Card(modifier = Modifier.width(250.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = FirePlexColors.Panel), shape = RoundedCornerShape(FirePlexDimens.CardRadius)) {
        LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(title, color = FirePlexColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            item { Text("Categories", color = FirePlexColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
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
        shape = RoundedCornerShape(FirePlexDimens.ButtonRadius),
        color = if (selected) FirePlexColors.Accent else Color(0x66111820),
        border = BorderStroke(FirePlexDimens.ThinBorder, if (selected) FirePlexColors.Accent else Color(0x99FFFFFF))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            color = if (selected) Color.Black else FirePlexColors.Text,
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
        else -> FirePlexColors.Text
    }

    Card(modifier = modifier.height(110.dp), colors = CardDefaults.cardColors(containerColor = FirePlexColors.PanelSoft), shape = RoundedCornerShape(FirePlexDimens.CardRadius)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, color = Color(0xFFBFEFF2), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(status, color = statusColor, fontSize = 19.sp)
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
    Card(modifier = Modifier.width(230.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = FirePlexColors.Panel), shape = RoundedCornerShape(FirePlexDimens.PanelRadius)) {
        LazyColumn(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Menu", color = FirePlexColors.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
            item { MenuButton("Hide Menu", onToggleMenu) }
            item { MenuButton("Refresh", onRefresh) }
            item { MenuButton("Settings", onOpenSettings) }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Categories", color = FirePlexColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    val bg = if (focused) FirePlexColors.Accent else Color.Transparent
    val fg = if (focused) Color.Black else FirePlexColors.Text

    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FirePlexDimens.ButtonRadius))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
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
    val focusedColor = if (isCancelAction) FirePlexColors.Gold else FirePlexColors.AccentBright

    Surface(
        modifier = modifier
            .height(if (focused) 52.dp else 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onClick),
        color = if (focused) focusedColor else normalColor,
        shape = RoundedCornerShape(FirePlexDimens.ButtonRadius),
        border = BorderStroke(if (focused) FirePlexDimens.FocusBorder else FirePlexDimens.ThinBorder, if (focused) Color.White else FirePlexColors.BorderStrong)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.Black else FirePlexColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun EmptyPanel(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FirePlexColors.Panel),
        shape = RoundedCornerShape(FirePlexDimens.CardRadius),
        border = BorderStroke(FirePlexDimens.ThinBorder, FirePlexColors.Border)
    ) {
        Text(text = text, modifier = Modifier.padding(16.dp), color = FirePlexColors.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun LibraryPosterCard(library: PlexLibrary, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(if (focused) 166.dp else 158.dp)
            .height(if (focused) 224.dp else 214.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvRemoteClick(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = if (focused) FirePlexColors.PanelFocused else FirePlexColors.Panel),
        border = if (focused) BorderStroke(FirePlexDimens.FocusBorder, FirePlexColors.Accent) else BorderStroke(FirePlexDimens.ThinBorder, FirePlexColors.Border),
        shape = RoundedCornerShape(FirePlexDimens.CardRadius)
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF4A3208), Color(0xFF111820), Color(0xFF050608)))).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(library.title.ifBlank { "Library" }, color = FirePlexColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(library.type.ifBlank { "category" }, color = FirePlexColors.Accent, fontSize = 12.sp)
            }
            Text(if (focused) "Press OK" else "Open", color = FirePlexColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
            .focusable()
            .tvRemoteClick(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (focused) FirePlexColors.PanelFocused else FirePlexColors.Panel),
        border = if (focused) BorderStroke(FirePlexDimens.FocusBorder, FirePlexColors.Accent) else BorderStroke(FirePlexDimens.ThinBorder, FirePlexColors.Border),
        shape = RoundedCornerShape(FirePlexDimens.CardRadius)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(if (focused) 16.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(width = 106.dp, height = 70.dp).clip(RoundedCornerShape(FirePlexDimens.CardRadius)).background(Color(0xFF1A2028)), contentAlignment = Alignment.Center) {
                if (artworkUrl.isNotBlank()) {
                    AsyncImage(model = cachedImageModel(artworkUrl), contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(item.type.take(1).uppercase().ifBlank { "V" }, color = FirePlexColors.Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title.ifBlank { "Untitled" }, color = FirePlexColors.Text, fontSize = if (focused) 20.sp else 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(mediaMetaLine(item), color = FirePlexColors.Accent, fontSize = 12.sp)

                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(item.summary, color = FirePlexColors.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (focused) {
                Spacer(Modifier.width(12.dp))
                Text("OK", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.background(FirePlexColors.Accent, RoundedCornerShape(FirePlexDimens.ButtonRadius)).padding(horizontal = 12.dp, vertical = 6.dp))
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



