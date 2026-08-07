package cz.solax.monitor.data

/** Adresa měniče. Port je oddělený, protože přes SSH tunel bývá jiný než 502. */
data class Endpoint(val ip: String, val port: Int = Prefs.DEFAULT_PORT) {

    /** Tvar pro uložení do historie - port vždy explicitně. */
    val storage: String get() = "$ip:$port"

    /** Tvar pro zobrazení - port jen když není výchozí, ať chipy nejsou zbytečně dlouhé. */
    val display: String get() = if (port == Prefs.DEFAULT_PORT) ip else "$ip:$port"

    companion object {
        /** Přijme "192.168.0.245" i "127.0.0.1:1502". */
        fun parse(text: String, defaultPort: Int = Prefs.DEFAULT_PORT): Endpoint? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val colon = trimmed.lastIndexOf(':')
            if (colon <= 0) return Endpoint(trimmed, defaultPort)
            val port = trimmed.substring(colon + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return Endpoint(trimmed.substring(0, colon), port)
        }
    }
}
