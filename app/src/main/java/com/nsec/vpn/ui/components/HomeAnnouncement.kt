package com.nsec.vpn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** New announcement content reopens the banner; identical refreshes preserve dismissal. */
@Composable
fun HomeAnnouncement(title: String?, body: String?, visible: Boolean = true) {
    var dismissed by rememberSaveable(title, body) { mutableStateOf(false) }
    if (!visible || title.isNullOrBlank() || dismissed) return
    Dialog(onDismissRequest = { dismissed = true }) {
        Surface(Modifier.fillMaxWidth().testTag("home_announcement"),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 6.dp) {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    IconButton(onClick = { dismissed = true }, modifier = Modifier.testTag("announcement_close")) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss Announcement",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                if (!body.isNullOrBlank()) Text(body, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
