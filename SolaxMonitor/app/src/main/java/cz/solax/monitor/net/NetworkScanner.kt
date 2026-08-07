package cz.solax.monitor.net

import cz.solax.monitor.modbus.ModbusTcpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import javax.net.SocketFactory

/** Nalezený měnič: adresa, port a slave id, na které odpověděl. */
data class FoundInverter(val ip: String, val port: Int, val slaveId: Int, val model: String?)

/**
 * Port scan podsítě /24 na portu 502 a ověření, že tam opravdu sedí Modbus.
 * Kotlinská obdoba scan_solax.py.
 */
class NetworkScanner(
    private val socketFactory: SocketFactory = SocketFactory.getDefault(),
) {
    /** Rychlá zkouška jedné adresy - odpoví na portu 502 a umí Modbus? */
    suspend fun probe(ip: String, port: Int = 502, slaveIds: List<Int> = SLAVE_IDS): FoundInverter? =
        withContext(Dispatchers.IO) {
            if (!isPortOpen(ip, port, PROBE_TIMEOUT_MS)) return@withContext null
            for (slave in slaveIds) {
                val found = runCatching {
                    ModbusTcpClient(ip, port, slave, MODBUS_TIMEOUT_MS, socketFactory).use { client ->
                        client.connect()
                        // holding 0x00-0x14 je InverterSN + FactoryName + ModuleName
                        val id = client.readHoldingOrNull(0x00, 0x15)
                        FoundInverter(ip, port, slave, id?.let { chars14(it, 0x0E, 7) })
                    }
                }.getOrNull()
                if (found != null) return@withContext found
            }
            null
        }

    /**
     * Oskenuje celý rozsah subnet.1 až subnet.254. Průběh hlásí přes [onProgress].
     */
    suspend fun scanSubnet(
        subnet: String,
        port: Int = 502,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<FoundInverter> = coroutineScope {
        val hosts = (1..254).map { "$subnet.$it" }
        var done = 0
        val open = hosts.chunked(CONCURRENCY).flatMap { chunk ->
            chunk.map { ip ->
                async(Dispatchers.IO) {
                    val reachable = isPortOpen(ip, port, PROBE_TIMEOUT_MS)
                    synchronized(hosts) { onProgress(++done, hosts.size) }
                    if (reachable) ip else null
                }
            }.awaitAll().filterNotNull()
        }
        // Modbus ověříme až u těch mála, které mají port otevřený
        open.mapNotNull { probe(it, port) }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        socketFactory.createSocket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    } catch (e: Exception) {
        false
    }

    companion object {
        val SLAVE_IDS = listOf(1, 0, 247)
        private const val PROBE_TIMEOUT_MS = 400
        private const val MODBUS_TIMEOUT_MS = 2000
        private const val CONCURRENCY = 48

        /** 14char pole: každý registr nese 2 ASCII znaky, MSB první. */
        fun chars14(regs: IntArray, from: Int, count: Int): String? {
            if (from + count > regs.size) return null
            val sb = StringBuilder()
            for (i in from until from + count) {
                sb.append(((regs[i] shr 8) and 0xFF).toChar())
                sb.append((regs[i] and 0xFF).toChar())
            }
            return sb.filter { it.code in 32..126 }.toString().trim().ifEmpty { null }
        }
    }
}
