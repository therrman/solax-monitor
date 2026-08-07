package cz.solax.monitor.modbus

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Minimalistický Modbus TCP klient.
 *
 * Protokol je triviální: 7bajtová MBAP hlavička + PDU. Implementujeme jen dvě funkce,
 * které měnič potřebuje pro čtení - 0x03 (holding) a 0x04 (input registry).
 *
 * MBAP hlavička:
 *   [0..1] transaction id
 *   [2..3] protocol id (vždy 0)
 *   [4..5] délka následujících bajtů (unit id + PDU)
 *   [6]    unit id (slave)
 */
class ModbusTcpClient(
    private val host: String,
    private val port: Int = 502,
    private val slave: Int = 1,
    private val timeoutMs: Int = 5000,
    private val socketFactory: SocketFactory = SocketFactory.getDefault(),
) : RegisterSource, AutoCloseable {

    /** Měnič odpověděl, ale hlásí chybu (typicky illegal data address u nepodporovaného bloku). */
    class ModbusException(val code: Int, val function: Int) :
        IOException("Modbus exception 0x%02X u funkce 0x%02X".format(code, function))

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
    private var transactionId = 0

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    fun connect() {
        close()
        val s = socketFactory.createSocket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = timeoutMs
        socket = s
        input = DataInputStream(s.getInputStream())
        output = s.getOutputStream()
    }

    /** Input registry, funkce 0x04. Živá měření, read-only. */
    override fun readInputRegisters(address: Int, count: Int): IntArray = read(0x04, address, count)

    /** Holding registry, funkce 0x03. Konfigurace. */
    override fun readHoldingRegisters(address: Int, count: Int): IntArray = read(0x03, address, count)

    /**
     * Vrátí null, když měnič blok nepodporuje (Modbus exception), jinak propustí IOException dál.
     * Odpovídá read_ir/read_hr z read_solax.py.
     */
    override fun readInputOrNull(address: Int, count: Int): IntArray? = orNull { readInputRegisters(address, count) }

    override fun readHoldingOrNull(address: Int, count: Int): IntArray? = orNull { readHoldingRegisters(address, count) }

    private inline fun orNull(block: () -> IntArray): IntArray? = try {
        block()
    } catch (e: ModbusException) {
        null
    }

    private fun read(function: Int, address: Int, count: Int): IntArray {
        require(count in 1..125) { "Modbus dovolí maximálně 125 registrů na request, požadováno $count" }
        val out = output ?: throw IOException("Nepřipojeno")
        val inp = input ?: throw IOException("Nepřipojeno")

        transactionId = (transactionId + 1) and 0xFFFF
        val req = byteArrayOf(
            (transactionId shr 8).toByte(), transactionId.toByte(),
            0, 0,                                   // protocol id
            0, 6,                                   // délka: unit id + 5 bajtů PDU
            slave.toByte(),
            function.toByte(),
            (address shr 8).toByte(), address.toByte(),
            (count shr 8).toByte(), count.toByte(),
        )
        out.write(req)
        out.flush()

        val header = ByteArray(7)
        inp.readFully(header)
        val length = ((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)
        if (length < 2) throw IOException("Poškozená MBAP hlavička, délka $length")

        val body = ByteArray(length - 1)         // bez unit id, to je v hlavičce
        inp.readFully(body)

        val fn = body[0].toInt() and 0xFF
        if (fn and 0x80 != 0) {
            throw ModbusException(body[1].toInt() and 0xFF, function)
        }
        val byteCount = body[1].toInt() and 0xFF
        if (byteCount != count * 2) {
            throw IOException("Měnič vrátil $byteCount bajtů, čekali jsme ${count * 2}")
        }

        return IntArray(count) { i ->
            val hi = body[2 + i * 2].toInt() and 0xFF
            val lo = body[3 + i * 2].toInt() and 0xFF
            (hi shl 8) or lo
        }
    }

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }
}
