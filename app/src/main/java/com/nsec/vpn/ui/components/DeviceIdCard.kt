package com.nsec.vpn.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Copy only the public installation identifier; recovery PINs never enter this component. */
@Composable
fun DeviceIdCard(deviceId: String, modifier: Modifier = Modifier,
                 label: String = "Your Device ID", tagPrefix: String = "device") {
    val context = LocalContext.current
    var copied by remember(deviceId) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1_800); copied = false } }
    Surface(modifier.fillMaxWidth().testTag("${tagPrefix}_card"), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(deviceId, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("${tagPrefix}_value"))
            TextButton(enabled = deviceId.isNotBlank(), onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    try {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Nsec VPN Device ID", deviceId))
                        copied = true
                        Toast.makeText(context, "Device ID Copied", Toast.LENGTH_SHORT).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, "Clipboard Access Is Blocked On This Device.", Toast.LENGTH_LONG).show()
                    }
                } else Toast.makeText(context, "Clipboard Unavailable On This Device.", Toast.LENGTH_LONG).show()
            }, modifier = Modifier.heightIn(min = 48.dp).testTag("${tagPrefix}_copy")) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied" else "Copy Device ID")
            }
        }
    }
}
