package com.nsec.vpn.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerRequestSheet(onDismiss: () -> Unit, onSubmit: suspend (String, String, String?) -> String) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var country by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val badCharacters = remember { Regex("[<>\\p{Cntrl}]") }
    val countryValid = country.trim().length in 2..80 && !badCharacters.containsMatchIn(country)
    val cityValid = city.trim().length in 2..80 && !badCharacters.containsMatchIn(city)
    val noteValid = note.length <= 500 && !Regex("[\\p{Cntrl}&&[^\\n\\r\\t]]").containsMatchIn(note)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun submit() {
        attempted = true
        if (submitting || !countryValid || !cityValid || !noteValid) return
        focus.clearFocus(); submitting = true; error = null
        scope.launch {
            try {
                val result = onSubmit(country.trim(), city.trim(), note.trim().takeIf { it.isNotEmpty() })
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                onDismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not send your request. Please try again." }
            finally { submitting = false }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!submitting) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !submitting,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !submitting, shouldDismissOnClickOutside = !submitting),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Suggest a location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Help shape our server network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, enabled = !submitting) { Icon(Icons.Default.Close, contentDescription = "Close server request") }
            }
            Text("Tell us where you’d like to connect. Your request goes to our team for review; availability is not guaranteed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = country, onValueChange = { country = it.take(80); error = null },
                label = { Text("Country") }, leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                singleLine = true, enabled = !submitting, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                isError = attempted && !countryValid,
                supportingText = { if (attempted && !countryValid) Text("Enter a country using 2–80 characters.") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
            )
            OutlinedTextField(
                value = city, onValueChange = { city = it.take(80); error = null },
                label = { Text("City") }, leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true, enabled = !submitting, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                isError = attempted && !cityValid,
                supportingText = { if (attempted && !cityValid) Text("Enter a city using 2–80 characters.") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(500); error = null },
                label = { Text("Anything we should know? (optional)") },
                minLines = 3, maxLines = 5, enabled = !submitting, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                isError = attempted && !noteValid,
                supportingText = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (attempted && !noteValid) "Remove unsupported characters." else "Please avoid personal or sensitive details.", modifier = Modifier.weight(1f))
                    Text("${note.length}/500", modifier = Modifier.padding(start = 8.dp))
                } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Text("One request every 10 minutes. Your installation ID and app version are attached so we can review your request.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
            }
            error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }
            Button(onClick = { submit() }, enabled = !submitting, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (submitting) "Sending request…" else "Send location request", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
