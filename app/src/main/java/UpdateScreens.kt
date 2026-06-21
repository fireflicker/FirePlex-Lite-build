package com.fireflicker.fireplex2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun UpdateContentsScreen(
    loading: Boolean,
    status: String,
    vodStatus: String,
    seriesStatus: String,
    artworkStatus: String,
    cachedAt: Long,
    onStartUpdate: () -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    val backReady = !loading
    val startFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(backReady, loading) {
        runCatching { if (backReady) backFocus.requestFocus() else startFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Update Media Contents", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
        Spacer(Modifier.height(28.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UpdateStatusTile("MOVIES", vodStatus, Modifier.weight(1f))
            UpdateStatusTile("SERIES", seriesStatus, Modifier.weight(1f))
            UpdateStatusTile("ARTWORK", artworkStatus, Modifier.weight(1f))
        }

        Spacer(Modifier.height(38.dp))
        if (loading) {
            CircularProgressIndicator(color = Color(0xFF74F3F0))
            Spacer(Modifier.height(18.dp))
            Text("Please wait...", color = Color.White, fontSize = 26.sp)
        } else {
            Text(status, color = Color(0xFFB7C7D8), fontSize = 17.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.weight(1f))
        Text(cacheLabel(cachedAt), color = Color(0xFF00E676), fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusActionButton("CLEAR CACHE", Modifier.weight(1f), Color(0xFF5D1726)) { if (!loading) onClearCache() }
            FocusActionButton(
                if (loading) "PLEASE WAIT..." else "START UPDATE",
                Modifier.weight(2f).focusRequester(startFocus),
                Color(0xFF203040)
            ) { if (!loading) onStartUpdate() }
            if (backReady) {
                FocusActionButton("BACK", Modifier.weight(1f).focusRequester(backFocus), Color(0xFF203040)) { onBack() }
            }
        }
    }
}


