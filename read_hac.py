import sys
import time

from pymodbus.client import ModbusTcpClient

# Nabíječky X3-HAC-22P v paralelu (obě mají Modbus adresu 70, unit id na TCP ignorují).
# Jiné IP lze předat jako argumenty: python3 read_hac.py 192.168.0.137
IPS = sys.argv[1:] or ['192.168.0.137', '192.168.0.142']
SLAVE = 1

# Mapa registrů: X1.X3-HAC.EV.Charger.Modbus.TCP.RTU.V1.0.0-EN.pdf. Registry, které PDF
# nepopisuje (SN 0x600, zdroj dat 0x60C, min. proud 0x63F, adresa 0x640, paralel 0x642-0x66F,
# input 0x37-0x42 a 0x64-0x78, bity FaultCode), jsou z homeassistant-solax-modbus
# (plugin_solax_ev_charger.py, GEN2). Skript je jen pro čtení.

def s16(v): return v - 65536 if v > 32767 else v
def u32_le(lsb, msb): return (msb << 16) | lsb

def chars(regs):
    """Řetězec: každý registr nese 2 ASCII znaky, MSB první."""
    out = []
    for r in regs:
        out.append(chr(r >> 8))
        out.append(chr(r & 0xFF))
    return ''.join(ch for ch in out if 32 <= ord(ch) < 127).strip()

RUN_MODES = {
    0: "Available", 1: "Preparing", 2: "Charging", 3: "Finishing", 4: "Faulted",
    5: "Unavailable", 6: "Reserved", 7: "Suspended EV", 8: "Suspended EVSE",
    9: "Update", 10: "Card Activation", 11: "Start Delay", 12: "Charge Paused",
    13: "Stopping", 14: "Occupied", 15: "Waiting for Response", 16: "Discharging",
    17: "Phase Switching",
}

# FaultCode (input 0x1E LSB / 0x1F MSB) - PDF V1.0.0 bity nepopisuje, převzato z pluginu
FAULT_BITS = {
    0: "Emergency stop", 1: "Overcurrent", 2: "Over temperature", 3: "PE grounding fault",
    4: "Leakage current", 5: "PE leakage current", 6: "Overload protection",
    8: "L1 overvoltage", 9: "L1 undervoltage", 10: "L2 overvoltage", 11: "L2 undervoltage",
    12: "L3 overvoltage", 13: "L3 undervoltage", 14: "Energy measurement IC fault",
    15: "Meter communication fault", 16: "Incorrect power rating",
    17: "Control Pilot voltage anomaly", 18: "Electronic lock fault",
    19: "Meter selection failure", 20: "Cover opened", 21: "PEN relay fault",
    22: "Parallel communication failure", 23: "L1N relay contact sticking",
    24: "L1N relay failure", 25: "L2L3 relay contact sticking", 26: "L2L3 relay failure",
    28: "Abnormal metering",
}

# PDF uvádí 0: 6 kW, 1: 7 kW, 2: 11 kW, 3: 22 kW, ale X3-HAC-22P hlásí 2 -> platí tabulka pluginu.
POWER_RATING = {0: "7 kW", 1: "11 kW", 2: "22 kW", 3: "6 kW", 4: "4.6 kW",
                5: "7.6 kW", 6: "9.6 kW", 7: "11.5 kW"}
SCENE = {0: "Solar", 1: "Standard", 2: "OCPP"}
CHARGE_MODE = {0: "Fast", 1: "ECO", 2: "Green"}
ECO_GEAR = {1: "6 A", 2: "10 A", 3: "16 A", 4: "20 A", 5: "25 A"}
GREEN_GEAR = {1: "3 A", 2: "6 A"}
START_MODE = {0: "Plug and Charge", 1: "Swipe Card to Start", 2: "App Start"}
BOOST_MODE = {0: "Normal", 1: "Timer Boost", 2: "Smart Boost"}
CHARGE_PHASE = {0: "třífázově", 1: "L1", 2: "L2", 3: "L3"}
# Holding 0x642, spodní bajt = Parallel Setting z aplikace. Ověřeno na hardwaru
# (master 0x0101, slave 0x0002); číselník pluginu (0 Master / 1 Slave) neplatí.
# Holding 0x60C: 1 = střídač (výkon sítě 0x15-0x18 sedí s feed-in střídače). Plugin tvrdí
# 0 CT / 1 Meter / 2 Inverter, to neplatí; hodnoty pro CT a elektroměr zatím neznáme.
PARALLEL_ROLE = {0: "vypnuto", 1: "Primary (master)", 2: "Secondary (slave)"}

def decode_bits(value, names):
    if value == 0:
        return "OK"
    return ", ".join(names.get(b, f"bit{b}") for b in range(32) if value >> b & 1)

