package com.nsec.vpn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceRecoverySheet(deviceId: String, onDismiss: () -> Unit,
    onStatus: suspend () -> Boolean, onSetPin: suspend (String, String?) -> Unit,
    onRecover: suspend (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(true) }
    var restore by remember { mutableStateOf(false) }
    var recoveryId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    fun loadStatus() {
        loading = true
        scope.launch {
            try { configured = onStatus(); message = null; failed = false }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Recovery Settings Unavailable"; failed = true }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { loadStatus() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetGesturesEnabled = !busy,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Device VIP Recovery", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.testTag("recovery_close")) {
                Icon(Icons.Default.Close, contentDescription = "Close Device VIP Recovery")
            }
        }
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save Your Device ID And A Private PIN Before Clearing App Data. Recovery Restores Existing VIP And Its Original Expiry.")
            DeviceIdCard(deviceId, label = "Current Device ID", tagPrefix = "recovery_device")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !restore, enabled = !busy, onClick = { restore = false; pin = ""; confirm = ""; oldPin = ""; message = null }, label = { Text("Protect VIP") })
                FilterChip(selected = restore, enabled = !busy, onClick = { restore = true; pin = ""; confirm = ""; oldPin = ""; message = null }, label = { Text("Restore VIP") })
            }
            if (restore) OutlinedTextField(value = recoveryId, onValueChange = { recoveryId = it.take(128) },
                label = { Text("Original Device ID") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            else {
                Text(if (configured == true) "Recovery PIN Is Set" else if (loading) "Checking Recovery Settings…" else "Set A Recovery PIN Before Clearing Data")
                if (configured == true) OutlinedTextField(value = oldPin, onValueChange = { oldPin = it.filter(Char::isDigit).take(12) },
                    label = { Text("Current Recovery PIN") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), enabled = !busy,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                label = { Text(if (restore) "Recovery PIN" else "New Recovery PIN (8–12 Digits)") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!restore) OutlinedTextField(value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(12) },
                label = { Text("Confirm New PIN") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), enabled = !busy,
                singleLine = true, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            if (!restore && configured == null && !loading) TextButton(onClick = { loadStatus() }) { Text("Retry Settings") }
            Button(enabled = !busy && pin.length in 8..12 && (if (restore) recoveryId.trim().length in 2..128
                else !loading && configured != null && pin == confirm && (configured != true || oldPin.length in 8..12)),
                onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            if (restore) onRecover(recoveryId.trim(), pin) else onSetPin(pin, oldPin.takeIf { configured == true })
                            if (!restore) configured = true
                            message = if (restore) "Device Restored. Existing VIP And Expiry Loaded." else "PIN Saved. Keep Your Device ID And PIN Somewhere Safe."
                            pin = ""; confirm = ""; oldPin = ""; failed = false
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "Recovery Failed. Please Retry."; failed = true }
                        finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Working…" else if (restore) "Restore Device And VIP" else "Save Recovery PIN")
            }
            Text("Device ID Alone Cannot Restore VIP. Keep The PIN Private; It Is Not Saved On This Phone. Five Incorrect Attempts Lock Recovery For 15 Minutes.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
}
