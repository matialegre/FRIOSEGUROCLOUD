#!/usr/bin/env python3
"""
Versión simplificada - Solo busca y muestra la IP del ESP32
"""

import socket
import sys

UDP_PORT = 5555
DISCOVER_MSG = "REEFER_DISCOVER"

def discover():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(3)
    
    print("🔍 Buscando ESP32 Reefer...")
    sock.sendto(DISCOVER_MSG.encode(), ("255.255.255.255", UDP_PORT))
    
    try:
        data, addr = sock.recvfrom(1024)
        parts = data.decode().split('|')
        ip = parts[1] if len(parts) > 1 else addr[0]
        print(f"\n✅ ENCONTRADO: http://{ip}")
        return ip
    except socket.timeout:
        print("\n❌ No se encontró ningún dispositivo")
        return None
    finally:
        sock.close()

if __name__ == "__main__":
    discover()
