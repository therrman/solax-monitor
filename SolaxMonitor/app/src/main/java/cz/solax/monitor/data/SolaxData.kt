package cz.solax.monitor.data

/** Kompletní snímek měniče. Nullable sekce = měnič ten blok registrů nepodporuje. */
data class SolaxData(
    val device: DeviceInfo,
    val inverter: InverterState,
    val pv: PvState,
    val grid: GridState,
    val meter: MeterState?,
    val battery: BatteryState,
    val bms: BmsDetail?,
    val energy: EnergyState,
    val faults: FaultState,
    val settings: SettingsState?,
    val windows: List<TimeWindow>,
    val unsupported: List<String>,
)

data class DeviceInfo(
    val serial: String? = null,
    val factory: String? = null,
    val model: String? = null,
    val firmwareDsp: String? = null,
    val hardwareDsp: Int? = null,
    val firmwareArm: String? = null,
    val armBootloader: Int? = null,
    val firmwareModbus: Int? = null,
    val ratedPowerW: Int? = null,
    val inverterTime: String? = null,
)

data class InverterState(
    val runMode: Int,
    val onGrid: Boolean,
    val frequencyHz: Double,
    val radiatorTempC: Int,
    /** Registr 0x66. PDF ho vede jako BusVolt 0.1V, ale tenhle firmware se tak nechová. */
    val reg0x66: Int,
    val lockState: Int,
) {
    val runModeText: String get() = RUN_MODES[runMode] ?: "neznámý ($runMode)"

    /** PDF zná jen 0 a 1, X3-Hybrid G4 vrací i 2. */
    val lockStateText: String get() = LOCK_STATE[lockState] ?: "nedokumentováno"
    val isFault: Boolean get() = runMode == 3 || runMode == 4
    val isNormal: Boolean get() = runMode == 2
}

data class PvState(
    val v1: Double, val a1: Double, val w1: Int,
    val v2: Double, val a2: Double, val w2: Int,
) {
    val totalW: Int get() = w1 + w2
}

data class Phase(
    val label: String,
    val voltageV: Double,
    val currentA: Double,
    val powerW: Int,
    val frequencyHz: Double,
    val invVoltV: Double,
    val feedinW: Int,
)

data class GridState(
    val phases: List<Phase>,
    val feedinTotalW: Int,
) {
    /** + export do sítě, - odběr ze sítě. */
    val isExporting: Boolean get() = feedinTotalW >= 0
}

data class MeterState(
    val meter1Ok: Boolean,
    val meter2Ok: Boolean,
    val frequencyHz: Double,
    val voltageTotalV: Double,
    val voltagePhasesV: List<Double>,
    val currentTotalA: Double,
    val currentPhasesA: List<Double>,
    val reactiveTotalVar: Int,
    val reactivePhasesVar: List<Int>,
    val powerFactorTotal: Double,
    val powerFactorPhases: List<Double>,
)

data class BatteryState(
    val voltageV: Double,
    val currentA: Double,
    val powerW: Int,
    val soc: Int,
    val tempC: Int,
    val bdcState: Int,
    val bmsConnected: Boolean,
    val mpptCount: Int,
    val chargeMaxA: Double,
    val dischargeMaxA: Double,
    val capacityWh: Int,
    val chargeableWh: Long? = null,
    val dischargeableWh: Long? = null,
    val bmsRelayOn: Boolean? = null,
    val forceCharge: Boolean? = null,
    val powerControl: Int? = null,
) {
    val bdcText: String get() = BDC_STATE[bdcState] ?: "neznámý ($bdcState)"
    val isCharging: Boolean get() = powerW > 0
    val isDischarging: Boolean get() = powerW < 0

    /** Subsystem_Num z holding 0x200 se na T58 toweru neplní, počet odvozujeme z kapacity. */
    val moduleCount: Int? get() = if (capacityWh >= T58_MODULE_WH) Math.round(capacityWh / T58_MODULE_WH.toFloat()) else null

    companion object {
        const val T58_MODULE_WH = 5760
    }
}

data class BmsDetail(
    val cellVMax: Double,
    val cellVMin: Double,
    val tempMaxC: Double,
    val tempMinC: Double,
    val soc: Int,
    val soh: Int,
) {
    val cellDeltaMv: Int get() = Math.round((cellVMax - cellVMin) * 1000).toInt()

    /** Prahy podle CLAUDE.md: zdravé < 50 mV, varování > 150 mV. */
    val balance: Balance
        get() = when {
            cellDeltaMv < 50 -> Balance.OK
            cellDeltaMv <= 150 -> Balance.WATCH
            else -> Balance.WARN
        }

    /** 0xBE se na některých BMS neplní. */
    val socReported: Boolean get() = soc > 0

    enum class Balance { OK, WATCH, WARN }
}

data class EnergyState(
    val solarTodayKwh: Double,
    val solarTotalKwh: Double,
    val batteryChargedTodayKwh: Double,
    val batteryDischargedTodayKwh: Double,
    val batteryChargedTotalKwh: Double,
    val batteryDischargedTotalKwh: Double,
    val toGridTodayKwh: Double,
    val fromGridTodayKwh: Double,
    val toGridTotalKwh: Double,
    val fromGridTotalKwh: Double,
    val acPortTodayKwh: Double,
    val acPortTotalKwh: Double,
    val eChargeTodayKwh: Double,
    val eChargeTotalKwh: Double,
    val offGridTodayKwh: Double,
    val offGridTotalKwh: Double,
    val onGridHours: Double,
    val offGridHours: Double,
)

data class FaultState(
    val pcsMajor: Int,
    val batteryMajor: Int,
    val invFault: Long,
    val mgrFault: Int,
    val bmsFault: Long,
) {
    val inverterFaults: List<String> get() = decodeBits(invFault, INV_FAULT_BITS)
    val managerFaults: List<String> get() = decodeBits(mgrFault.toLong(), MGR_FAULT_BITS)
    val bmsFaults: List<String> get() = decodeBits(bmsFault, BMS_FAULT_BITS)
    val all: List<Pair<String, String>>
        get() = inverterFaults.map { "MĚNIČ" to it } +
            managerFaults.map { "MANAGER" to it } +
            bmsFaults.map { "BMS" to it }
    val isClean: Boolean get() = all.isEmpty()
}

data class SettingsState(
    val useMode: Int,
    val manualMode: Int,
    val lithium: Boolean,
    val selfUseMinSoc: Int,
    val nightChargeEnabled: Boolean,
    val nightChargeSoc: Int,
    val feedinNightSoc: Int,
    val feedinMinSoc: Int,
    val backupNightSoc: Int,
    val backupMinSoc: Int,
    val epsRestartSoc: Int? = null,
    val offGridMinSoc: Int? = null,
) {
    val useModeText: String get() = USE_MODE[useMode] ?: "neznámý ($useMode)"
    val manualModeText: String get() = MANUAL_MODE[manualMode] ?: "neznámý ($manualMode)"
}

data class TimeWindow(val label: String, val from: String, val to: String, val enabled: Boolean = true) {
    val isEmpty: Boolean get() = from == "00:00" && to == "00:00"
}
