import json
import os

IP, SLAVE = '192.168.0.245', 1

# Diagnostika: SOLAX_RECORD=soubor uloží syrové registry, SOLAX_REPLAY=soubor je přehraje
# místo čtení z měniče. Umožňuje krmit jiné implementace identickými daty.
REPLAY = os.environ.get('SOLAX_REPLAY')
RECORD = os.environ.get('SOLAX_RECORD')
_snapshot = json.load(open(REPLAY)) if REPLAY else {}
_recorded = {}

if REPLAY:
    c = None
else:
    from pymodbus.client import ModbusTcpClient
    c = ModbusTcpClient(IP, port=502, timeout=5)
    c.connect()

def s16(v): return v - 65536 if v > 32767 else v
def u32_le(lsb, msb): return (msb << 16) | lsb
def s32_le(lsb, msb):
    v = u32_le(lsb, msb)
    return v - (1 << 32) if v >= (1 << 31) else v

def chars14(regs):
    """14char pole: každý registr nese 2 ASCII znaky, MSB první."""
    out = []
    for r in regs:
        out.append(chr(r >> 8))
        out.append(chr(r & 0xFF))
    return ''.join(ch for ch in out if 32 <= ord(ch) < 127).strip()

def hi(v): return v >> 8
def lo(v): return v & 0xFF

def _cached(space, addr, count, fetch):
    key = f"{space}:{addr}:{count}"
    if REPLAY:
        return _snapshot.get(key)
    try:
        r = fetch()
        out = None if r.isError() else list(r.registers)
    except Exception:
        out = None
    if RECORD:
        _recorded[key] = out
    return out

def read_ir(addr, count):
    """Input registry (0x04). Vrací None, pokud měnič blok nepodporuje."""
    return _cached('i', addr, count,
                   lambda: c.read_input_registers(address=addr, count=count, slave=SLAVE))

def read_hr(addr, count):
    """Holding registry (0x03). Vrací None, pokud měnič blok nepodporuje."""
    return _cached('h', addr, count,
                   lambda: c.read_holding_registers(address=addr, count=count, slave=SLAVE))

RUN_MODES = {
    0: "Waiting", 1: "Checking", 2: "Normal", 3: "Fault", 4: "Permanent Fault",
    5: "Update", 6: "Off-grid waiting", 7: "Off-grid", 8: "Self Testing",
    9: "Idle", 10: "Standby",
}

# Table 2-3 Inverter error code (X3) - InvFaultMessage, 32 bitů (input 0x40 LSB / 0x41 MSB)
INV_FAULT_BITS = [
    "TZ Protect Fault", "Grid Lost Fault", "Grid Volt Fault", "Grid Freq Fault",
    "PV Volt Fault", "Bus Volt Fault", "Bat Volt Fault", "AC10mins Volt Fault",
    "DCI OCP Fault", "DCV OCP Fault", "SW OCP Fault", "RC OCP Fault",
    "Isolation Fault", "Temp Over Fault", "BatConnDir Fault", "Off-grid Overload",
    "Overload", "Bat Power Low", "BMS Lost", "Fan Fault",
    "Low Temp Fault", "Reserve21", "Reserve22", "INV Volt Sample Fault",
    "Inner Comm Fault", "INV EEPROM Fault", "RCD Fault", "Grid Relay Fault",
    "Off-grid Relay Fault", "PV ConnDir Fault", "Charger Relay Fault", "Earth Relay Fault",
]

# Table 2-5 Manager error code - Mgr FaultMessage, 16 bitů (input 0x43)
MGR_FAULT_BITS = [
    "Power Type Fault", "Port OC Warning", "Mgr EEPROM Fault", "Reserve3",
    "NTC Sample Invalid", "Bat Temp Low", "Bat Temp High", "Reserve7",
    "Reserve8", "Meter Fault", "Bypass Relay Fault", "Fan 2 Fault",
    "Reserve12", "Reserve13", "Reserve14", "Reserve15",
]

