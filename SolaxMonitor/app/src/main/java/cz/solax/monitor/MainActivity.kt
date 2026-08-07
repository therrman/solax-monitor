package cz.solax.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.solax.monitor.ui.ConnectScreen
import cz.solax.monitor.ui.DashboardScreen
import cz.solax.monitor.ui.SettingsDialog
import cz.solax.monitor.ui.SolaxColors
import cz.solax.monitor.ui.SolaxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: SolaxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolaxTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: SolaxViewModel) {
    val state by vm.state.collectAsState()

    if (state.showSettings) {
        SettingsDialog(
            refreshSeconds = state.refreshSeconds,
            autoRefresh = state.autoRefresh,
            slaveId = state.slaveId,
            connectedEndpoint = state.connected?.display,
            onRefreshSeconds = vm::setRefreshSeconds,
            onAutoRefresh = vm::setAutoRefresh,
            onDisconnect = vm::disconnect,
            onDismiss = { vm.showSettings(false) },
        )
    }

    when (state.screen) {
        Screen.CONNECT -> ConnectScreen(
            state = state,
            onIpChange = vm::onIpInput,
            onPortChange = vm::onPortInput,
            onUseEndpoint = vm::useEndpoint,
            onConnect = vm::connectToInput,
            onConnectFound = vm::connectTo,
            onScan = vm::scan,
            onSlaveChange = vm::setSlaveId,
        )

        Screen.DASHBOARD -> Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Column {
                            Text("Solax Monitor", fontSize = 18.sp)
                            Text(
                                buildString {
                                    append(state.connected?.display ?: "")
                                    if (state.lastUpdateMs > 0) {
                                        append(" · ")
                                        append(TIME_FORMAT.format(Date(state.lastUpdateMs)))
                                    }
                                    if (!state.autoRefresh) append(" · pozastaveno")
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = vm::refreshNow) {
                                Icon(Icons.Default.Refresh, contentDescription = "Obnovit")
                            }
                        }
                        IconButton(onClick = { vm.showSettings(true) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Nastavení")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                if (state.error != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.error!!, fontSize = 13.sp, color = SolaxColors.Bad)
                    }
                }
                state.data?.let { DashboardScreen(it) }
                Spacer(Modifier.size(0.dp))
            }
        }
    }
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
