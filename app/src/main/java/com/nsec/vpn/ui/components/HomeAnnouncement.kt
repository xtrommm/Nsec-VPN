package com.nsec.vpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** New announcement content reopens the centered banner; identical refreshes preserve dismissal. */
@Composable
fun HomeAnnouncement(title: String?, body: String?, visible: Boolean = true) {
    var dismissed by rememberSaveable(title, body) { mutableStateOf(false) }
    if (!visible || title.isNullOrBlank() || dismissed) return
    Dialog(onDismissRequest = { dismissed = true }) {
        Surface(Modifier.fillMaxWidth().testTag("home_announcement"), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), tonalElevation = 2.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.Info, null, Modifier.padding(10.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("ANNOUNCEMENT", Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { dismissed = true }, modifier = Modifier.testTag("announcement_close")) {
                        Icon(Icons.Default.Close, "Dismiss Announcement", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