# Table 2-6 BMS warning code - Bat_BMS_FaultMessage, 32 bitů (input 0x44 LSB / 0x45 MSB)
BMS_FAULT_BITS = [
    "BMS_External_Err", "BMS_Internal_Err", "BMS_OverVoltage", "BMS_LowerVoltage",
    "BMS_ChargeOCP", "BMS_DischargeOCP", "BMS_TemHigh", "BMS_TemLow",
    "BMS_CellImbalance", "BMS_Hardware_Protect", "BMS_Circuit_Fault", "BMS_ISO_Fault",
    "BMS_VolSen_Fault", "BMS_TempSen_Fault", "BMS_CurSen_Fault", "BMS_Relay_Fault",
    "BMS_Type_Unmatch", "BMS_Ver_Unmathch", "BMS_MFR_Unmathch", "BMS_SW_Unmathch",
    "BMS_M&S_Unmatch", "BMS_CR_NORespond", "BMS_SW_Protect", "BMS_536_Fault",
    "BMS_SelfcheckErr", "BMS_TempdiffErr", "MS_BreakFault", "BMS_Flash_Fault",
    "BMS_Precharge_Fault", "BMS_AirSwitch_Break", "Rev30", "Rev31",
]

# holding 0x1B, varianta X3 (str. 9-10 protokolu)
SAFETY_CODE = {
    0: "VDE0126", 1: "VDE4105", 2: "AS 4777_2020_A", 3: "G98/1", 4: "C10/11",
    5: "TOR", 6: "EN50438_NL", 7: "Denmark2019_W", 8: "CEB", 9: "CEI021",
    10: "NRS097_2_1", 11: "VDE0126_Gr_Is", 12: "UTE_C15_712", 13: "IEC61727",
    14: "G99/1", 15: "VDE0126_Gr_Co", 16: "Guyana", 17: "C15_712_is_50",
    18: "C15_712_is_60", 19: "New Zealand", 20: "RD1699", 21: "Chile",
    22: "Israel", 23: "Czech_PPDS_2020", 24: "RD1699_Island", 25: "EN50549_Poland",
    26: "EN50438_Portugal", 27: "PEA", 28: "MEA", 29: "EN50549_Sweden",
    30: "Philippines", 31: "EN50438_Slovenia", 32: "Denmark2019_E", 33: "EN50549_EU",
    34: "AS 4777_2020_B", 35: "AS 4777_2020_C", 36: "User-Defined",
    37: "EN50549_Romania", 38: "CEI016", 39: "ACEA",
}

USE_MODE = {0: "Self use", 1: "Feedin Priority", 2: "Back up", 3: "Manual"}
MANUAL = {0: "Stop", 1: "Force charge", 2: "Force discharge"}
POWER_CONTROL = {
    0: "disable remote control", 1: "enable power control",
    2: "enable electric quantity control", 3: "enable SOC target control",
}


def decode_bits(value, names):
    """Vrátí seznam názvů bitů, které jsou v hodnotě nastavené."""
    return [name for i, name in enumerate(names) if value & (1 << i)]


def period(reg):
    """Registr časového okna: Hi = minuta, Lo = hodina."""
    return f"{lo(reg):02d}:{hi(reg):02d}"


# ---------------------------------------------------------------- čtení
# Input registry (0x04): živá měření.
# Modbus limit je 125 registrů na request, takže 0x00-0x9F čteme ve dvou blocích.
ir = read_ir(0x00, 0x60) + read_ir(0x60, 0x40)          # 0x00-0x9F
# 0xB8-0xD0 jedním čtením: stav meteru, BMS detail i detail elektroměru.
# Když měnič blok elektroměru nemá, spadne celý request - proto fallback na samotný BMS detail.
ir_m = read_ir(0xB8, 0x19)
bms_det = ir_m[2:8] if ir_m else read_ir(0xBA, 6)       # 0xBA-0xBF
# blok vzdáleného řízení - starší firmware ho nemusí mít
ir_rc = read_ir(0x100, 0x21)                            # 0x100-0x120

