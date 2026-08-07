package cz.solax.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.solax.monitor.UiState
import cz.solax.monitor.data.Endpoint
import cz.solax.monitor.data.Prefs

@Composable
fun ConnectScreen(
    state: UiState,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUseEndpoint: (Endpoint) -> Unit,
    onConnect: () -> Unit,
    onConnectFound: (Endpoint) -> Unit,
    onScan: () -> Unit,
    onSlaveChange: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Solax Monitor",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "X3-Hybrid G4 přes Modbus TCP",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.busyMessage != null) {
            SectionCard("Připojování") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(state.busyMessage, fontSize = 14.sp)
                }
                if (state.scanProgress != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.scanProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${state.scanProgress} %",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.found.isNotEmpty()) {
            SectionCard("Nalezené měniče") {
                state.found.forEach { found ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                Endpoint(found.ip, found.port).display,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                buildString {
                                    append("slave ${found.slaveId}")
                                    found.model?.let { append(" · $it") }
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onConnectFound(Endpoint(found.ip, found.port)) }) {
                            Text("Připojit")
                        }
                    }
                }
            }
        }

        SectionCard("Adresa měniče") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.ipInput,
                    onValueChange = onIpChange,
                    label = { Text("IP adresa") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.portInput,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }
            Text(
                "Výchozí ${Prefs.DEFAULT_IP}:${Prefs.DEFAULT_PORT} je Wi-Fi dongle v režimu AP. " +
                    "Port měň jen při tunelu.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Naposledy použité",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.history.take(Prefs.HISTORY_SIZE).forEach { endpoint ->
                        AssistChip(
                            onClick = { onUseEndpoint(endpoint) },
                            label = { Text(endpoint.display, fontSize = 12.sp) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = state.busyMessage == null && state.ipInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Připojit") }
                OutlinedButton(
                    onClick = onScan,
                    enabled = state.busyMessage == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, Modifier.height(18.dp))
                    Text(" Najít měnič")
                }
            }
        }

        SectionCard("Pokročilé") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Modbus slave id", fontSize = 14.sp, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = state.slaveId.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { onSlaveChange(it.coerceIn(0, 247)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(0.35f),
                )
            }
            Text(
                "Výchozí 1. Měnit jen když měnič na jedničku neodpovídá.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (state.error != null) {
            SectionCard("Chyba") {
                Text(state.error, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
