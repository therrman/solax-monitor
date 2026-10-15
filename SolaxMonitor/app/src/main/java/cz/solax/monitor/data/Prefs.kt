package cz.solax.monitor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("solax")

/** Uložené nastavení: poslední adresy, slave id, interval obnovy a adresy nabíječek. */
class Prefs(private val context: Context) {

    suspend fun history(): List<Endpoint> =
        context.dataStore.data.first()[KEY_HISTORY]
            ?.split('\n')
            ?.mapNotNull { Endpoint.parse(it) }
            ?: emptyList()

    /** Přidá adresu na začátek historie, drží posledních [HISTORY_SIZE]. */
    suspend fun remember(endpoint: Endpoint) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(endpoint.storage) + current.filter { it != endpoint.storage })
                .take(HISTORY_SIZE)
            prefs[KEY_HISTORY] = updated.joinToString("\n")
        }
    }

    suspend fun slaveId(): Int = context.dataStore.data.first()[KEY_SLAVE] ?: DEFAULT_SLAVE

    suspend fun setSlaveId(id: Int) {
        context.dataStore.edit { it[KEY_SLAVE] = id }
    }

    suspend fun refreshSeconds(): Int = context.dataStore.data.first()[KEY_REFRESH] ?: DEFAULT_REFRESH

    suspend fun setRefreshSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_REFRESH] = seconds.coerceIn(MIN_REFRESH, MAX_REFRESH) }
    }

    /** Adresy nabíječek X3-HAC (port 502, unit id ignorují). */
    suspend fun hacHosts(): List<String> =
        context.dataStore.data.first()[KEY_HAC]
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_HAC_HOSTS

    suspend fun setHacHosts(hosts: List<String>) {
        context.dataStore.edit { it[KEY_HAC] = hosts.joinToString("\n") }
    }

    companion object {
        /** Výchozí adresa Wi-Fi donglu v režimu AP. */
        const val DEFAULT_IP = "192.168.10.10"
        const val DEFAULT_PORT = 502
        const val DEFAULT_SLAVE = 1
        const val DEFAULT_REFRESH = 5
        const val MIN_REFRESH = 1
        const val MAX_REFRESH = 30
        const val HISTORY_SIZE = 5
        val PRESET_INTERVALS = listOf(2, 5, 10, 30)

        /** Primary (…CH3N) a secondary (…CH3U) nabíječka v paralelu. */
        val DEFAULT_HAC_HOSTS = listOf("192.168.0.137", "192.168.0.142")

        private val KEY_HISTORY = stringPreferencesKey("history")
        private val KEY_SLAVE = intPreferencesKey("slave")
        private val KEY_REFRESH = intPreferencesKey("refresh")
        private val KEY_HAC = stringPreferencesKey("hac_hosts")
    }
}
