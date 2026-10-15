package cz.solax.monitor.data

import cz.solax.monitor.modbus.RegisterSource

/**
 * Přečte a dekóduje stav jedné nabíječky X3-HAC-22P. Port read_hac.py.
 *
 * Protokol nabíječky vyžaduje alespoň 1 s mezi dotazy, proto jedno čtení trvá ~3 s.
 * [pause] je injektovatelné, aby ho ověřovací modul mohl při přehrávání nahraných
 * registrů přeskočit.
 */
class HacReader(
    private val client: RegisterSource,
    private val pause: () -> Unit = { Thread.sleep(MIN_REQUEST_GAP_MS) },
) {

    fun read(): HacData {
        val ir = client.readInputRegisters(0x00, 0x43)
        pause()
        // Verze modulu a protokolu jsou jen informativní, chybějící blok čtení nerozbije.
        val ver = client.readInputOrNull(0x64, 0x15)
        pause()
        val hr = client.readHoldingRegisters(0x600, 0x70)

        val h: (Int) -> Int = { addr -> hr[addr - 0x600] }

        val device = HacDevice(
            serial = chars14(hr, 0, 7),
            wifiModule = chars14(hr, 7, 5),
            powerRating = ir[0x21],
            threePhase = ir[0x22] == 1,
            scene = ir[0x23],
            firmware = ir[0x25] / 100.0,
            moduleFirmware = ver?.let { it[0x71 - 0x64] / 100.0 },
            protocolVersion = ver?.let { it[0x78 - 0x64] / 100.0 },
            networkConnected = ir[0x26] == 1,
            signalPercent = ir[0x27],
            modbusAddress = h(0x640),
            clock = "%02d.%02d.20%02d %02d:%02d:%02d".format(
                h(0x621), h(0x622), h(0x623), h(0x620), h(0x61F), h(0x61E),
            ),
            tempC = s16(ir[0x1C]),
        )

        val status = HacStatus(
            runMode = ir[0x1D],
            fault = u32le(ir[0x1E], ir[0x1F]),
            cpVoltageV = ir[0x1A] / 100.0,
            pwmPercent = ir[0x1B] / 10.0,
            phase = ir[0x28],
            locked = ir[0x2D] == 1,
            breaker = ir[0x2E],
            chargingDisabled = ir[0x30] == 1,
        )

        val start = ir.copyOfRange(0x31, 0x37)
        val charging = HacCharging(
            phases = listOf("L1", "L2", "L3").mapIndexed { i, label ->
                HacPhase(
                    label = label,
                    voltageV = ir[i] / 100.0,
                    currentA = s16(ir[4 + i]) / 100.0,
                    powerW = s16(ir[8 + i]),
                    frequencyHz = ir[0x0C + i] / 100.0,
                )
            },
            totalW = s16(ir[0x0B]),
            sessionKwh = ir[0x0F] / 10.0,
            sessionMinutes = u32le(ir[0x2B], ir[0x2C]) / 60,
            sessionStart = if (start.any { it != 0 }) {
                "%02d.%02d.20%02d %02d:%02d:%02d".format(start[3], start[4], start[5], start[2], start[1], start[0])
            } else {
                null
            },
            totalKwh = u32le(ir[0x10], ir[0x11]) / 10.0,
        )

        val grid = HacGrid(
            phasesW = listOf(s16(ir[0x15]), s16(ir[0x16]), s16(ir[0x17])),
            totalW = s16(ir[0x18]),
        )

        val settings = HacSettings(
            chargeMode = h(0x669),
            ecoGear = h(0x60E),
            greenGear = h(0x60F),
            boostMode = h(0x613),
            startMode = h(0x610),
            currentA = h(0x628) / 100.0,
            minCurrentA = h(0x63F) / 100.0,
            maxCurrentA = h(0x668) / 100.0,
            mainBreakerA = h(0x614),
            breakerGuard = h(0x664) == 1,
            alarmOverV = h(0x611),
            alarmUnderV = h(0x612),
            dataSource = h(0x60C),
        )

        val parallel = HacParallel(
            raw = h(0x642),
            secondaryCount = h(0x66C),
            ratio = h(0x66D),
            errors = u32le(h(0x66A), h(0x66B)),
            secondaryRunMode = ir[0x39],
            secondaryCurrentsA = listOf(s16(ir[0x3A]) / 100.0, s16(ir[0x3B]) / 100.0, s16(ir[0x3C]) / 100.0),
            secondaryPowerW = s16(ir[0x40]),
        )

        return HacData(device, status, charging, grid, settings, parallel)
    }

    companion object {
        const val MIN_REQUEST_GAP_MS = 1000L
    }
}
