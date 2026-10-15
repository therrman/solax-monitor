package cz.solax.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.solax.monitor.data.Endpoint
import cz.solax.monitor.data.HacData
import cz.solax.monitor.data.HacReader
import cz.solax.monitor.data.Prefs
import cz.solax.monitor.data.SolaxData
import cz.solax.monitor.data.SolaxReader
import cz.solax.monitor.modbus.ModbusTcpClient
import cz.solax.monitor.net.FoundInverter
import cz.solax.monitor.net.NetworkScanner
import cz.solax.monitor.net.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class Screen { CONNECT, DASHBOARD }

/** Záložky na dashboardu: měnič a nabíječky X3-HAC. */
enum class Tab { INVERTER, CHARGERS }

/** Poslední známý stav jedné nabíječky. [data] zůstává i po chybě čtení, ať obrazovka neblikne prázdná. */
data class ChargerState(
    val host: String,
    val data: HacData? = null,
    val error: String? = null,
)

data class UiState(
    val screen: Screen = Screen.CONNECT,
    val ipInput: String = Prefs.DEFAULT_IP,
    val portInput: String = Prefs.DEFAULT_PORT.toString(),
    val history: List<Endpoint> = emptyList(),
    val slaveId: Int = Prefs.DEFAULT_SLAVE,
    val refreshSeconds: Int = Prefs.DEFAULT_REFRESH,
    val autoRefresh: Boolean = true,
    val busyMessage: String? = null,
    val scanProgress: Int? = null,
    val found: List<FoundInverter> = emptyList(),
    val error: String? = null,
    val data: SolaxData? = null,
    val connected: Endpoint? = null,
    val lastUpdateMs: Long = 0L,
    val refreshing: Boolean = false,
    val showSettings: Boolean = false,
    val tab: Tab = Tab.INVERTER,
    val hacHosts: List<String> = Prefs.DEFAULT_HAC_HOSTS,
    val chargers: List<ChargerState> = Prefs.DEFAULT_HAC_HOSTS.map { ChargerState(it) },
    val chargersRefreshing: Boolean = false,
    /** Průběh hledání nabíječek v %, null = neběží. */
    val hacScanProgress: Int? = null,
    val hacScanMessage: String? = null,
)

class SolaxViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: ModbusTcpClient? = null
    private var reader: SolaxReader? = null
    private var refreshJob: Job? = null
    private var connectJob: Job? = null
    private var chargerJob: Job? = null
    private var paused = false

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    history = prefs.history(),
                    slaveId = prefs.slaveId(),
                    refreshSeconds = prefs.refreshSeconds(),
                    hacHosts = prefs.hacHosts(),
                    chargers = prefs.hacHosts().map { host -> ChargerState(host) },
                )
            }
            autoConnect()
        }
    }

    // ------------------------------------------------------------ připojení

    /**
     * Zkusí výchozí adresu donglu, pak naposledy použité, a když nic neodpoví,
     * spustí sken podsítě.
     */
    fun autoConnect() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            val candidates = (listOf(Endpoint(Prefs.DEFAULT_IP)) + _state.value.history).distinct()
            for (endpoint in candidates) {
                _state.update {
                    it.copy(busyMessage = "Zkouším ${endpoint.display}…", error = null, found = emptyList())
                }
                if (tryConnect(endpoint)) return@launch
            }
            _state.update { it.copy(busyMessage = null) }
            scan()
        }
    }

    fun connectTo(endpoint: Endpoint) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Připojuji k ${endpoint.display}…", error = null) }
            if (!tryConnect(endpoint)) {
                _state.update {
                    it.copy(
                        busyMessage = null,
                        error = "Na ${endpoint.display} se nepodařilo navázat Modbus spojení.",
                    )
                }
            }
        }
    }

    /** Připojí se podle toho, co je právě v polích IP a port. */
    fun connectToInput() {
        val s = _state.value
        val endpoint = Endpoint.parse(s.ipInput, s.portInput.toIntOrNull() ?: Prefs.DEFAULT_PORT)
        if (endpoint == null) {
            _state.update { it.copy(error = "Neplatná adresa nebo port.") }
            return
        }
        connectTo(endpoint)
    }

    private suspend fun tryConnect(endpoint: Endpoint): Boolean {
        val slave = _state.value.slaveId
        val factory = WifiNetwork.socketFactory(getApplication())
        val fresh = ModbusTcpClient(endpoint.ip, endpoint.port, slave, TIMEOUT_MS, factory)
        val data = withContext(Dispatchers.IO) {
            runCatching {
                fresh.connect()
                SolaxReader(fresh).let { r -> r.read() to r }
            }.getOrNull()
        }
        if (data == null) {
            runCatching { fresh.close() }
            return false
        }
        client?.close()
        client = fresh
        reader = data.second
        prefs.remember(endpoint)
        val history = prefs.history()
        _state.update {
            it.copy(
                screen = Screen.DASHBOARD,
                connected = endpoint,
                ipInput = endpoint.ip,
                portInput = endpoint.port.toString(),
                data = data.first,
                lastUpdateMs = System.currentTimeMillis(),
                busyMessage = null,
                error = null,
                history = history,
            )
        }
        startRefreshLoop()
        return true
    }

    fun scan() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val subnet = WifiNetwork.subnet(context) ?: Prefs.DEFAULT_IP.substringBeforeLast('.')
            val port = _state.value.portInput.toIntOrNull() ?: Prefs.DEFAULT_PORT
            _state.update {
                it.copy(
                    busyMessage = "Prohledávám $subnet.0/24 na portu $port…",
                    scanProgress = 0, found = emptyList(), error = null,
                )
            }
            val scanner = NetworkScanner(WifiNetwork.socketFactory(context))
            val found = runCatching {
                scanner.scanSubnet(subnet, port) { done, total ->
                    _state.update { it.copy(scanProgress = done * 100 / total) }
                }
            }.getOrElse { emptyList() }

            _state.update {
                it.copy(
                    busyMessage = null,
                    scanProgress = null,
                    found = found,
                    error = if (found.isEmpty()) {
                        "V síti $subnet.0/24 neodpověděl na portu $port žádný měnič. " +
                            "Zkontroluj, že jsi na stejné Wi-Fi, nebo zadej adresu ručně."
                    } else {
                        null
                    },
                )
            }
            if (found.size == 1) connectTo(found.first().let { Endpoint(it.ip, it.port) })
        }
    }

    // ------------------------------------------------------------ obnovování

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                val s = _state.value
                if (s.screen != Screen.DASHBOARD) break
                delay(s.refreshSeconds * 1000L)
                if (paused || !_state.value.autoRefresh) continue
                refreshOnce()
            }
        }
    }

    fun refreshNow() {
        if (_state.value.tab == Tab.CHARGERS) {
            viewModelScope.launch { refreshChargers() }
        } else {
            viewModelScope.launch { refreshOnce() }
        }
    }

    // ------------------------------------------------------------ nabíječky

    fun selectTab(tab: Tab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        if (tab == Tab.CHARGERS) startChargerLoop() else chargerJob?.cancel()
    }

    /**
     * Nabíječky se čtou jen na své záložce. Jedno čtení trvá ~3 s (protokol chce 1 s mezi dotazy),
     * takže interval je aspoň [MIN_CHARGER_REFRESH_S] a další čtení začne až po dokončení předchozího.
     */
    private fun startChargerLoop() {
        chargerJob?.cancel()
        chargerJob = viewModelScope.launch {
            var first = true
            while (true) {
                // První čtení hned po otevření záložky, další jen při zapnuté automatické obnově
                if (!paused && (first || _state.value.autoRefresh)) refreshChargers()
                first = false
                delay(maxOf(_state.value.refreshSeconds, MIN_CHARGER_REFRESH_S) * 1000L)
                if (_state.value.screen != Screen.DASHBOARD || _state.value.tab != Tab.CHARGERS) break
            }
        }
    }

    /** Obě nabíječky se čtou současně, každá po vlastním spojení. Jen čtení. */
    private suspend fun refreshChargers() {
        if (_state.value.chargersRefreshing) return
        val hosts = _state.value.hacHosts
        val factory = WifiNetwork.socketFactory(getApplication())
        _state.update { it.copy(chargersRefreshing = true) }
        val results = withContext(Dispatchers.IO) {
            hosts.map { host ->
                async {
                    runCatching {
                        ModbusTcpClient(host, 502, 1, TIMEOUT_MS, factory).use { c ->
                            c.connect()
                            HacReader(c).read()
                        }
                    }
                }
            }.awaitAll()
        }
        _state.update { st ->
            st.copy(
                chargersRefreshing = false,
                chargers = hosts.mapIndexed { i, host ->
                    val old = st.chargers.firstOrNull { it.host == host }
                    results[i].fold(
                        onSuccess = { ChargerState(host, it, null) },
                        onFailure = { e ->
                            ChargerState(host, old?.data, "${e.message ?: e.javaClass.simpleName}")
                        },
                    )
                },
            )
        }
    }

    /** Oskenuje podsíť a nalezené nabíječky uloží jako seznam adres. Nic nenajde = seznam zůstane. */
    fun scanChargers() {
        if (_state.value.hacScanProgress != null) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            val subnet = WifiNetwork.subnet(context)
            if (subnet == null) {
                _state.update { it.copy(hacScanMessage = "Telefon není připojený k Wi-Fi.") }
                return@launch
            }
            _state.update { it.copy(hacScanProgress = 0, hacScanMessage = null) }
            val found = runCatching {
                NetworkScanner(WifiNetwork.socketFactory(context)).scanChargers(subnet) { done, total ->
                    _state.update { it.copy(hacScanProgress = done * 100 / total) }
                }
            }.getOrElse { emptyList() }
            _state.update {
                it.copy(
                    hacScanProgress = null,
                    hacScanMessage = if (found.isEmpty()) {
                        "V síti $subnet.0/24 nebyla nalezena žádná nabíječka."
                    } else {
                        "Nalezeno: " + found.joinToString(", ") { f -> "${f.ip} (${f.serial})" }
                    },
                )
            }
            if (found.isNotEmpty()) setHacHosts(found.joinToString(",") { it.ip })
        }
    }

    fun setHacHosts(text: String) {
        val hosts = text.split(',', ';', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (hosts.isEmpty()) return
        _state.update { st ->
            st.copy(hacHosts = hosts, chargers = hosts.map { h -> st.chargers.firstOrNull { it.host == h } ?: ChargerState(h) })
        }
        viewModelScope.launch { prefs.setHacHosts(hosts) }
        if (_state.value.tab == Tab.CHARGERS) startChargerLoop()
    }

    private suspend fun refreshOnce() {
        val r = reader ?: return
        val c = client ?: return
        _state.update { it.copy(refreshing = true) }
        val result = withContext(Dispatchers.IO) {
            runCatching { r.read() }.recoverCatching { first ->
                // Spojení mohlo vypadnout (dongle je občas vyhodí) - jeden pokus o obnovu
                if (first is IOException) {
                    c.connect()
                    r.read()
                } else {
                    throw first
                }
            }
        }
        result.fold(
            onSuccess = { data ->
                _state.update {
                    it.copy(
                        data = data,
                        lastUpdateMs = System.currentTimeMillis(),
                        refreshing = false,
                        error = null,
                    )
                }
            },
            onFailure = { e ->
                _state.update {
                    it.copy(refreshing = false, error = "Čtení selhalo: ${e.message ?: e.javaClass.simpleName}")
                }
            },
        )
    }

    fun disconnect() {
        refreshJob?.cancel()
        chargerJob?.cancel()
        connectJob?.cancel()
        client?.close()
        client = null
        reader = null
        viewModelScope.launch {
            _state.update {
                it.copy(
                    screen = Screen.CONNECT,
                    tab = Tab.INVERTER,
                    data = null,
                    connected = null,
                    error = null,
                    found = emptyList(),
                    history = prefs.history(),
                )
            }
        }
    }

    // ------------------------------------------------------------ nastavení

    fun onIpInput(value: String) = _state.update { it.copy(ipInput = value) }

    fun onPortInput(value: String) =
        _state.update { it.copy(portInput = value.filter { c -> c.isDigit() }.take(5)) }

    /** Chip z historie naplní obě pole najednou. */
    fun useEndpoint(endpoint: Endpoint) = _state.update {
        it.copy(ipInput = endpoint.ip, portInput = endpoint.port.toString())
    }

    fun setAutoRefresh(enabled: Boolean) = _state.update { it.copy(autoRefresh = enabled) }

    fun setRefreshSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(Prefs.MIN_REFRESH, Prefs.MAX_REFRESH)
        _state.update { it.copy(refreshSeconds = clamped) }
        viewModelScope.launch { prefs.setRefreshSeconds(clamped) }
    }

    fun setSlaveId(id: Int) {
        _state.update { it.copy(slaveId = id) }
        viewModelScope.launch { prefs.setSlaveId(id) }
    }

    fun showSettings(show: Boolean) = _state.update { it.copy(showSettings = show) }

    fun onPause() {
        paused = true
    }

    fun onResume() {
        paused = false
        if (_state.value.screen == Screen.DASHBOARD) refreshNow()
    }

    override fun onCleared() {
        client?.close()
        super.onCleared()
    }

    companion object {
        private const val TIMEOUT_MS = 5000
        private const val MIN_CHARGER_REFRESH_S = 5
    }
}
