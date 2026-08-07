package cz.solax.monitor.data

/**
 * Šířka a znaménko jsou u Solaxu per registr, 32bit hodnoty jsou little endian
 * (nižší slovo první). Použití špatného helperu dá věrohodně vypadající nesmysl,
 * proto jsou to samostatné funkce se stejnými jmény jako v read_solax.py.
 */

/** Signed 16bit. */
fun s16(v: Int): Int = if (v > 32767) v - 65536 else v

/** Unsigned 32bit z dvojice registrů, nižší slovo první. */
fun u32le(lsb: Int, msb: Int): Long = (msb.toLong() shl 16) or lsb.toLong()

/** Signed 32bit z dvojice registrů, nižší slovo první. */
fun s32le(lsb: Int, msb: Int): Int {
    val v = u32le(lsb, msb)
    return (if (v >= (1L shl 31)) v - (1L shl 32) else v).toInt()
}

fun hi(v: Int): Int = (v shr 8) and 0xFF

fun lo(v: Int): Int = v and 0xFF

/** 14char pole: každý registr nese 2 ASCII znaky, MSB první. */
fun chars14(regs: IntArray, from: Int, count: Int): String? {
    if (from + count > regs.size) return null
    val sb = StringBuilder()
    for (i in from until from + count) {
        sb.append(hi(regs[i]).toChar())
        sb.append(lo(regs[i]).toChar())
    }
    return sb.filter { it.code in 32..126 }.toString().trim().ifEmpty { null }
}

/** Registr časového okna: Hi bajt je minuta, Lo bajt je hodina. */
fun timeOfDay(reg: Int): String = "%02d:%02d".format(lo(reg), hi(reg))
