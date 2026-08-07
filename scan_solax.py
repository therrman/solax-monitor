import socket
import sys
from concurrent.futures import ThreadPoolExecutor

PORT = 502
TIMEOUT = 0.4


def get_local_subnet():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    finally:
        s.close()
    return ".".join(ip.split(".")[:3])


def check_port(ip):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(TIMEOUT)
        try:
            s.connect((ip, PORT))
            return ip
        except OSError:
            return None


def main():
    subnet = sys.argv[1] if len(sys.argv) > 1 else get_local_subnet()
    print(f"=== SKEN SÍTĚ {subnet}.0/24 na portu {PORT} (Modbus TCP) ===")
    hosts = [f"{subnet}.{i}" for i in range(1, 255)]

    found = []
    with ThreadPoolExecutor(max_workers=100) as ex:
        for ip in ex.map(check_port, hosts):
            if ip:
                found.append(ip)
                print(f"  {ip}  -> port {PORT} OTEVŘENÝ")

    if not found:
        print("\nNic nenalezeno. Zkontroluj, že jsi připojený na stejnou síť jako měnič.")
        return

    print("\n=== OVĚŘENÍ MODBUS ODPOVĚDI ===")
    try:
        from pymodbus.client import ModbusTcpClient
    except ImportError:
        print("pymodbus není nainstalován, nelze ověřit obsah odpovědi (pip3 install pymodbus).")
        return

    for ip in found:
        c = ModbusTcpClient(ip, port=PORT, timeout=2)
        if not c.connect():
            print(f"{ip}: port byl otevřený, ale Modbus spojení selhalo")
            continue
        ok = False
        for slave_id in (1, 0, 247):
            r = c.read_holding_registers(address=0, count=1, slave=slave_id)
            if not r.isError():
                print(f"{ip}: MODBUS OK, slave id {slave_id}, register[0]={r.registers[0]}")
                ok = True
                break
        if not ok:
            print(f"{ip}: port otevřený, ale čtení registru selhalo pro slave id 1/0/247")
        c.close()


if __name__ == "__main__":
    main()
