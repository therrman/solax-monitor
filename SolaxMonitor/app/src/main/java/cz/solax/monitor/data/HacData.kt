package cz.solax.monitor.data

/** Kompletní snímek jedné nabíječky X3-HAC-22P. */
data class HacData(
    val device: HacDevice,
    val status: HacStatus,
    val charging: HacCharging,
    val grid: HacGrid,
    val settings: HacSettings,
    val parallel: HacParallel,
)

data class HacDevice(
    val serial: String?,
    val wifiModule: String?,
    val powerRating: Int,
    val threePhase: Boolean,
    val scene: Int,
    val firmware: Double,
    val moduleFirmware: Double?,
    val protocolVersion: Double?,
    val networkConnected: Boolean,
    val signalPercent: Int,
    val modbusAddress: Int,
    val clock: String,
    val tempC: Int,
) {
    val powerRatingText: String get() = HAC_POWER_RATING[powerRating] ?: "neznámý ($powerRating)"
    val sceneText: String get() = HAC_SCENE[scene] ?: "neznámá ($scene)"
}

data class HacStatus(
    val runMode: Int,
    val fault: Long,
    val cpVoltageV: Double,
    val pwmPercent: Double,
    val phase: Int,
    val locked: Boolean,
    val breaker: Int,
    val chargingDisabled: Boolean,
) {
    val runModeText: String get() = HAC_RUN_MODES[runMode] ?: "neznámý ($runMode)"
    val phaseText: String get() = HAC_CHARGE_PHASE[phase] ?: "$phase"
    val breakerText: String get() = HAC_BREAKER[breaker] ?: "$breaker"
    val faults: List<String> get() = decodeHacFaults(fault)
    val isCharging: Boolean get() = runMode == 2
    val isFaulted: Boolean get() = runMode == 4 || fault != 0L
}

data class HacPhase(val label: String, val voltageV: Double, val currentA: Double, val powerW: Int, val frequencyHz: Double)

data class HacCharging(
    val phases: List<HacPhase>,
    val totalW: Int,
    val sessionKwh: Double,
    val sessionMinutes: Long,
    /** null = relace nezačala (registr začátku je nulový). */
    val sessionStart: String?,
    val totalKwh: Double,
)

/** Výkon sítě, který nabíječka dostává od střídače (+ export, - import). */
data class HacGrid(val phasesW: List<Int>, val totalW: Int)

data class HacSettings(
    val chargeMode: Int,
    val ecoGear: Int,
    val greenGear: Int,
    val boostMode: Int,
    val startMode: Int,
    val currentA: Double,
    val minCurrentA: Double,
    val maxCurrentA: Double,
    val mainBreakerA: Int,
    val breakerGuard: Boolean,
    val alarmOverV: Int,
    val alarmUnderV: Int,
    val dataSource: Int,
) {
    val chargeModeText: String get() = HAC_CHARGE_MODE[chargeMode] ?: "neznámý ($chargeMode)"
    val ecoGearText: String get() = HAC_ECO_GEAR[ecoGear] ?: "$ecoGear"
    val greenGearText: String get() = HAC_GREEN_GEAR[greenGear] ?: "$greenGear"
    val boostModeText: String get() = HAC_BOOST_MODE[boostMode] ?: "$boostMode"
    val startModeText: String get() = HAC_START_MODE[startMode] ?: "$startMode"

    /** Ověřeno jen 1 = střídač. Hodnoty pro CT a elektroměr zatím neznáme. */
    val dataSourceText: String get() = if (dataSource == 1) "střídač" else "$dataSource (CT/elektroměr? neověřeno)"
}

data class HacParallel(
    /** Syrový holding 0x642, role je spodní bajt. */
    val raw: Int,
    val secondaryCount: Int,
    val ratio: Int,
    val errors: Long,
    val secondaryRunMode: Int,
    val secondaryCurrentsA: List<Double>,
    val secondaryPowerW: Int,
) {
    val role: Int get() = lo(raw)
    val roleText: String get() = HAC_PARALLEL_ROLE[role] ?: "?"
    val isPrimary: Boolean get() = role == 1
    val ratioText: String get() = "${hi(ratio)}:${lo(ratio)}"
    val secondaryRunModeText: String get() = HAC_RUN_MODES[secondaryRunMode] ?: "$secondaryRunMode"
}
