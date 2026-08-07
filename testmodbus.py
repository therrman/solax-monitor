from pymodbus.client import ModbusTcpClient

c = ModbusTcpClient('192.168.0.245', port=502, timeout=3)
c.connect()

# Solax X3-Hybrid-G4: čtení holding registrů od adresy 0
# Slave ID bývá 1 (měnič), nebo zkus i 0 / 247 pokud nereaguje
for slave_id in [1, 0, 247]:
      r = c.read_holding_registers(address=0, count=10, slave=slave_id)
      if not r.isError():
          print(f"slave {slave_id} OK: {r.registers}")
          break
      else:
          print(f"slave {slave_id}: {r}")

c.close()