def read_charger(ip):
    c = ModbusTcpClient(ip, port=502, timeout=5)
    if not c.connect():
        print(f"\n### {ip}: nelze se připojit ###")
        return
    blocks = {}
    # Protokol vyžaduje min. 1 s mezi dotazy.
    for name, fn, addr, count in (('ir', c.read_input_registers, 0x00, 0x43),
                                  ('ver', c.read_input_registers, 0x64, 0x15),
                                  ('hr', c.read_holding_registers, 0x600, 0x70)):
        r = fn(address=addr, count=count, slave=SLAVE)
        blocks[name] = None if r.isError() else list(r.registers)
        time.sleep(1)
    c.close()

    ir, ver, hr = blocks['ir'], blocks['ver'], blocks['hr']
    if ir is None or hr is None:
        print(f"\n### {ip}: čtení registrů selhalo ###")
        return
    def H(addr): return hr[addr - 0x600]

    print(f"\n######## NABÍJEČKA {ip} ########")
    print("=== ZAŘÍZENÍ ===")
    print(f"SN:         {chars(hr[0:7])}   (WiFi modul {chars(hr[7:12])})")
    print(f"Model:      {POWER_RATING.get(ir[0x21], ir[0x21])}, "
          f"{'třífázová' if ir[0x22] == 1 else 'jednofázová'}, scéna {SCENE.get(ir[0x23], ir[0x23])}")
    fw = f"Firmware:   {ir[0x25]/100:.2f}"
    if ver:
        fw += f"   modul {ver[0x71-0x64]/100:.2f}, Modbus protokol {ver[0x78-0x64]/100:.2f}"
    print(fw)
    print(f"Síť:        {'připojeno' if ir[0x26] == 1 else 'nepřipojeno'}, signál {ir[0x27]} %")
    print(f"Modbus adr: {H(0x640)}")
    print(f"Hodiny:     {H(0x621):02d}.{H(0x622):02d}.20{H(0x623):02d} "
          f"{H(0x620):02d}:{H(0x61F):02d}:{H(0x61E):02d}   (TZ {s16(H(0x61D)):+d} min)")
    print(f"Teplota:    {s16(ir[0x1C])} °C")

    print("\n=== STAV ===")
    print(f"RunMode:    {ir[0x1D]} ({RUN_MODES.get(ir[0x1D], '?')})")
    print(f"Porucha:    {decode_bits(u32_le(ir[0x1E], ir[0x1F]), FAULT_BITS)}")
    print(f"CP napětí:  {ir[0x1A]/100:.2f} V   (12 V = bez auta, 9 V = připojeno, 6 V = nabíjí)")
    print(f"PWM:        {ir[0x1B]/10:.1f} %")
    print(f"Fáze:       {CHARGE_PHASE.get(ir[0x28], ir[0x28])}")
    print(f"Zámek:      {'zamčeno' if ir[0x2D] == 1 else 'odemčeno'}, "
          f"jistič {['bez omezení', 'omezuje', 'zastaveno'][ir[0x2E]] if ir[0x2E] < 3 else ir[0x2E]}, "
          f"{'nabíjení zakázáno' if ir[0x30] == 1 else 'nabíjení povoleno'}")

    print("\n=== NABÍJENÍ ===")
    for i, ph in enumerate(('L1', 'L2', 'L3')):
        print(f"{ph}: {ir[i]/100:6.2f} V  {s16(ir[4+i])/100:5.2f} A  {s16(ir[8+i]):5d} W  {ir[0x0C+i]/100:.2f} Hz")
    print(f"Výkon celkem:     {s16(ir[0x0B])} W")
    print(f"Tato relace:      {ir[0x0F]/10:.1f} kWh, {u32_le(ir[0x2B], ir[0x2C]) // 60} min")
    st = ir[0x31:0x37]
    if any(st):
        print(f"Začátek relace:   {st[3]:02d}.{st[4]:02d}.20{st[5]:02d} {st[2]:02d}:{st[1]:02d}:{st[0]:02d}")
    print(f"Nabito celkem:    {u32_le(ir[0x10], ir[0x11])/10:.1f} kWh")

    print("\n=== SÍŤ (data od střídače) ===")
    print(f"Výkon:      L1 {s16(ir[0x15]):+d} W   L2 {s16(ir[0x16]):+d} W   L3 {s16(ir[0x17]):+d} W   "
          f"celkem {s16(ir[0x18]):+d} W   (+ export, - import)")

    print("\n=== NASTAVENÍ ===")
    print(f"Režim:            {CHARGE_MODE.get(H(0x669), H(0x669))}")
    print(f"ECO stupeň:       {ECO_GEAR.get(H(0x60E), H(0x60E))}")
    print(f"Green stupeň:     {GREEN_GEAR.get(H(0x60F), H(0x60F))}")
    print(f"Boost:            {BOOST_MODE.get(H(0x613), H(0x613))}")
    print(f"Start nabíjení:   {START_MODE.get(H(0x610), H(0x610))}")
    print(f"Nabíjecí proud:   {H(0x628)/100:.2f} A   (min {H(0x63F)/100:.2f} A, max {H(0x668)/100:.2f} A)")
    print(f"Hlavní jistič:    {H(0x614)} A (Dynamic load balance), hlídání {'zapnuto' if H(0x664) == 1 else 'vypnuto'}")
    print(f"Alarm napětí:     {H(0x611)} / {H(0x612)} V   (přepětí / podpětí)")
    src = {1: "střídač"}.get(H(0x60C), f"{H(0x60C)} (CT/elektroměr? neověřeno)")
    print(f"Zdroj dat sítě:   {src}")

    print("\n=== PARALELNÍ PROVOZ ===")
    print(f"Role:             {PARALLEL_ROLE.get(H(0x642) & 0xFF, '?')}   (0x642 = 0x{H(0x642):04X})")
    print(f"Počet secondary:  {H(0x66C)}")
    if H(0x642) & 0xFF == 1:
        print(f"Poměr výkonu:     {H(0x66D) >> 8}:{H(0x66D) & 0xFF}   (primary:secondary)")
    print(f"Chyby paralelu:   0x{u32_le(H(0x66A), H(0x66B)):08X}")
    print(f"Secondary:        {RUN_MODES.get(ir[0x39], ir[0x39])}, "
          f"{s16(ir[0x3A])/100:.2f}/{s16(ir[0x3B])/100:.2f}/{s16(ir[0x3C])/100:.2f} A, {s16(ir[0x40])} W")

for ip in IPS:
    read_charger(ip)