# Holding registry (0x03): konfigurace.
# 0x7D-0xBA jedním čtením: firmware, RTC, režimy, časová okna, typ měniče.
# Fallback na užší blok 0x8B-0x9F, ať o nastavení nepřijdeme kvůli okrajovým registrům.
hr, hr_base = read_hr(0x7D, 0x3E), 0x7D
if hr is None:
    hr, hr_base = read_hr(0x8B, 0x15), 0x8B
hr_id = read_hr(0x00, 0x1C)                             # SN, výrobce, model, safety code
hr_bms = read_hr(0x200, 0x11)                           # BMS info - nemusí být podporováno

def H(addr):
    """Holding registr podle absolutní adresy; None, pokud není v načteném bloku."""
    i = addr - hr_base
    return hr[i] if hr is not None and 0 <= i < len(hr) else None

M = lambda addr: ir_m[addr - 0xB8]                      # noqa: E731
RC = lambda addr: ir_rc[addr - 0x100]                   # noqa: E731

# ---------------------------------------------------------------- výpis
if hr_id or hr:
    print("=== ZAŘÍZENÍ ===")
if hr_id:
    print(f"Sériové číslo:  {chars14(hr_id[0x00:0x07])}")
    print(f"Výrobce:        {chars14(hr_id[0x07:0x0E])}")
    print(f"Model:          {chars14(hr_id[0x0E:0x15])}")
    # holding 0x1B vrací na X3-Hybrid G4 hodnotu mimo rozsah enumu (4750),
    # safety code tam tenhle firmware nedrží - tiskneme jen když dává smysl
    sc = hr_id[0x1B]
    if sc in SAFETY_CODE:
        print(f"Safety code:    {sc} ({SAFETY_CODE[sc]})")
if H(0x7D) is not None:
    print(f"Firmware DSP:   {H(0x7F)}.{H(0x7D)}   (HW {H(0x7E)})")
    print(f"Firmware ARM:   {H(0x80)}.{H(0x83)}   (bootloader {H(0x84)})")
    print(f"Firmware Modbus:{H(0x82)}")
    print(f"Čas měniče:     20{H(0x8A):02d}-{H(0x89):02d}-{H(0x88):02d} "
          f"{H(0x87):02d}:{H(0x86):02d}:{H(0x85):02d}")
if H(0xBA) is not None:
    print(f"Typ měniče:     {H(0xBA)} W")

print("\n=== MĚNIČ ===")
print(f"RunMode:    {ir[0x09]} ({RUN_MODES.get(ir[0x09], '?')})")
print(f"Grid:       {'OnGrid' if ir[0x1A] == 0 else 'OffGrid'}")
print(f"Frekvence:  {ir[0x07]/100:.2f} Hz")
print(f"Teplota:    {s16(ir[0x08])} °C (radiator)")
# PDF má u 0x66 BusVolt 0.1V, ale tenhle firmware se tak nechová: hodnota je záporná
# při nabíjení baterie (-797 při +1485 W) a kolem +119 v klidu. Není to napětí DC busu,
# takže tiskneme syrovou znaménkovou hodnotu a nepředstíráme jednotku.
print(f"Reg 0x66:   {s16(ir[0x66]):+d}   (dokumentováno jako BusVolt, neodpovídá)")
print(f"Lock state: {ir[0x54]} ({ {0: 'locked', 1: 'unlocked'}.get(ir[0x54], 'nedokumentováno') })")

print("\n=== FOTOVOLTAIKA ===")
print(f"PV1:        {ir[0x03]/10:.1f} V × {ir[0x05]/10:.1f} A = {ir[0x0A]} W")
print(f"PV2:        {ir[0x04]/10:.1f} V × {ir[0x06]/10:.1f} A = {ir[0x0B]} W")
print(f"PV celkem:  {ir[0x0A] + ir[0x0B]} W")

