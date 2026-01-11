#!/usr/bin/env python3
"""
Script para probar una IP específica del ESP32
Útil si conocés la IP pero el discovery no funciona
"""

import socket
import json
import sys
import urllib.request
import urllib.error

def test_ip(ip):
    """Prueba si un ESP32 Reefer está en esa IP"""
    print(f"🔍 Probando IP: {ip}")
    print("=" * 60)
    
    # Test 1: UDP Discovery
    print("\n1️⃣  Probando UDP Discovery (puerto 5555)...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(2)
        sock.bind(('', 5556))
        
        sock.sendto(b"REEFER_DISCOVER", (ip, 5555))
        
        try:
            data, addr = sock.recvfrom(1024)
            response = data.decode('utf-8')
            print(f"   ✅ UDP responde: {response}")
            
            if "REEFER_HERE" in response:
                parts = response.split('|')
                if len(parts) >= 4:
                    print(f"   📱 ID: {parts[2]}")
                    print(f"   📝 Nombre: {parts[3]}")
        except socket.timeout:
            print("   ⚠️  Sin respuesta UDP (puede estar bien si HTTP funciona)")
        finally:
            sock.close()
    except Exception as e:
        print(f"   ❌ Error UDP: {e}")
    
    # Test 2: HTTP API
    print("\n2️⃣  Probando HTTP API (puerto 80)...")
    try:
        url = f"http://{ip}/api/status"
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 'Reefer-Discovery/1.0')
        req.timeout = 3
        
        with urllib.request.urlopen(req, timeout=3) as response:
            data = json.loads(response.read().decode())
            
            print(f"   ✅ HTTP responde correctamente")
            print(f"\n   📊 Información del dispositivo:")
            
            if 'device' in data:
                dev = data['device']
                print(f"      ID: {dev.get('id', 'N/A')}")
                print(f"      Nombre: {dev.get('name', 'N/A')}")
                print(f"      IP: {dev.get('ip', ip)}")
            
            if 'sensor' in data:
                sen = data['sensor']
                temp = sen.get('temp_avg', 'N/A')
                print(f"      Temperatura: {temp}°C")
                print(f"      Sensor válido: {sen.get('valid', False)}")
            
            if 'system' in data:
                sys_data = data['system']
                print(f"      WiFi: {'✅' if sys_data.get('wifi_connected') else '❌'}")
                print(f"      Internet: {'✅' if sys_data.get('internet') else '❌'}")
                print(f"      Uptime: {sys_data.get('uptime_sec', 0)} segundos")
            
            print(f"\n   🌐 URL completa: http://{ip}")
            print(f"   📱 Panel web: http://{ip}/")
            
            return True
            
    except urllib.error.URLError as e:
        print(f"   ❌ Error HTTP: {e}")
        print(f"   💡 Verificá que:")
        print(f"      - La IP sea correcta")
        print(f"      - El ESP32 esté encendido")
        print(f"      - Estés en la misma red")
        return False
    except Exception as e:
        print(f"   ❌ Error: {e}")
        return False
    
    # Test 3: Puerto 80 general
    print("\n3️⃣  Probando conexión TCP puerto 80...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(2)
        result = sock.connect_ex((ip, 80))
        sock.close()
        
        if result == 0:
            print(f"   ✅ Puerto 80 abierto")
        else:
            print(f"   ❌ Puerto 80 cerrado o filtrado")
    except Exception as e:
        print(f"   ❌ Error: {e}")
    
    return False

def main():
    if len(sys.argv) < 2:
        print("=" * 60)
        print("  PROBAR IP ESPECÍFICA DEL ESP32")
        print("=" * 60)
        print()
        print("Uso: python probar_ip.py [IP]")
        print()
        print("Ejemplos:")
        print("  python probar_ip.py 192.168.1.50")
        print("  python probar_ip.py 192.168.0.100")
        print()
        
        # Modo interactivo
        ip = input("Ingresá la IP a probar (o Enter para cancelar): ").strip()
        if not ip:
            print("Cancelado.")
            return 1
    else:
        ip = sys.argv[1]
    
    # Validar formato IP básico
    parts = ip.split('.')
    if len(parts) != 4:
        print(f"❌ IP inválida: {ip}")
        return 1
    
    try:
        for part in parts:
            num = int(part)
            if num < 0 or num > 255:
                raise ValueError
    except ValueError:
        print(f"❌ IP inválida: {ip}")
        return 1
    
    success = test_ip(ip)
    
    print("\n" + "=" * 60)
    if success:
        print("✅ ¡ESP32 encontrado y funcionando!")
    else:
        print("⚠️  El ESP32 no responde en esa IP")
        print("\n💡 Sugerencias:")
        print("   - Verificá la IP en el router/configuración WiFi")
        print("   - Probá desde el navegador: http://" + ip)
        print("   - Verificá el Serial Monitor del ESP32")
    print("=" * 60)
    
    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(main())
