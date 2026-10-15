import cz.solax.monitor.data.BDC_STATE
import cz.solax.monitor.data.HacReader
import cz.solax.monitor.data.MANUAL_MODE
import cz.solax.monitor.data.POWER_CONTROL
import cz.solax.monitor.data.SolaxData
import cz.solax.monitor.data.SolaxReader
import cz.solax.monitor.data.USE_MODE
import cz.solax.monitor.modbus.ModbusTcpClient
import cz.solax.monitor.modbus.RegisterSource
import java.io.File
import java.util.Locale

/**
 * Ověřovací běh: načte snímek registrů uložený read_solax.py (SOLAX_RECORD) a
 * vypíše dekódované hodnoty ve stejném formátu jako referenční skript.
 * Výstupy pak stačí porovnat diffem - obě strany dostaly identická vstupní data.
 *
 * Použití:
 *   verify <snapshot.json>          přehraje nahraný snímek
 *   verify --live <host> <port>     čte přímo z měniče
 *   verify --hac <host>             čte přímo z nabíječky X3-HAC (port 502)
 */
fun main(args: Array<String>) {
    // Python tiskne desetinnou tečku, Kotlin by v české locale dal čárku
    Locale.setDefault(Locale.ROOT)

    if (args.firstOrNull() == "--hac") {
        printHac(args.getOrElse(1) { "192.168.0.137" })
        return
    }

    val source: RegisterSource = if (args.firstOrNull() == "--live") {
        val host = args.getOrElse(1) { "127.0.0.1" }
        val port = args.getOrElse(2) { "1502" }.toInt()
        ModbusTcpClient(host, port, 1, 5000).also { it.connect() }
    } else {
        SnapshotSource(File(args.firstOrNull() ?: "/tmp/solax_raw.json"))
    }

    printLikePython(SolaxReader(source).read())
}

/** Přehraje registry ze snímku, který uložil read_solax.py. */
private class SnapshotSource(file: File) : RegisterSource {
    // Formát: {"i:0:96": [1,2,...], "h:125:62": null, ...}
    private val blocks: Map<String, IntArray?> = parse(file.readText())

    private fun block(space: String, address: Int, count: Int): IntArray? =
        blocks["$space:$address:$count"]

    override fun readInputRegisters(address: Int, count: Int): IntArray =
        block("i", address, count) ?: error("Snímek neobsahuje input blok $address/$count")

    override fun readHoldingRegisters(address: Int, count: Int): IntArray =
        block("h", address, count) ?: error("Snímek neobsahuje holding blok $address/$count")

    override fun readInputOrNull(address: Int, count: Int): IntArray? = block("i", address, count)

    override fun readHoldingOrNull(address: Int, count: Int): IntArray? = block("h", address, count)

    /** Minimalistický parser - snímek je plochá mapa string -> pole čísel nebo null. */
    private fun parse(json: String): Map<String, IntArray?> {
        val result = mutableMapOf<String, IntArray?>()
        val entry = Regex("\"([^\"]+)\"\\s*:\\s*(null|\\[[^]]*])")
        entry.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            result[key] = if (value == "null") {
                null
            } else {
                value.trim('[', ']')
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toIntArray()
            }
        }
        return result
    }
}

private val PY_POWER_CONTROL = mapOf(
    0 to "disable remote control", 1 to "enable power control",
    2 to "enable electric quantity control", 3 to "enable SOC target control",
)