print("\n=== SÍŤ (3 fáze) ===")
print(f"L1: {ir[0x6A]/10:6.1f} V  {s16(ir[0x6B])/10:+5.1f} A  {s16(ir[0x6C]):+6d} W  {ir[0x6D]/100:.2f} Hz")
print(f"L2: {ir[0x6E]/10:6.1f} V  {s16(ir[0x6F])/10:+5.1f} A  {s16(ir[0x70]):+6d} W  {ir[0x71]/100:.2f} Hz")
print(f"L3: {ir[0x72]/10:6.1f} V  {s16(ir[0x73])/10:+5.1f} A  {s16(ir[0x74]):+6d} W  {ir[0x75]/100:.2f} Hz")
print(f"Výstupní napětí měniče: {ir[0x9C]/10:.1f} / {ir[0x9D]/10:.1f} / {ir[0x9E]/10:.1f} V")
print(f"Feedin (CT/meter): {s32_le(ir[0x46], ir[0x47]):+d} W   (+ export, - import)")
print(f"  po fázích: L1 {s32_le(ir[0x82], ir[0x83]):+d} W   "
      f"L2 {s32_le(ir[0x84], ir[0x85]):+d} W   L3 {s32_le(ir[0x86], ir[0x87]):+d} W")

if ir_m:
    print("\n=== ELEKTROMĚR ===")
    # Meter2 hlásí com error, když druhý elektroměr není nainstalovaný - není to porucha
    print(f"Komunikace: Meter1 {'OK' if M(0xB8) == 1 else 'CHYBA'}, "
          f"Meter2 {'OK' if M(0xB9) == 1 else 'nepřipojen'}")
    print(f"Frekvence:  {M(0xC8)/100:.2f} Hz")
    print(f"Napětí:     celkem {M(0xC9)/10:.1f} V   "
          f"L1 {M(0xCA)/10:.1f}  L2 {M(0xCB)/10:.1f}  L3 {M(0xCC)/10:.1f} V")
    print(f"Proud:      celkem {s16(M(0xCD))/10:+.1f} A   "
          f"L1 {s16(M(0xCE))/10:+.1f}  L2 {s16(M(0xCF))/10:+.1f}  L3 {s16(M(0xD0))/10:+.1f} A")
    print(f"Jalový výkon: celkem {s16(M(0xC0)):+d} var   "
          f"L1 {s16(M(0xC1)):+d}  L2 {s16(M(0xC2)):+d}  L3 {s16(M(0xC3)):+d} var")
    print(f"Účiník:     celkem {s16(M(0xC4))/100:+.2f}   "
          f"L1 {s16(M(0xC5))/100:+.2f}  L2 {s16(M(0xC6))/100:+.2f}  L3 {s16(M(0xC7))/100:+.2f}")

print("\n=== BATERIE ===")
print(f"Napětí:     {ir[0x14]/10:.1f} V")
print(f"Proud:      {s16(ir[0x15])/10:+.1f} A")
print(f"Výkon:      {s16(ir[0x16]):+d} W   (+ nabíjí, - vybíjí)")
print(f"SoC:        {ir[0x1C]} %")
print(f"Teplota:    {s16(ir[0x18])} °C")
bdc = {0: "discharge", 1: "charge", 2: "stop"}.get(ir[0x19], "?")
print(f"BDC stav:   {ir[0x19]} ({bdc})")
print(f"BMS:        {'Connected' if ir[0x17] == 1 else 'Disconnected'}")
print(f"MPPT count: {ir[0x1B]}")
print(f"BMS Charge MaxCurrent:    {ir[0x24]/10:.1f} A")
print(f"BMS Discharge MaxCurrent: {ir[0x25]/10:.1f} A")
print(f"BMS Battery Capacity:     {ir[0x26]} Wh", end="")
# T58 V2 modul má 5.76 kWh; Subsystem_Num z holding 0x200 se na tomhle toweru neplní
print(f"   (~{ir[0x26]/5760:.0f}× modul T58)" if ir[0x26] >= 5760 else "")
if ir_rc:
    print(f"Lze ještě nabít:   {u32_le(RC(0x116), RC(0x117))} Wh")
    print(f"Lze ještě vybít:   {u32_le(RC(0x118), RC(0x119))} Wh")
    print(f"BMS relé:          {'ON' if RC(0x120) == 1 else 'OFF'}")
    print(f"Force charge flag: {'ANO' if RC(0x11F) == 1 else 'ne'}")
    print(f"ModbusPowerControl: {RC(0x100)} ({POWER_CONTROL.get(RC(0x100), '?')})")

