package cz.solax.monitor.data

/**
 * Dekódovací tabulky z protokolu Hybrid X1/X3-G4 Modbus V3.36 (původně V3.21).
 * Přeneseno 1:1 z read_solax.py, ověřeno proti živému X3-Hybrid G4.
 */

val RUN_MODES = mapOf(
    0 to "Waiting", 1 to "Checking", 2 to "Normal", 3 to "Fault", 4 to "Permanent Fault",
    5 to "Update", 6 to "Off-grid waiting", 7 to "Off-grid", 8 to "Self Testing",
    9 to "Idle", 10 to "Standby",
)

val USE_MODE = mapOf(
    0 to "Self use", 1 to "Feedin Priority", 2 to "Back up", 3 to "Manual",
    4 to "Peak Shaving", 5 to "TOU Mode",
)

val MANUAL_MODE = mapOf(0 to "Stop", 1 to "Force charge", 2 to "Force discharge")

val BDC_STATE = mapOf(0 to "vybíjí", 1 to "nabíjí", 2 to "stop")

/** PDF zná jen 0 a 1, ale X3-Hybrid G4 vrací i hodnotu 2. */
val LOCK_STATE = mapOf(0 to "locked", 1 to "unlocked")

val POWER_CONTROL = mapOf(
    0 to "vypnuto", 1 to "power control",
    2 to "electric quantity control", 3 to "SOC target control",
    4 to "push power (+/-)", 5 to "push power (zero)",
    6 to "self consume charge/discharge", 7 to "self consume charge only",
)

/** Table 2-3 Inverter error code (X3) - InvFaultMessage, input 0x40 LSB / 0x41 MSB. */
val INV_FAULT_BITS = listOf(
    "TZ Protect Fault", "Grid Lost Fault", "Grid Volt Fault", "Grid Freq Fault",
    "PV Volt Fault", "Bus Volt Fault", "Bat Volt Fault", "AC10mins Volt Fault",
    "DCI OCP Fault", "DCV OCP Fault", "SW OCP Fault", "RC OCP Fault",
    "Isolation Fault", "Temp Over Fault", "BatConnDir Fault", "Off-grid Overload",
    "Overload", "Bat Power Low", "BMS Lost", "Fan Fault",
    "Low Temp Fault", "Parallel Fault", "Hard Limit Fault", "INV Volt Sample Fault",
    "Inner Comm Fault", "INV EEPROM Fault", "RCD Fault", "Grid Relay Fault",
    "Off-grid Relay Fault", "PV ConnDir Fault", "Charger Relay Fault", "Earth Relay Fault",
)

/** Table 2-5 Manager error code - Mgr FaultMessage, input 0x43. */
val MGR_FAULT_BITS = listOf(
    "Power Type Fault", "Port OC Warning", "Mgr EEPROM Fault", "Reserve3",
    "NTC Sample Invalid", "Bat Temp Low", "Bat Temp High", "Reserve7",
    "Reserve8", "Meter Fault", "Bypass Relay Fault", "Fan 2 Fault",
    "Reserve12", "Reserve13", "Reserve14", "Reserve15",
)

/** Table 2-6 BMS warning code - Bat_BMS_FaultMessage, input 0x44 LSB / 0x45 MSB. */
val BMS_FAULT_BITS = listOf(
    "BMS_External_Err", "BMS_Internal_Err", "BMS_OverVoltage", "BMS_LowerVoltage",
    "BMS_ChargeOCP", "BMS_DischargeOCP", "BMS_TemHigh", "BMS_TemLow",
    "BMS_CellImbalance", "BMS_Hardware_Protect", "BMS_Circuit_Fault", "BMS_ISO_Fault",
    "BMS_VolSen_Fault", "BMS_TempSen_Fault", "BMS_CurSen_Fault", "BMS_Relay_Fault",
    "BMS_Type_Unmatch", "BMS_Ver_Unmathch", "BMS_MFR_Unmathch", "BMS_SW_Unmathch",
    "BMS_M&S_Unmatch", "BMS_CR_NORespond", "BMS_SW_Protect", "BMS_536_Fault",
    "BMS_SelfcheckErr", "BMS_TempdiffErr", "MS_BreakFault", "BMS_Flash_Fault",
    "BMS_Precharge_Fault", "BMS_AirSwitch_Break", "Rev30", "Rev31",
)

/** Rozsvícené bity převede na názvy. Reserve/Rev bity necháváme - ať je vidět, když se objeví. */
fun decodeBits(value: Long, names: List<String>): List<String> =
    names.filterIndexed { i, _ -> (value shr i) and 1L == 1L }
