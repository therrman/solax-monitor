package cz.solax.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.solax.monitor.ChargerState
import cz.solax.monitor.data.HacData

/** Záložka s nabíječkami X3-HAC. Jen zobrazuje, do nabíječek se nezapisuje. */
@Composable
fun ChargersScreen(chargers: List<ChargerState>, refreshing: Boolean, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chargers.forEach { c ->
            val d = c.data
            if (d == null) {
                item { NoDataCard(c, refreshing) }
            } else {
                item { StatusCard(c, d) }
                item { GridFromInverterCard(d) }
                item { ChargerSettingsCard(d) }
                if (d.parallel.role != 0) item { ParallelCard(d) }
                item { ChargerDeviceCard(d) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun title(c: ChargerState, d: HacData?): String {
    val role = d?.parallel?.roleText?.takeIf { d.parallel.role != 0 }
    return "Nabíječka ${c.host}" + (role?.let { " · $it" } ?: "")
}

@Composable
private fun NoDataCard(c: ChargerState, refreshing: Boolean) {
    SectionCard(title(c, null)) {
        Text(
            if (c.error != null) "Čtení selhalo: ${c.error}" else if (refreshing) "Načítám… (první čtení trvá několik sekund)" else "Zatím žádná data.",
            fontSize = 13.sp,
            color = if (c.error != null) SolaxColors.Bad else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(c: ChargerState, d: HacData) {
    val s = d.status
    val ch = d.charging
    val color = when {
        s.isFaulted -> SolaxColors.Bad
        s.isCharging -> SolaxColors.Charge
        else -> SolaxColors.Muted
    }
    SectionCard(title(c, d), badge = { Badge(s.runModeText.uppercase(), color) }) {
        if (c.error != null) {
            Text("Poslední čtení selhalo (${c.error}), zobrazena starší data.", fontSize = 12.sp, color = SolaxColors.Warn)
            Spacer(Modifier.height(6.dp))
        }
        BigValue("${ch.totalW}", "W", color, caption = "výkon nabíjení")
        Spacer(Modifier.height(10.dp))
        PhaseHeader("Fáze", "Napětí", "Proud", "Výkon")
        ch.phases.forEach { p ->
            PhaseRow(p.label, "%.1f V".format(p.voltageV), "%.2f A".format(p.currentA), "${p.powerW} W")
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(6.dp))
        ValueRow("Tato relace", "%.1f kWh, %d min".format(ch.sessionKwh, ch.sessionMinutes))
        ch.sessionStart?.let { ValueRow("Začátek relace", it) }
        ValueRow("Nabito celkem", "%.1f kWh".format(ch.totalKwh))
        ValueRow("Frekvence", "%.2f Hz".format(ch.phases.first().frequencyHz))
        ValueRow("Porucha", if (s.faults.isEmpty()) "OK" else s.faults.joinToString(", "), if (s.faults.isEmpty()) MaterialTheme.colorScheme.onSurface else SolaxColors.Bad)
        // 12 V = bez auta, 9 V = připojeno, 6 V = nabíjí
        ValueRow("CP napětí", "%.2f V".format(s.cpVoltageV))
        ValueRow("PWM", "%.1f %%".format(s.pwmPercent))
        ValueRow("Fáze", s.phaseText)
        ValueRow("Zámek", if (s.locked) "zamčeno" else "odemčeno")
        ValueRow("Jistič", s.breakerText)
        ValueRow("Nabíjení", if (s.chargingDisabled) "zakázáno" else "povoleno")
        ValueRow("Teplota", "${d.device.tempC} °C")
    }
}

/** Výkon sítě, který nabíječka dostává od střídače. Znaménko jako u měniče: + export, - odběr. */
@Composable
private fun GridFromInverterCard(d: HacData) {
    val g = d.grid
    SectionCard("Síť (data od střídače)") {
        BigValue(
            "%+d".format(g.totalW), "W",
            if (g.totalW >= 0) SolaxColors.Export else SolaxColors.Import,
            caption = if (g.totalW >= 0) "dodávka do sítě" else "odběr ze sítě",
        )
        Spacer(Modifier.height(8.dp))
        PhaseHeader("Fáze", "", "", "Výkon")
        g.phasesW.forEachIndexed { i, w -> PhaseRow("L${i + 1}", "", "", "%+d W".format(w)) }
    }
}

@Composable
private fun ChargerSettingsCard(d: HacData) {
    val s = d.settings
    SectionCard("Nastavení") {
        ValueRow("Režim", s.chargeModeText)
        ValueRow("ECO stupeň", s.ecoGearText)
        ValueRow("Green stupeň", s.greenGearText)
        ValueRow("Boost", s.boostModeText)
        ValueRow("Start nabíjení", s.startModeText)
        ValueRow("Nabíjecí proud", "%.2f A".format(s.currentA))
        ValueRow("Min / max proud", "%.2f / %.2f A".format(s.minCurrentA, s.maxCurrentA))
        ValueRow("Hlavní jistič", "${s.mainBreakerA} A, hlídání ${if (s.breakerGuard) "zapnuto" else "vypnuto"}")
        ValueRow("Alarm napětí (přepětí / podpětí)", "${s.alarmOverV} / ${s.alarmUnderV} V")
        ValueRow("Zdroj dat sítě", s.dataSourceText)
    }
}

@Composable
private fun ParallelCard(d: HacData) {
    val p = d.parallel
    SectionCard("Paralelní provoz", badge = { Badge(p.roleText.uppercase(), SolaxColors.Export) }) {
        ValueRow("Role (0x642)", "%s (0x%04X)".format(p.roleText, p.raw))
        ValueRow("Počet secondary", "${p.secondaryCount}")
        // PowerAllocationRate dává smysl jen na primary, na secondary je 0x66D bezvýznamné
        if (p.isPrimary) {
            ValueRow("Poměr výkonu (primary:secondary)", p.ratioText)
            ValueRow("Secondary", p.secondaryRunModeText)
            ValueRow(
                "Secondary proud",
                p.secondaryCurrentsA.joinToString(" / ") { "%.2f".format(it) } + " A",
            )
            ValueRow("Secondary výkon", "${p.secondaryPowerW} W")
        }
        ValueRow("Chyby paralelu", "0x%08X".format(p.errors))
    }
}

@Composable
private fun ChargerDeviceCard(d: HacData) {
    val v = d.device
    SectionCard("Zařízení") {
        v.serial?.let { ValueRow("Sériové číslo", it) }
        v.wifiModule?.let { ValueRow("Wi-Fi modul", it) }
        ValueRow("Model", "${v.powerRatingText}, ${if (v.threePhase) "třífázová" else "jednofázová"}, scéna ${v.sceneText}")
        ValueRow("Firmware", "%.2f".format(v.firmware))
        v.moduleFirmware?.let { ValueRow("Firmware modulu", "%.2f".format(it)) }
        v.protocolVersion?.let { ValueRow("Modbus protokol", "%.2f".format(it)) }
        ValueRow("Síť", if (v.networkConnected) "připojeno, signál ${v.signalPercent} %" else "nepřipojeno")
        ValueRow("Modbus adresa", "${v.modbusAddress}")
        ValueRow("Čas nabíječky", v.clock)
    }
}
