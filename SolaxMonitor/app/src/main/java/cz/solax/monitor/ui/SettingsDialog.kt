package cz.solax.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.solax.monitor.data.Prefs

@Composable
fun SettingsDialog(
    refreshSeconds: Int,
    autoRefresh: Boolean,
    slaveId: Int,
    connectedEndpoint: String?,
    hacHosts: List<String>,
    onHacHosts: (String) -> Unit,
    onRefreshSeconds: (Int) -> Unit,
    onAutoRefresh: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by remember { mutableStateOf(refreshSeconds.toString()) }
    var hac by remember { mutableStateOf(hacHosts.joinToString(", ")) }

    // Adresy nabíječek se ukládají až při zavření, ať se nepřipojuje na rozepsané IP
    val finish = { onHacHosts(hac); onDismiss() }

    AlertDialog(
        onDismissRequest = finish,
        confirmButton = { TextButton(onClick = finish) { Text("Hotovo") } },
        dismissButton = {
            TextButton(onClick = { onDisconnect(); onDismiss() }) { Text("Odpojit") }
        },
        title = { Text("Nastavení") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatická obnova", Modifier.weight(1f))
                    Switch(checked = autoRefresh, onCheckedChange = onAutoRefresh)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Interval obnovy",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Prefs.PRESET_INTERVALS.forEach { seconds ->
                        FilterChip(
                            selected = refreshSeconds == seconds,
                            onClick = {
                                onRefreshSeconds(seconds)
                                custom = seconds.toString()
                            },
                            label = { Text("${seconds}s", fontSize = 12.sp) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { text ->
                        custom = text.filter { it.isDigit() }.take(2)
                        custom.toIntOrNull()?.let { onRefreshSeconds(it) }
                    },
                    label = { Text("Vlastní (${Prefs.MIN_REFRESH}–${Prefs.MAX_REFRESH} s)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hac,
                    onValueChange = { hac = it },
                    label = { Text("Nabíječky X3-HAC (IP, oddělené čárkou)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    buildString {
                        append("Připojeno k ${connectedEndpoint ?: "?"}, slave id $slaveId.")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Aplikace do měniče pouze čte, nic nezapisuje.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