if bms_det:
    tem_high, tem_low, v_high, v_low, bms_soc, bms_soh = bms_det

    print("\n=== BMS DETAIL (články) ===")
    print(f"Cell V max:  {v_high/1000:.3f} V")
    print(f"Cell V min:  {v_low/1000:.3f} V")
    print(f"Cell delta:  {v_high - v_low} mV   (zdravé < 50 mV, varování > 150 mV)")
    print(f"Bat tem max: {s16(tem_high)/10:.1f} °C")
    print(f"Bat tem min: {s16(tem_low)/10:.1f} °C")
    # 0xBE se na tomhle BMS neplní (vrací 0), reálné SoC je v input 0x1C
    print(f"BMS SoC:     {bms_soc} %" if bms_soc else "BMS SoC:     neplní se (viz SoC výše)")
    print(f"BMS SoH:     {bms_soh} %")

print("\n=== ENERGIE ===")
print(f"Výroba FV dnes:          {ir[0x96]/10:.1f} kWh")
print(f"Výroba FV celkem:        {u32_le(ir[0x94], ir[0x95])/10:.1f} kWh")
print(f"Dnes nabito do baterie:  {ir[0x23]/10:.1f} kWh")
print(f"Dnes vybito z baterie:   {ir[0x20]/10:.1f} kWh")
print(f"Celkem nabito (lifetime): {u32_le(ir[0x21], ir[0x22])/10:.1f} kWh")
print(f"Celkem vybito (lifetime): {u32_le(ir[0x1D], ir[0x1E])/10:.1f} kWh")
print(f"Dnes do sítě (meter):    {u32_le(ir[0x98], ir[0x99])/100:.2f} kWh")
print(f"Dnes ze sítě (meter):    {u32_le(ir[0x9A], ir[0x9B])/100:.2f} kWh")
print(f"Celkem do sítě (meter):  {u32_le(ir[0x48], ir[0x49])/100:.2f} kWh")
print(f"Celkem ze sítě (meter):  {u32_le(ir[0x4A], ir[0x4B])/100:.2f} kWh")
print(f"Dnes na AC portu:        {ir[0x50]/10:.1f} kWh")
print(f"Celkem na AC portu:      {u32_le(ir[0x52], ir[0x53])/10:.1f} kWh")
print(f"EchargeToday:            {ir[0x91]/10:.1f} kWh")
print(f"EchargeTotal:            {u32_le(ir[0x92], ir[0x93])/10:.1f} kWh")
print(f"Off-grid výroba dnes:    {ir[0x90]/10:.1f} kWh")
print(f"Off-grid výroba celkem:  {u32_le(ir[0x8E], ir[0x8F])/10:.1f} kWh")
print(f"Provoz on-grid:          {u32_le(ir[0x88], ir[0x89])/10:.1f} h")
print(f"Provoz off-grid:         {u32_le(ir[0x8A], ir[0x8B])/10:.1f} h")

# Chybové a alarmové registry (input 0x3E-0x45), už načtené v bloku výše
pcs_major = ir[0x3E]
bat_major = ir[0x3F]
inv_fault = u32_le(ir[0x40], ir[0x41])
mgr_fault = ir[0x43]
bms_fault = u32_le(ir[0x44], ir[0x45])

print("\n=== CHYBY A ALARMY ===")
print(f"PCSMajorFault:     0x{pcs_major:04X}")
print(f"BatteryMajorFault: 0x{bat_major:04X}")
print(f"InvFaultMessage:   0x{inv_fault:08X}")
print(f"Mgr FaultMessage:  0x{mgr_fault:04X}")
print(f"BMS FaultMessage:  0x{bms_fault:08X}")

