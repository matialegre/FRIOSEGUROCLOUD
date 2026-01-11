#!/usr/bin/env python3
"""
Script avanzado: Descubre ESP32 y prueba conectividad
"""

import socket
import json
import sys
import urllib.request
import urllib.error

# Importar función de discovery
try:
    from discover_esp32 import discover_reefers
except ImportError:
    print("[ERROR] No se encontró discover_esp32.py")
    print("Asegurate de tener el archivo discover_esp32.py en el mismo directorio")
    sys.exit(1)

def test_connectivity(ip, timeout=3):
    """Prueba si el ESP32 responde al endpoint /api/status"""
    try:
        url = f"http://{ip}/api/status"
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 'Reefer-Discovery/1.0')
        
        with urllib.request.urlopen(req, timeout=timeout) as response:
            data = json.loads(response.read().decode())
            return True, data
    except urllib.error.URLError as e:
        return False, str(e)
    except json.JSONDecodeError:
        return False, "Respuesta inválida (no es JSON)"
    except Exception as e:
        return False, str(e)

def main():
    print("=" * 60)
    print("  🔍 DESCUBRIENDO Y PROBANDO ESP32 REEFER")
    print("=" * 60)
    print()
    
    # Descubrir dispositivos
    devices = discover_reefers(timeout=5, broadcast=True)
    
    if not devices:
        print("❌ No se encontraron dispositivos")
        return 1
    
    print()
    print("=" * 60)
    print("  🧪 PROBANDO CONECTIVIDAD")
    print("=" * 60)
    print()
    
    # Probar cada dispositivo
    for device in devices:
        ip = device['ip']
        print(f"📡 Probando {ip}...")
        
        success, result = test_connectivity(ip)
        
        if success:
            print(f"   ✅ Conectividad OK")
            print(f"   📊 Temperatura: {result.get('sensor', {}).get('temp_avg', 'N/A')}°C")
            print(f"   🔌 WiFi: {result.get('system', {}).get('wifi_connected', False)}")
            print(f"   🌐 Internet: {result.get('system', {}).get('internet', False)}")
            print()
        else:
            print(f"   ⚠️  Sin respuesta: {result}")
            print()
    
    return 0

if __name__ == "__main__":
    sys.exit(main())