private fun printLikePython(d: SolaxData) {
    val dev = d.device
    println("=== ZAŘÍZENÍ ===")
    dev.serial?.let { println("Sériové číslo:  $it") }
    dev.factory?.let { println("Výrobce:        $it") }
    dev.model?.let { println("Model:          $it") }
    dev.firmwareDsp?.let { println("Firmware DSP:   $it   (HW ${dev.hardwareDsp})") }
    dev.firmwareArm?.let { println("Firmware ARM:   $it   (bootloader ${dev.armBootloader})") }
    dev.firmwareModbus?.let { println("Firmware Modbus:$it") }
    dev.inverterTime?.let { println("Čas měniče:     $it") }
    dev.ratedPowerW?.let { println("Typ měniče:     $it W") }

    val inv = d.inverter
    println()
    println("=== MĚNIČ ===")
    println("RunMode:    ${inv.runMode} (${inv.runModeText})")
    println("Grid:       ${if (inv.onGrid) "OnGrid" else "OffGrid"}")
    println("Frekvence:  %.2f Hz".format(inv.frequencyHz))
    println("Teplota:    ${inv.radiatorTempC} °C (radiator)")
    println("Reg 0x66:   %+d   (dokumentováno jako BusVolt, neodpovídá)".format(inv.reg0x66))
    println("Lock state: ${inv.lockState} (${inv.lockStateText})")

    val pv = d.pv
    println()
    println("=== FOTOVOLTAIKA ===")
    println("PV1:        %.1f V × %.1f A = %d W".format(pv.v1, pv.a1, pv.w1))
    println("PV2:        %.1f V × %.1f A = %d W".format(pv.v2, pv.a2, pv.w2))
    println("PV celkem:  ${pv.totalW} W")

    println()
    println("=== SÍŤ (3 fáze) ===")
    d.grid.phases.forEach { p ->
        println(
            "%s: %6.1f V  %+5.1f A  %+6d W  %.2f Hz"
                .format(p.label, p.voltageV, p.currentA, p.powerW, p.frequencyHz),
        )
    }
    println(
        "Výstupní napětí měniče: %.1f / %.1f / %.1f V"
            .format(d.grid.phases[0].invVoltV, d.grid.phases[1].invVoltV, d.grid.phases[2].invVoltV),
    )
    println("Feedin (CT/meter): %+d W   (+ export, - import)".format(d.grid.feedinTotalW))
    println(
        "  po fázích: L1 %+d W   L2 %+d W   L3 %+d W"
            .format(d.grid.phases[0].feedinW, d.grid.phases[1].feedinW, d.grid.phases[2].feedinW),
    )

    d.meter?.let { m ->
        println()
        println("=== ELEKTROMĚR ===")
        println(
            "Komunikace: Meter1 %s, Meter2 %s"
                .format(if (m.meter1Ok) "OK" else "CHYBA", if (m.meter2Ok) "OK" else "nepřipojen"),
        )
        println("Frekvence:  %.2f Hz".format(m.frequencyHz))
        println(
            "Činný výkon:  celkem %+d W   L1 %+d  L2 %+d  L3 %+d W"
                .format(m.activeTotalW, m.activePhasesW[0], m.activePhasesW[1], m.activePhasesW[2]),
        )
        println(
            "Jalový výkon: celkem %+d var   L1 %+d  L2 %+d  L3 %+d var"
                .format(m.reactiveTotalVar, m.reactivePhasesVar[0], m.reactivePhasesVar[1], m.reactivePhasesVar[2]),
        )
        println(
            "Účiník:     celkem %+.2f   L1 %+.2f  L2 %+.2f  L3 %+.2f"
                .format(m.powerFactorTotal, m.powerFactorPhases[0], m.powerFactorPhases[1], m.powerFactorPhases[2]),
        )
        println(
            "Napětí:     průměr %.1f V   L1 %.1f  L2 %.1f  L3 %.1f V"
                .format(m.voltageAvgV, m.voltagePhasesV[0], m.voltagePhasesV[1], m.voltagePhasesV[2]),
        )
        println(
            "Proud:      součet %+.1f A   L1 %+.1f  L2 %+.1f  L3 %+.1f A"
                .format(m.currentTotalA, m.currentPhasesA[0], m.currentPhasesA[1], m.currentPhasesA[2]),
        )
    }

    val b = d.battery
    println()
    println("=== BATERIE ===")
    println("Napětí:     %.1f V".format(b.voltageV))
    println("Proud:      %+.1f A".format(b.currentA))
    println("Výkon:      %+d W   (+ nabíjí, - vybíjí)".format(b.powerW))
    println("SoC:        ${b.soc} %")
    println("Teplota:    ${b.tempC} °C")
    println("BDC stav:   ${b.bdcState} (${BDC_STATE[b.bdcState] ?: "?"})")
    println("BMS:        ${if (b.bmsConnected) "Connected" else "Disconnected"}")
    println("MPPT count: ${b.mpptCount}")
    println("BMS Charge MaxCurrent:    %.1f A".format(b.chargeMaxA))
    println("BMS Discharge MaxCurrent: %.1f A".format(b.dischargeMaxA))
    println("BMS Battery Capacity:     ${b.capacityWh} Wh   (~${b.moduleCount}× modul T58)")
    b.chargeableWh?.let { println("Lze ještě nabít:   $it Wh") }
    b.dischargeableWh?.let { println("Lze ještě vybít:   $it Wh") }
    b.bmsRelayOn?.let { println("BMS relé:          ${if (it) "ON" else "OFF"}") }
    b.forceCharge?.let { println("Force charge flag: ${if (it) "ANO" else "ne"}") }
    // Aplikace zobrazuje české popisky, referenční skript anglické z PDF - kvůli
    // porovnatelnosti diffem tu mapujeme na znění skriptu
    b.powerControl?.let { println("ModbusPowerControl: $it (${PY_POWER_CONTROL[it] ?: "?"})") }

    d.bms?.let { bms ->
        println()
        println("=== BMS DETAIL (články) ===")
        println("Cell V max:  %.3f V".format(bms.cellVMax))
        println("Cell V min:  %.3f V".format(bms.cellVMin))
        println("Cell delta:  ${bms.cellDeltaMv} mV   (zdravé < 50 mV, varování > 150 mV)")
        println("Bat tem max: %.1f °C".format(bms.tempMaxC))
        println("Bat tem min: %.1f °C".format(bms.tempMinC))
        println("BMS SoC:     ${if (bms.socReported) "${bms.soc} %" else "neplní se (viz SoC výše)"}")
        println("BMS SoH:     ${bms.soh} %")
    }

    val e = d.energy
    println()
    println("=== ENERGIE ===")
    println("Výroba FV dnes:          %.1f kWh".format(e.solarTodayKwh))
    println("Výroba FV celkem:        %.1f kWh".format(e.solarTotalKwh))
    println("Dnes nabito do baterie:  %.1f kWh".format(e.batteryChargedTodayKwh))
    println("Dnes vybito z baterie:   %.1f kWh".format(e.batteryDischargedTodayKwh))
    println("Celkem nabito (lifetime): %.1f kWh".format(e.batteryChargedTotalKwh))
    println("Celkem vybito (lifetime): %.1f kWh".format(e.batteryDischargedTotalKwh))
    println("Dnes do sítě (meter):    %.2f kWh".format(e.toGridTodayKwh))
    println("Dnes ze sítě (meter):    %.2f kWh".format(e.fromGridTodayKwh))
    println("Celkem do sítě (meter):  %.2f kWh".format(e.toGridTotalKwh))
    println("Celkem ze sítě (meter):  %.2f kWh".format(e.fromGridTotalKwh))
    println("Dnes na AC portu:        %.1f kWh".format(e.acPortTodayKwh))
    println("Celkem na AC portu:      %.1f kWh".format(e.acPortTotalKwh))
    println("EchargeToday:            %.1f kWh".format(e.eChargeTodayKwh))
    println("EchargeTotal:            %.1f kWh".format(e.eChargeTotalKwh))
    println("Off-grid výroba dnes:    %.1f kWh".format(e.offGridTodayKwh))
    println("Off-grid výroba celkem:  %.1f kWh".format(e.offGridTotalKwh))
    println("Provoz on-grid:          %.1f h".format(e.onGridHours))
    println("Provoz off-grid:         %.1f h".format(e.offGridHours))

    val f = d.faults
    println()
    println("=== CHYBY A ALARMY ===")
    println("PCSMajorFault:     0x%04X".format(f.pcsMajor))
    println("BatteryMajorFault: 0x%04X".format(f.batteryMajor))
    println("InvFaultMessage:   0x%08X".format(f.invFault))
    println("Mgr FaultMessage:  0x%04X".format(f.mgrFault))
    println("BMS FaultMessage:  0x%08X".format(f.bmsFault))
    if (f.isClean) {
        println("Stav: bez chyb")
    } else {
        f.inverterFaults.forEach { println("  [MĚNIČ]   $it") }
        f.managerFaults.forEach { println("  [MANAGER] $it") }
        f.bmsFaults.forEach { println("  [BMS]     $it") }
    }

    d.settings?.let { s ->
        println()
        println("=== NASTAVENÍ ===")
        println("SolarChargerUseMode:      ${s.useMode} (${USE_MODE[s.useMode] ?: "?"})")
        println("Manual mode:              ${s.manualMode} (${MANUAL_MODE[s.manualMode] ?: "?"})")
        println("Battery type:             ${if (s.lithium) "Lithium" else "Lead Acid"}")
        println("SelfUse Discharge MinSoC: ${s.selfUseMinSoc} %")
        println("NightCharge enabled:      ${if (s.nightChargeEnabled) "yes" else "no"}")
        println("NightCharge target SoC:   ${s.nightChargeSoc} %")
        println("Feedin  NightCharge/MinSoC: ${s.feedinNightSoc} % / ${s.feedinMinSoc} %")
        println("BackUp  NightCharge/MinSoC: ${s.backupNightSoc} % / ${s.backupMinSoc} %")
        s.epsRestartSoc?.let { println("EPS restart SoC:          $it %") }
        s.offGridMinSoc?.let { println("Off-grid MinSoC:          $it %") }
    }

    if (d.windows.isNotEmpty()) {
        println()
        println("=== ČASOVÁ OKNA ===")
        println("Nabíjení 1:  ${d.windows[0].from} - ${d.windows[0].to}")
        println("Vybíjení 1:  ${d.windows[1].from} - ${d.windows[1].to}")
        println("Perioda 2:   ${if (d.windows[2].enabled) "zapnuta" else "vypnuta"}")
        println("Nabíjení 2:  ${d.windows[2].from} - ${d.windows[2].to}")
        println("Vybíjení 2:  ${d.windows[3].from} - ${d.windows[3].to}")
    }

    if (d.unsupported.isNotEmpty()) {
        println()
        println("=== NEPODPOROVANÉ BLOKY ===")
        d.unsupported.forEach { println("  $it") }
    }
}

