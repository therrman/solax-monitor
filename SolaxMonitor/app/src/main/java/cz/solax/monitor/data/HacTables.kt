package cz.solax.monitor.data

/**
 * Dekódovací tabulky nabíječek X3-HAC-22P. Port read_hac.py.
 * Zdroj: X1.X3-HAC.EV.Charger.Modbus.TCP.RTU.V1.0.0, doplněno o registry z pluginu
 * homeassistant-solax-modbus (SN, paralel, bity poruch). Tam, kde se plugin nebo PDF
 * liší od hardwaru, platí hodnoty ověřené na dvou nabíječkách v paralelu.
 */

val HAC_RUN_MODES = mapOf(
    0 to "Available", 1 to "Preparing", 2 to "Charging", 3 to "Finishing", 4 to "Faulted",
    5 to "Unavailable", 6 to "Reserved", 7 to "Suspended EV", 8 to "Suspended EVSE",
    9 to "Update", 10 to "Card Activation", 11 to "Start Delay", 12 to "Charge Paused",
    13 to "Stopping", 14 to "Occupied", 15 to "Waiting for Response", 16 to "Discharging",
    17 to "Phase Switching",
)

/** FaultCode, input 0x1E LSB / 0x1F MSB. PDF bity nepopisuje, jsou z pluginu. Chybějící bity = reserve. */
val HAC_FAULT_BITS = mapOf(
    0 to "Emergency stop", 1 to "Overcurrent", 2 to "Over temperature", 3 to "PE grounding fault",
    4 to "Leakage current", 5 to "PE leakage current", 6 to "Overload protection",
    8 to "L1 overvoltage", 9 to "L1 undervoltage", 10 to "L2 overvoltage", 11 to "L2 undervoltage",
    12 to "L3 overvoltage", 13 to "L3 undervoltage", 14 to "Energy measurement IC fault",
    15 to "Meter communication fault", 16 to "Incorrect power rating",
    17 to "Control Pilot voltage anomaly", 18 to "Electronic lock fault",
    19 to "Meter selection failure", 20 to "Cover opened", 21 to "PEN relay fault",
    22 to "Parallel communication failure", 23 to "L1N relay contact sticking",
    24 to "L1N relay failure", 25 to "L2L3 relay contact sticking", 26 to "L2L3 relay failure",
    28 to "Abnormal metering",
)

/** PDF uvádí 2 = 11 kW, ale X3-HAC-22P hlásí 2, takže platí tabulka pluginu. */
val HAC_POWER_RATING = mapOf(
    0 to "7 kW", 1 to "11 kW", 2 to "22 kW", 3 to "6 kW", 4 to "4.6 kW",
    5 to "7.6 kW", 6 to "9.6 kW", 7 to "11.5 kW",
)

val HAC_SCENE = mapOf(0 to "Solar", 1 to "Standard", 2 to "OCPP")
val HAC_CHARGE_MODE = mapOf(0 to "Fast", 1 to "ECO", 2 to "Green")
val HAC_ECO_GEAR = mapOf(1 to "6 A", 2 to "10 A", 3 to "16 A", 4 to "20 A", 5 to "25 A")
val HAC_GREEN_GEAR = mapOf(1 to "3 A", 2 to "6 A")
val HAC_START_MODE = mapOf(0 to "Plug and Charge", 1 to "Swipe Card to Start", 2 to "App Start")
val HAC_BOOST_MODE = mapOf(0 to "Normal", 1 to "Timer Boost", 2 to "Smart Boost")
val HAC_CHARGE_PHASE = mapOf(0 to "třífázově", 1 to "L1", 2 to "L2", 3 to "L3")
val HAC_BREAKER = mapOf(0 to "bez omezení", 1 to "omezuje", 2 to "zastaveno")

/**
 * Holding 0x642, spodní bajt. Ověřeno na hardwaru (primary 0x0101, secondary 0x0002),
 * číselník pluginu (0 Master / 1 Slave) neplatí.
 */
val HAC_PARALLEL_ROLE = mapOf(0 to "vypnuto", 1 to "Primary", 2 to "Secondary")

/** Rozsvícené bity poruch na názvy; bity bez popisu se ukážou jako bitN. */
fun decodeHacFaults(value: Long): List<String> =
    (0 until 32).filter { (value shr it) and 1L == 1L }.map { HAC_FAULT_BITS[it] ?: "bit$it" }
