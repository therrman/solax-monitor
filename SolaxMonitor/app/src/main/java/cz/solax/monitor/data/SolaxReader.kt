package cz.solax.monitor.data

import cz.solax.monitor.modbus.RegisterSource

/**
 * Přečte a dekóduje kompletní stav měniče. Port read_solax.py.
 *
 * Bloky jsou slučované, ať je requestů co nejmíň (7 místo 12). Volitelné bloky mají
 * fallback nebo se jen vynechají - starší firmware je nemusí mít a nesmí kvůli tomu
 * spadnout celé čtení.
 */
class SolaxReader(private val client: RegisterSource) {

    /** SN, výrobce a model se nemění, stačí přečíst jednou za spojení. */
    private var idBlock: IntArray? = null
    private var idBlockTried = false

    fun read(): SolaxData {
        val unsupported = mutableListOf<String>()

        // Input registry (0x04), živá měření. Limit 125 registrů na request,
        // takže 0x00-0x9F čteme ve dvou blocích.
        val ir = client.readInputRegisters(0x00, 0x60) + client.readInputRegisters(0x60, 0x40)

        // 0xB8-0xD0 jedním čtením: stav meteru, BMS detail i detail elektroměru.
        // Když měnič blok elektroměru nemá, spadne celý request - proto fallback.
        val irM = client.readInputOrNull(0xB8, 0x19)
        if (irM == null) unsupported += "detail elektroměru (0xC0-0xD0)"
        val bmsRegs = irM?.copyOfRange(2, 8) ?: client.readInputOrNull(0xBA, 6)
        if (bmsRegs == null) unsupported += "BMS detail článků (0xBA-0xBF)"

        val irRc = client.readInputOrNull(0x100, 0x21)
        if (irRc == null) unsupported += "využitelná kapacita ve Wh (0x100-0x120)"

        // Holding 0x7D-0xBA jedním čtením: firmware, RTC, režimy, časová okna, typ měniče.
        var hrBase = 0x7D
        var hr = client.readHoldingOrNull(0x7D, 0x3E)
        if (hr == null) {
            hrBase = 0x8B
            hr = client.readHoldingOrNull(0x8B, 0x15)
            if (hr != null) unsupported += "firmware a typ měniče (0x7D-0xBA), použit užší blok"
        }
        if (hr == null) unsupported += "nastavení a časová okna (0x8B-0x9F)"

        if (!idBlockTried) {
            idBlock = client.readHoldingOrNull(0x00, 0x1C)
            idBlockTried = true
            if (idBlock == null) unsupported += "sériové číslo a model (0x00-0x1B)"
        }

        val h: (Int) -> Int? = { addr -> hr?.getOrNull(addr - hrBase) }
        val m: (Int) -> Int = { addr -> irM!![addr - 0xB8] }
        val rc: (Int) -> Int = { addr -> irRc!![addr - 0x100] }

        val id = idBlock
        val device = DeviceInfo(
            serial = id?.let { chars14(it, 0x00, 7) },
            factory = id?.let { chars14(it, 0x07, 7) },
            model = id?.let { chars14(it, 0x0E, 7) },
            firmwareDsp = h(0x7D)?.let { "${h(0x7F)}.$it" },
            hardwareDsp = h(0x7E),
            firmwareArm = h(0x83)?.let { "${h(0x80)}.$it" },
            armBootloader = h(0x84),
            firmwareModbus = h(0x82),
            ratedPowerW = h(0xBA),
            inverterTime = h(0x85)?.let {
                "20%02d-%02d-%02d %02d:%02d:%02d".format(
                    h(0x8A), h(0x89), h(0x88), h(0x87), h(0x86), h(0x85),
                )
            },
        )

        val inverter = InverterState(
            runMode = ir[0x09],
            onGrid = ir[0x1A] == 0,
            frequencyHz = ir[0x07] / 100.0,
            radiatorTempC = s16(ir[0x08]),
            // PDF vede 0x66 jako BusVolt 0.1V, ale hodnota je záporná při nabíjení
            // baterie a kolem +119 v klidu - napětí DC busu to není. Necháváme syrovou.
            reg0x66 = s16(ir[0x66]),
            lockState = ir[0x54],
        )

        val pv = PvState(
            v1 = ir[0x03] / 10.0, a1 = ir[0x05] / 10.0, w1 = ir[0x0A],
            v2 = ir[0x04] / 10.0, a2 = ir[0x06] / 10.0, w2 = ir[0x0B],
        )

        val grid = GridState(
            phases = listOf(
                Triple(0x6A, 0x9C, 0x82), Triple(0x6E, 0x9D, 0x84), Triple(0x72, 0x9E, 0x86),
            ).mapIndexed { i, (base, invVolt, feedin) ->
                Phase(
                    label = "L${i + 1}",
                    voltageV = ir[base] / 10.0,
                    currentA = s16(ir[base + 1]) / 10.0,
                    powerW = s16(ir[base + 2]),
                    frequencyHz = ir[base + 3] / 100.0,
                    invVoltV = ir[invVolt] / 10.0,
                    feedinW = s32le(ir[feedin], ir[feedin + 1]),
                )
            },
            feedinTotalW = s32le(ir[0x46], ir[0x47]),
        )

        val meter = irM?.let {
            MeterState(
                meter1Ok = m(0xB8) == 1,
                meter2Ok = m(0xB9) == 1,
                frequencyHz = m(0xC8) / 100.0,
                voltageTotalV = m(0xC9) / 10.0,
                voltagePhasesV = listOf(m(0xCA), m(0xCB), m(0xCC)).map { v -> v / 10.0 },
                currentTotalA = s16(m(0xCD)) / 10.0,
                currentPhasesA = listOf(m(0xCE), m(0xCF), m(0xD0)).map { v -> s16(v) / 10.0 },
                reactiveTotalVar = s16(m(0xC0)),
                reactivePhasesVar = listOf(m(0xC1), m(0xC2), m(0xC3)).map { v -> s16(v) },
                powerFactorTotal = s16(m(0xC4)) / 100.0,
                powerFactorPhases = listOf(m(0xC5), m(0xC6), m(0xC7)).map { v -> s16(v) / 100.0 },
            )
        }

        val battery = BatteryState(
            voltageV = ir[0x14] / 10.0,
            currentA = s16(ir[0x15]) / 10.0,
            powerW = s16(ir[0x16]),
            soc = ir[0x1C],
            tempC = s16(ir[0x18]),
            bdcState = ir[0x19],
            bmsConnected = ir[0x17] == 1,
            mpptCount = ir[0x1B],
            chargeMaxA = ir[0x24] / 10.0,
            dischargeMaxA = ir[0x25] / 10.0,
            capacityWh = ir[0x26],
            chargeableWh = irRc?.let { u32le(rc(0x116), rc(0x117)) },
            dischargeableWh = irRc?.let { u32le(rc(0x118), rc(0x119)) },
            bmsRelayOn = irRc?.let { rc(0x120) == 1 },
            forceCharge = irRc?.let { rc(0x11F) == 1 },
            powerControl = irRc?.let { rc(0x100) },
        )

        val bms = bmsRegs?.let {
            BmsDetail(
                tempMaxC = s16(it[0]) / 10.0,
                tempMinC = s16(it[1]) / 10.0,
                cellVMax = it[2] / 1000.0,
                cellVMin = it[3] / 1000.0,
                soc = it[4],
                soh = it[5],
            )
        }

        val energy = EnergyState(
            solarTodayKwh = ir[0x96] / 10.0,
            solarTotalKwh = u32le(ir[0x94], ir[0x95]) / 10.0,
            batteryChargedTodayKwh = ir[0x23] / 10.0,
            batteryDischargedTodayKwh = ir[0x20] / 10.0,
            batteryChargedTotalKwh = u32le(ir[0x21], ir[0x22]) / 10.0,
            batteryDischargedTotalKwh = u32le(ir[0x1D], ir[0x1E]) / 10.0,
            toGridTodayKwh = u32le(ir[0x98], ir[0x99]) / 100.0,
            fromGridTodayKwh = u32le(ir[0x9A], ir[0x9B]) / 100.0,
            toGridTotalKwh = u32le(ir[0x48], ir[0x49]) / 100.0,
            fromGridTotalKwh = u32le(ir[0x4A], ir[0x4B]) / 100.0,
            acPortTodayKwh = ir[0x50] / 10.0,
            acPortTotalKwh = u32le(ir[0x52], ir[0x53]) / 10.0,
            eChargeTodayKwh = ir[0x91] / 10.0,
            eChargeTotalKwh = u32le(ir[0x92], ir[0x93]) / 10.0,
            offGridTodayKwh = ir[0x90] / 10.0,
            offGridTotalKwh = u32le(ir[0x8E], ir[0x8F]) / 10.0,
            onGridHours = u32le(ir[0x88], ir[0x89]) / 10.0,
            offGridHours = u32le(ir[0x8A], ir[0x8B]) / 10.0,
        )

        val faults = FaultState(
            pcsMajor = ir[0x3E],
            batteryMajor = ir[0x3F],
            invFault = u32le(ir[0x40], ir[0x41]),
            mgrFault = ir[0x43],
            bmsFault = u32le(ir[0x44], ir[0x45]),
        )

        val settings = h(0x8B)?.let {
            SettingsState(
                useMode = it,
                manualMode = h(0x8C)!!,
                lithium = h(0x8D) == 1,
                selfUseMinSoc = hi(h(0x93)!!),
                nightChargeEnabled = lo(h(0x93)!!) != 0,
                nightChargeSoc = h(0x94)!!,
                feedinNightSoc = hi(h(0x95)!!),
                feedinMinSoc = lo(h(0x95)!!),
                backupNightSoc = hi(h(0x96)!!),
                backupMinSoc = lo(h(0x96)!!),
                epsRestartSoc = h(0xA0),
                offGridMinSoc = h(0xB8),
            )
        }

        val period2On = h(0x9B)?.let { it != 0 } ?: false
        val windows = if (h(0x97) == null) emptyList() else listOf(
            TimeWindow("Nabíjení 1", timeOfDay(h(0x97)!!), timeOfDay(h(0x98)!!)),
            TimeWindow("Vybíjení 1", timeOfDay(h(0x99)!!), timeOfDay(h(0x9A)!!)),
            TimeWindow("Nabíjení 2", timeOfDay(h(0x9C)!!), timeOfDay(h(0x9D)!!), period2On),
            TimeWindow("Vybíjení 2", timeOfDay(h(0x9E)!!), timeOfDay(h(0x9F)!!), period2On),
        )

        return SolaxData(
            device = device,
            inverter = inverter,
            pv = pv,
            grid = grid,
            meter = meter,
            battery = battery,
            bms = bms,
            energy = energy,
            faults = faults,
            settings = settings,
            windows = windows,
            unsupported = unsupported,
        )
    }
}
