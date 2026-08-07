package cz.solax.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.solax.monitor.data.BmsDetail
import cz.solax.monitor.data.SolaxData
import kotlin.math.abs

@Composable
fun DashboardScreen(data: SolaxData, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { FaultsCard(data) }
        item { FlowCard(data) }
        item { PvCard(data) }
        item { BatteryCard(data) }
        data.bms?.let { item { BmsCard(it) } }
        item { GridCard(data) }
        data.meter?.let { item { MeterCard(it) } }
        item { InverterCard(data) }
        item { EnergyCard(data) }
        data.settings?.let { item { SettingsCard(data) } }
        if (data.windows.isNotEmpty()) item { WindowsCard(data) }
        item { DeviceCard(data) }
        if (data.unsupported.isNotEmpty()) item { UnsupportedCard(data) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ------------------------------------------------------------------ karty

@Composable
private fun FaultsCard(data: SolaxData) {
    val f = data.faults
    SectionCard(
        title = "Chyby a alarmy",
        badge = {
            if (f.isClean) Badge("BEZ CHYB", SolaxColors.Charge)
            else Badge("${f.all.size}× CHYBA", SolaxColors.Bad)
        },
    ) {
        if (f.isClean) {
            Text(
                "Měnič, manager i BMS hlásí čistý stav.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            f.all.forEach { (source, name) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Badge(source, SolaxColors.Bad)
                    Text("  $name", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(6.dp))
        ValueRow("InvFaultMessage", "0x%08X".format(f.invFault))
        ValueRow("Mgr FaultMessage", "0x%04X".format(f.mgrFault))
        ValueRow("BMS FaultMessage", "0x%08X".format(f.bmsFault))
        ValueRow("PCSMajorFault", "0x%04X".format(f.pcsMajor))
        ValueRow("BatteryMajorFault", "0x%04X".format(f.batteryMajor))
    }
}

/** Přehled toku energie: kolik teče z FV, do baterie, do sítě a do domu. */
@Composable
private fun FlowCard(data: SolaxData) {
    val pv = data.pv.totalW
    val bat = data.battery.powerW
    val grid = data.grid.feedinTotalW
    // Co nespotřebuje baterie ani síť, jde do domu
    val house = pv - bat - grid
    SectionCard("Tok energie") {
        BigValue("$pv", "W", SolaxColors.Sun, caption = "z fotovoltaiky")
        Spacer(Modifier.height(10.dp))
        ValueRow(
            "Baterie",
            "%+d W".format(bat),
            if (bat > 0) SolaxColors.Charge else if (bat < 0) SolaxColors.Discharge else SolaxColors.Muted,
        )
        ValueRow(
            if (grid >= 0) "Dodávka do sítě" else "Odběr ze sítě",
            "%+d W".format(grid),
            if (grid >= 0) SolaxColors.Export else SolaxColors.Import,
        )
        ValueRow("Spotřeba domu", "≈ %d W".format(house))
    }
}

@Composable
private fun PvCard(data: SolaxData) {
    val pv = data.pv
    SectionCard("Fotovoltaika") {
        BigValue("${pv.totalW}", "W", SolaxColors.Sun, caption = "PV1 + PV2")
        Spacer(Modifier.height(10.dp))
        PhaseHeader("String", "Napětí", "Proud", "Výkon")
        PhaseRow("PV1", "%.1f V".format(pv.v1), "%.1f A".format(pv.a1), "${pv.w1} W")
        PhaseRow("PV2", "%.1f V".format(pv.v2), "%.1f A".format(pv.a2), "${pv.w2} W")
    }
}

@Composable
private fun BatteryCard(data: SolaxData) {
    val b = data.battery
    val stateColor = when {
        b.isCharging -> SolaxColors.Charge
        b.isDischarging -> SolaxColors.Discharge
        else -> SolaxColors.Muted
    }
    val arrow = when {
        b.isCharging -> "▲ nabíjí"
        b.isDischarging -> "▼ vybíjí"
        else -> "— klid"
    }
    SectionCard(
        title = "Baterie",
        badge = {
            if (b.bmsConnected) Badge("BMS OK", SolaxColors.Charge)
            else Badge("BMS ODPOJENO", SolaxColors.Bad)
        },
    ) {
        BigValue("${b.soc}", "%", stateColor, caption = arrow)
        Spacer(Modifier.height(10.dp))
        ValueRow("Výkon", "%+d W".format(b.powerW), stateColor)
        ValueRow("Napětí", "%.1f V".format(b.voltageV))
        ValueRow("Proud", "%+.1f A".format(b.currentA))
        ValueRow("Teplota", "${b.tempC} °C")
        ValueRow("BDC stav", "${b.bdcState} (${b.bdcText})")
        ValueRow("MPPT count", "${b.mpptCount}")
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(6.dp))
        ValueRow("Kapacita", buildString {
            append("${b.capacityWh} Wh")
            b.moduleCount?.let { append("  (~${it}× T58)") }
        })
        ValueRow("BMS max nabíjecí proud", "%.1f A".format(b.chargeMaxA))
        ValueRow("BMS max vybíjecí proud", "%.1f A".format(b.dischargeMaxA))
        b.chargeableWh?.let { ValueRow("Lze ještě nabít", "$it Wh") }
        b.dischargeableWh?.let { ValueRow("Lze ještě vybít", "$it Wh") }
        b.bmsRelayOn?.let { ValueRow("BMS relé", if (it) "ON" else "OFF") }
        b.forceCharge?.let { ValueRow("Force charge", if (it) "ANO" else "ne") }
        b.powerControl?.let {
            ValueRow("Vzdálené řízení", cz.solax.monitor.data.POWER_CONTROL[it] ?: "neznámé ($it)")
        }
    }
}

@Composable
private fun BmsCard(bms: BmsDetail) {
    val (badgeText, badgeColor) = when (bms.balance) {
        BmsDetail.Balance.OK -> "VYVÁŽENO" to SolaxColors.Charge
        BmsDetail.Balance.WATCH -> "SLEDOVAT" to SolaxColors.Warn
        BmsDetail.Balance.WARN -> "ROZVÁŽENO" to SolaxColors.Bad
    }
    SectionCard(title = "BMS detail (články)", badge = { Badge(badgeText, badgeColor) }) {
        BigValue("${bms.cellDeltaMv}", "mV", badgeColor, caption = "rozdíl článků")
        Text(
            "zdravé < 50 mV · varování > 150 mV",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ValueRow("Cell V max", "%.3f V".format(bms.cellVMax))
        ValueRow("Cell V min", "%.3f V".format(bms.cellVMin))
        ValueRow("Teplota max", "%.1f °C".format(bms.tempMaxC))
        ValueRow("Teplota min", "%.1f °C".format(bms.tempMinC))
        ValueRow("BMS SoH", "${bms.soh} %")
        // 0xBE se na některých BMS neplní, pak je platné SoC jen z 0x1C
        ValueRow("BMS SoC", if (bms.socReported) "${bms.soc} %" else "neplní se")
    }
}

@Composable
private fun GridCard(data: SolaxData) {
    val g = data.grid
    SectionCard(
        title = "Síť (3 fáze)",
        badge = {
            if (data.inverter.onGrid) Badge("ON-GRID", SolaxColors.Charge)
            else Badge("OFF-GRID", SolaxColors.Warn)
        },
    ) {
        BigValue(
            "%+d".format(g.feedinTotalW),
            "W",
            if (g.isExporting) SolaxColors.Export else SolaxColors.Import,
            caption = if (g.isExporting) "dodávka do sítě" else "odběr ze sítě",
        )
        Spacer(Modifier.height(10.dp))
        PhaseHeader("Fáze", "Napětí", "Proud", "Výkon")
        g.phases.forEach { p ->
            PhaseRow(p.label, "%.1f V".format(p.voltageV), "%+.1f A".format(p.currentA), "%+d W".format(p.powerW))
        }
        Spacer(Modifier.height(8.dp))
        PhaseHeader("Fáze", "Frekvence", "Výstup INV", "Feedin")
        g.phases.forEach { p ->
            PhaseRow(
                p.label,
                "%.2f Hz".format(p.frequencyHz),
                "%.1f V".format(p.invVoltV),
                "%+d W".format(p.feedinW),
            )
        }
    }
}

@Composable
private fun MeterCard(m: cz.solax.monitor.data.MeterState) {
    SectionCard(
        title = "Elektroměr",
        badge = {
            if (m.meter1Ok) Badge("METER 1 OK", SolaxColors.Charge)
            else Badge("METER 1 CHYBA", SolaxColors.Bad)
        },
    ) {
        BigValue(
            "%+d".format(m.activeTotalW),
            "W",
            if (m.activeTotalW >= 0) SolaxColors.Export else SolaxColors.Import,
            caption = "činný výkon",
        )
        Spacer(Modifier.height(10.dp))
        ValueRow("Jalový výkon", "%+d var".format(m.reactiveTotalVar))
        ValueRow("Účiník", "%+.2f".format(m.powerFactorTotal))
        ValueRow("Frekvence", "%.2f Hz".format(m.frequencyHz))
        // 0xC9 je průměr fází, kdežto proud a jalový výkon jsou součty
        ValueRow("Napětí (průměr fází)", "%.1f V".format(m.voltageAvgV))
        ValueRow("Proud (součet fází)", "%+.1f A".format(m.currentTotalA))
        // Meter2 hlásí com error, když druhý elektroměr není nainstalovaný
        ValueRow("Meter 2", if (m.meter2Ok) "OK" else "nepřipojen")
        Spacer(Modifier.height(8.dp))
        PhaseHeader("Fáze", "Činný", "Jalový", "Účiník")
        listOf("L1", "L2", "L3").forEachIndexed { i, label ->
            PhaseRow(
                label,
                "%+d W".format(m.activePhasesW[i]),
                "%+d var".format(m.reactivePhasesVar[i]),
                "%+.2f".format(m.powerFactorPhases[i]),
            )
        }
        Spacer(Modifier.height(8.dp))
        PhaseHeader("Fáze", "", "Napětí", "Proud")
        listOf("L1", "L2", "L3").forEachIndexed { i, label ->
            PhaseRow(
                label,
                "",
                "%.1f V".format(m.voltagePhasesV[i]),
                "%+.1f A".format(m.currentPhasesA[i]),
            )
        }
    }
}

@Composable
private fun InverterCard(data: SolaxData) {
    val inv = data.inverter
    val color = when {
        inv.isFault -> SolaxColors.Bad
        inv.isNormal -> SolaxColors.Charge
        else -> SolaxColors.Warn
    }
    SectionCard(
        title = "Měnič",
        badge = { Badge(inv.runModeText.uppercase(), color) },
    ) {
        ValueRow("RunMode", "${inv.runMode} (${inv.runModeText})", color)
        ValueRow("Připojení", if (inv.onGrid) "OnGrid" else "OffGrid")
        ValueRow("Frekvence", "%.2f Hz".format(inv.frequencyHz))
        ValueRow("Teplota radiátoru", "${inv.radiatorTempC} °C")
        ValueRow("Registr 0x66", "%+d".format(inv.reg0x66))
        ValueRow("Lock state", "${inv.lockState} (${inv.lockStateText})")
    }
}

@Composable
private fun EnergyCard(data: SolaxData) {
    val e = data.energy
    SectionCard("Energie") {
        Text("Dnes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ValueRow("Výroba FV", "%.1f kWh".format(e.solarTodayKwh), SolaxColors.Sun)
        ValueRow("Nabito do baterie", "%.1f kWh".format(e.batteryChargedTodayKwh), SolaxColors.Charge)
        ValueRow("Vybito z baterie", "%.1f kWh".format(e.batteryDischargedTodayKwh), SolaxColors.Discharge)
        ValueRow("Do sítě", "%.2f kWh".format(e.toGridTodayKwh), SolaxColors.Export)
        ValueRow("Ze sítě", "%.2f kWh".format(e.fromGridTodayKwh), SolaxColors.Import)
        ValueRow("Na AC portu", "%.1f kWh".format(e.acPortTodayKwh))
        ValueRow("EchargeToday", "%.1f kWh".format(e.eChargeTodayKwh))
        ValueRow("Off-grid výroba", "%.1f kWh".format(e.offGridTodayKwh))

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("Celkem", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ValueRow("Výroba FV", "%.1f kWh".format(e.solarTotalKwh), SolaxColors.Sun)
        ValueRow("Nabito do baterie", "%.1f kWh".format(e.batteryChargedTotalKwh))
        ValueRow("Vybito z baterie", "%.1f kWh".format(e.batteryDischargedTotalKwh))
        ValueRow("Do sítě", "%.2f kWh".format(e.toGridTotalKwh))
        ValueRow("Ze sítě", "%.2f kWh".format(e.fromGridTotalKwh))
        ValueRow("Na AC portu", "%.1f kWh".format(e.acPortTotalKwh))
        ValueRow("EchargeTotal", "%.1f kWh".format(e.eChargeTotalKwh))
        ValueRow("Off-grid výroba", "%.1f kWh".format(e.offGridTotalKwh))
        ValueRow("Provoz on-grid", "%.1f h".format(e.onGridHours))
        ValueRow("Provoz off-grid", "%.1f h".format(e.offGridHours))
    }
}

@Composable
private fun SettingsCard(data: SolaxData) {
    val s = data.settings ?: return
    SectionCard("Nastavení") {
        ValueRow("Režim", "${s.useMode} (${s.useModeText})")
        ValueRow("Manual mode", "${s.manualMode} (${s.manualModeText})")
        ValueRow("Typ baterie", if (s.lithium) "Lithium" else "Lead Acid")
        ValueRow("SelfUse min SoC", "${s.selfUseMinSoc} %")
        ValueRow("Noční nabíjení", if (s.nightChargeEnabled) "zapnuto" else "vypnuto")
        ValueRow("Noční nabíjení do", "${s.nightChargeSoc} %")
        ValueRow("Feedin noční / min", "${s.feedinNightSoc} % / ${s.feedinMinSoc} %")
        ValueRow("BackUp noční / min", "${s.backupNightSoc} % / ${s.backupMinSoc} %")
        s.epsRestartSoc?.let { ValueRow("EPS restart SoC", "$it %") }
        s.offGridMinSoc?.let { ValueRow("Off-grid min SoC", "$it %") }
    }
}

@Composable
private fun WindowsCard(data: SolaxData) {
    SectionCard("Časová okna") {
        data.windows.forEach { w ->
            val text = if (w.isEmpty) "nenastaveno" else "${w.from} – ${w.to}"
            val color = when {
                !w.enabled -> SolaxColors.Muted
                w.isEmpty -> SolaxColors.Muted
                else -> MaterialTheme.colorScheme.onSurface
            }
            ValueRow(w.label + if (!w.enabled) " (vypnuto)" else "", text, color)
        }
    }
}

@Composable
private fun DeviceCard(data: SolaxData) {
    val d = data.device
    SectionCard("Zařízení") {
        d.model?.let { ValueRow("Model", it) }
        d.serial?.let { ValueRow("Sériové číslo", it) }
        d.factory?.let { ValueRow("Výrobce", it) }
        d.ratedPowerW?.let { ValueRow("Jmenovitý výkon", "$it W") }
        d.firmwareDsp?.let { ValueRow("Firmware DSP", "$it (HW ${d.hardwareDsp})") }
        d.firmwareArm?.let { ValueRow("Firmware ARM", "$it (boot ${d.armBootloader})") }
        d.firmwareModbus?.let { ValueRow("Firmware Modbus", "$it") }
        d.inverterTime?.let { ValueRow("Čas měniče", it) }
    }
}

@Composable
private fun UnsupportedCard(data: SolaxData) {
    SectionCard("Nepodporované bloky") {
        Text(
            "Tyhle registry měnič nevrací, odpovídající hodnoty proto chybí:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        data.unsupported.forEach {
            Text("• $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