/** Klíčové hodnoty nabíječky, ve stejném pořadí a formátu jako řádky read_hac.py. */
private fun printHac(host: String) {
    val d = ModbusTcpClient(host, 502, 1, 5000).use { c ->
        c.connect()
        HacReader(c).read()
    }
    val v = d.device
    val s = d.status
    val ch = d.charging
    val st = d.settings
    val p = d.parallel
    println("SN:         ${v.serial}   (WiFi modul ${v.wifiModule})")
    println("Model:      ${v.powerRatingText}, ${if (v.threePhase) "třífázová" else "jednofázová"}, scéna ${v.sceneText}")
    println("Firmware:   %.2f   modul %.2f, Modbus protokol %.2f".format(v.firmware, v.moduleFirmware, v.protocolVersion))
    println("Modbus adr: ${v.modbusAddress}")
    println("Teplota:    ${v.tempC} °C")
    println("RunMode:    ${s.runMode} (${s.runModeText})")
    println("Porucha:    ${if (s.faults.isEmpty()) "OK" else s.faults.joinToString(", ")}")
    println("CP napětí:  %.2f V".format(s.cpVoltageV))
    println("PWM:        %.1f %%".format(s.pwmPercent))
    println("Fáze:       ${s.phaseText}")
    ch.phases.forEach { println("${it.label}: %6.2f V  %5.2f A  %5d W  %.2f Hz".format(it.voltageV, it.currentA, it.powerW, it.frequencyHz)) }
    println("Výkon celkem:     ${ch.totalW} W")
    println("Tato relace:      %.1f kWh, ${ch.sessionMinutes} min".format(ch.sessionKwh))
    println("Nabito celkem:    %.1f kWh".format(ch.totalKwh))
    println("Výkon:      L1 %+d W   L2 %+d W   L3 %+d W   celkem %+d W".format(d.grid.phasesW[0], d.grid.phasesW[1], d.grid.phasesW[2], d.grid.totalW))
    println("Režim:            ${st.chargeModeText}")
    println("Nabíjecí proud:   %.2f A   (min %.2f A, max %.2f A)".format(st.currentA, st.minCurrentA, st.maxCurrentA))
    println("Hlavní jistič:    ${st.mainBreakerA} A")
    println("Alarm napětí:     ${st.alarmOverV} / ${st.alarmUnderV} V")
    println("Zdroj dat sítě:   ${st.dataSourceText}")
    println("Role:             ${p.roleText}   (0x642 = 0x%04X)".format(p.raw))
    println("Počet secondary:  ${p.secondaryCount}")
    println("Chyby paralelu:   0x%08X".format(p.errors))
}