inv_list = decode_bits(inv_fault, INV_FAULT_BITS)
mgr_list = decode_bits(mgr_fault, MGR_FAULT_BITS)
bms_list = decode_bits(bms_fault, BMS_FAULT_BITS)

if not (inv_list or mgr_list or bms_list):
    print("Stav: bez chyb")
else:
    for name in inv_list:
        print(f"  [MĚNIČ]   {name}")
    for name in mgr_list:
        print(f"  [MANAGER] {name}")
    for name in bms_list:
        print(f"  [BMS]     {name}")

if hr:
    print("\n=== NASTAVENÍ ===")
    print(f"SolarChargerUseMode:      {H(0x8B)} ({USE_MODE.get(H(0x8B), '?')})")
    print(f"Manual mode:              {H(0x8C)} ({MANUAL.get(H(0x8C), '?')})")
    print(f"Battery type:             {'Lithium' if H(0x8D) == 1 else 'Lead Acid'}")
    print(f"SelfUse Discharge MinSoC: {hi(H(0x93))} %")
    print(f"NightCharge enabled:      {'yes' if lo(H(0x93)) else 'no'}")
    print(f"NightCharge target SoC:   {H(0x94)} %")
    print(f"Feedin  NightCharge/MinSoC: {hi(H(0x95))} % / {lo(H(0x95))} %")
    print(f"BackUp  NightCharge/MinSoC: {hi(H(0x96))} % / {lo(H(0x96))} %")
    if H(0xA0) is not None:
        print(f"EPS restart SoC:          {H(0xA0)} %")
    if H(0xB8) is not None:
        print(f"Off-grid MinSoC:          {H(0xB8)} %")

    print("\n=== ČASOVÁ OKNA ===")
    print(f"Nabíjení 1:  {period(H(0x97))} - {period(H(0x98))}")
    print(f"Vybíjení 1:  {period(H(0x99))} - {period(H(0x9A))}")
    print(f"Perioda 2:   {'zapnuta' if H(0x9B) else 'vypnuta'}")
    print(f"Nabíjení 2:  {period(H(0x9C))} - {period(H(0x9D))}")
    print(f"Vybíjení 2:  {period(H(0x9E))} - {period(H(0x9F))}")

if hr_bms and hr_bms[0]:                                # T58 tower Subsystem_Num nereportuje
    print("\n=== BMS INFO ===")
    print(f"Počet modulů (Subsystem_Num): {hr_bms[0]}")
    print(f"BMS master verze: {hi(hr_bms[1])}.{lo(hr_bms[1])}")
    for i in range(8):
        v = hr_bms[2 + i]
        if v:
            print(f"BMS slave{i+1} verze: {hi(v)}.{lo(v)}")
    print(f"BMS master SN:    {chars14(hr_bms[10:17])}")

# ---------------------------------------------------------------- diagnostika
missing = []
if ir_m is None:
    missing.append("0xB8-0xD0 (detail elektroměru)")
if bms_det is None:
    missing.append("0xBA-0xBF (BMS detail - články)")
if ir_rc is None:
    missing.append("0x100-0x120 (kapacita baterie ve Wh)")
if hr is None:
    missing.append("holding 0x8B-0x9F (nastavení, časová okna)")
elif hr_base == 0x8B:
    missing.append("holding 0x7D-0xBA (firmware, RTC, typ měniče) - použit užší blok 0x8B-0x9F")
if hr_id is None:
    missing.append("holding 0x00-0x1B (sériové číslo, model)")
if hr_bms is None:
    missing.append("holding 0x200-0x210 (BMS info)")
if missing:
    print("\n=== NEPODPOROVANÉ BLOKY ===")
    for m in missing:
        print(f"  {m}")

if RECORD:
    with open(RECORD, 'w') as f:
        json.dump(_recorded, f)
if c:
    c.close()
