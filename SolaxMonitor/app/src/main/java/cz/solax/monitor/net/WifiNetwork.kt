package cz.solax.monitor.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import javax.net.SocketFactory

/**
 * Když se telefon připojí k Wi-Fi donglu měniče, ta síť nemá internet. Android proto
 * veškerý provoz tiše přesměruje na mobilní data a spojení na 192.168.10.10 selže.
 *
 * Řešením je vytvořit socket přes SocketFactory konkrétní Wi-Fi sítě - ten se na ni
 * naváže bez ohledu na to, kudy vede default route.
 */
object WifiNetwork {

    private fun cm(context: Context): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Wi-Fi síť, i když zrovna není default (typicky protože nemá internet). */
    fun find(context: Context): Network? {
        val manager = cm(context)
        @Suppress("DEPRECATION")
        return manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** SocketFactory navázaná na Wi-Fi; když Wi-Fi není, vrátí výchozí. */
    fun socketFactory(context: Context): SocketFactory =
        find(context)?.socketFactory ?: SocketFactory.getDefault()

    /**
     * Lokální IPv4 adresa telefonu na Wi-Fi síti, např. "192.168.10.5".
     * Z ní odvozujeme podsíť pro sken.
     */
    fun localIpv4(context: Context): String? {
        val manager = cm(context)
        val network = find(context) ?: return null
        return manager.getLinkProperties(network)
            ?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    /** "192.168.10.5" -> "192.168.10". Vrátí null, když nejsme na Wi-Fi. */
    fun subnet(context: Context): String? =
        localIpv4(context)?.substringBeforeLast('.', "")?.takeIf { it.isNotEmpty() }
}
