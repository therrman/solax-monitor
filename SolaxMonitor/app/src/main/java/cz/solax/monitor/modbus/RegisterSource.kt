package cz.solax.monitor.modbus

/**
 * Zdroj registrů. Odděluje dekódování od transportu, takže se do SolaxReaderu
 * dá podstrčit i nahraný snímek registrů a ověřit, že dekóduje stejně jako
 * referenční read_solax.py.
 */
interface RegisterSource {
    fun readInputRegisters(address: Int, count: Int): IntArray
    fun readHoldingRegisters(address: Int, count: Int): IntArray

    /** null = měnič blok nepodporuje (Modbus exception), na rozdíl od chyby spojení. */
    fun readInputOrNull(address: Int, count: Int): IntArray?
    fun readHoldingOrNull(address: Int, count: Int): IntArray?
}
