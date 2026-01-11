#!/usr/bin/env python3
"""
Script para verificar configuración de red antes de buscar ESP32
"""

import socket
import subprocess
import sys
import platform

def get_local_ip():
    """Obtiene la IP local"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return None

def test_internet():
    """Verifica conectividad a internet"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(3)
        s.connect(("8.8.8.8", 53))
        s.close()
        return True
    except:
        return False

def test_udp_port():
    """Verifica que se pueda crear socket UDP"""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.bind(('', 5556))
        sock.close()
        return True
    except PermissionError:
        return False, "Permisos insuficientes (probá con sudo)"
    except Exception as e:
        return False, str(e)

def get_network_info():
    """Obtiene info de red usando comandos del sistema"""
    info = {}
    system = platform.system()
    
    if system == "Windows":
        try:
            result = subprocess.run(['ipconfig'], capture_output=True, text=True, timeout=5)
            info['output'] = result.stdout
        except:
            pass
    elif system in ["Linux", "Darwin"]:
        try:
            result = subprocess.run(['ifconfig'], capture_output=True, text=True, timeout=5)
            info['output'] = result.stdout
        except:
            try:
                result = subprocess.run(['ip', 'addr'], capture_output=True, text=True, timeout=5)
                info['output'] = result.stdout
            except:
                pass
    
    return info

def main():
    print("=" * 60)
    print("  🔍 VERIFICACIÓN DE RED PARA ESP32")
    print("=" * 60)
    print()
    
    # 1. IP Local
    print("1️⃣  IP Local:")
    local_ip = get_local_ip()
    if local_ip:
        print(f"   ✅ {local_ip}")
        
        parts = local_ip.split('.')
        if len(parts) == 4:
            network = f"{parts[0]}.{parts[1]}.{parts[2]}.0/24"
            broadcast = f"{parts[0]}.{parts[1]}.{parts[2]}.255"
            print(f"   📍 Red estimada: {network}")
            print(f"   📡 Broadcast: {broadcast}")
    else:
        print("   ❌ No se pudo determinar")
    
    # 2. Internet
    print("\n2️⃣  Conectividad a Internet:")
    if test_internet():
        print("   ✅ Conectado")
    else:
        print("   ⚠️  Sin internet (puede estar bien si el ESP32 está en red local)")
    
    # 3. Socket UDP
    print("\n3️⃣  Permisos UDP:")
    udp_test = test_udp_port()
    if udp_test is True:
        print("   ✅ Puede crear sockets UDP")
    else:
        if isinstance(udp_test, tuple):
            print(f"   ❌ {udp_test[1]}")
        else:
            print(f"   ❌ Error: {udp_test}")
    
    # 4. Información de red
    print("\n4️⃣  Información de red del sistema:")
    net_info = get_network_info()
    if net_info.get('output'):
        # Mostrar solo líneas relevantes
        lines = net_info['output'].split('\n')
        relevant = [l for l in lines if 'inet' in l.lower() or 'IPv4' in l or 'Dirección' in l or 'address' in l.lower()]
        if relevant:
            for line in relevant[:10]:  # Primeras 10 líneas relevantes
                print(f"   {line.strip()}")
        else:
            print("   ℹ️  Ejecutá 'ipconfig' (Windows) o 'ifconfig' (Linux/Mac) para más detalles")
    else:
        print("   ℹ️  No se pudo obtener (ejecutá manualmente ipconfig/ifconfig)")
    
    # 5. Recomendaciones
    print("\n" + "=" * 60)
    print("  💡 RECOMENDACIONES")
    print("=" * 60)
    print()
    
    issues = []
    
    if not local_ip:
        issues.append("❌ No se pudo obtener IP local")
    
    if udp_test is not True:
        issues.append("❌ Problema con sockets UDP")
        if platform.system() != "Windows":
            print("   💡 En Linux/Mac, probá ejecutar con: sudo python3 verificar_red.py")
    
    if issues:
        print("⚠️  Problemas detectados:")
        for issue in issues:
            print(f"   {issue}")
        print()
    
    print("✅ Checklist antes de buscar ESP32:")
    print("   ☐ El ESP32 está encendido")
    print("   ☐ El ESP32 está conectado al WiFi")
    print("   ☐ Estás en la MISMA red WiFi que el ESP32")
    print("   ☐ El firewall permite UDP en puerto 5555-5556")
    print("   ☐ El firewall permite HTTP en puerto 80")
    print()
    
    if local_ip:
        print(f"📝 Si conocés la IP del ESP32, probá:")
        print(f"   python probar_ip.py [IP_DEL_ESP32]")
        print()
        print(f"📝 O ejecutá el diagnóstico completo:")
        print(f"   python discover_esp32_diagnostico.py")
    else:
        print("❌ Sin IP local - verificá tu conexión de red")
    
    print("=" * 60)
    
    return 0 if local_ip and udp_test is True else 1

if __name__ == "__main__":
    sys.exit(main())
