#!/usr/bin/env python3
"""
Script de diagnóstico avanzado para encontrar ESP32 Reefer
Prueba múltiples métodos: UDP Discovery, escaneo HTTP, mDNS
"""

import socket
import json
import sys
import urllib.request
import urllib.error
import threading
import time
from datetime import datetime
from ipaddress import IPv4Network

# Configuración
UDP_PORT = 5555
DISCOVER_MSG = "REEFER_DISCOVER"
RESPONSE_MSG = "REEFER_HERE"

def print_section(title):
    """Imprime un encabezado de sección"""
    print("\n" + "=" * 70)
    print(f"  {title}")
    print("=" * 70)

def get_local_network_info():
    """Obtiene información de la red local"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        
        # Obtener máscara de red (asumimos /24)
        parts = local_ip.split('.')
        network_base = f"{parts[0]}.{parts[1]}.{parts[2]}.0/24"
        broadcast = f"{parts[0]}.{parts[1]}.{parts[2]}.255"
        
        return local_ip, network_base, broadcast
    except Exception as e:
        print(f"[ERROR] No se pudo obtener info de red: {e}")
        return None, None, "255.255.255.255"

def method1_udp_broadcast(timeout=5):
    """Método 1: UDP Broadcast (método original)"""
    print_section("MÉTODO 1: UDP Broadcast")
    
    devices = []
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(timeout)
    
    # Bind a un puerto específico para recibir respuestas
    local_ip, _, broadcast = get_local_network_info()
    sock.bind(('', 5556))  # Puerto diferente para recibir
    
    try:
        print(f"📡 Enviando '{DISCOVER_MSG}' a {broadcast}:{UDP_PORT}")
        sock.sendto(DISCOVER_MSG.encode('utf-8'), (broadcast, UDP_PORT))
        
        print(f"⏳ Esperando respuestas (timeout: {timeout}s)...")
        start = time.time()
        
        while time.time() - start < timeout:
            try:
                sock.settimeout(1)  # Timeout corto para verificar periódicamente
                data, addr = sock.recvfrom(1024)
                response = data.decode('utf-8', errors='ignore')
                
                if RESPONSE_MSG in response:
                    parts = response.split('|')
                    device = {
                        'method': 'UDP Broadcast',
                        'ip': parts[1] if len(parts) > 1 else addr[0],
                        'device_id': parts[2] if len(parts) > 2 else 'UNKNOWN',
                        'device_name': parts[3] if len(parts) > 3 else 'Reefer',
                    }
                    devices.append(device)
                    print(f"✅ Encontrado: {device['ip']} ({device['device_name']})")
            except socket.timeout:
                continue
            except Exception as e:
                print(f"[DEBUG] Error recibiendo: {e}")
                continue
                
    except Exception as e:
        print(f"[ERROR] Error en UDP broadcast: {e}")
    finally:
        sock.close()
    
    return devices

def method2_udp_direct_scan(timeout=2):
    """Método 2: Escaneo directo de IPs (sin broadcast)"""
    print_section("MÉTODO 2: Escaneo Directo UDP")
    
    devices = []
    local_ip, network_base, _ = get_local_network_info()
    
    if not network_base:
        print("[SKIP] No se pudo determinar red local")
        return devices
    
    print(f"📍 Escaneando red: {network_base}")
    
    # Generar lista de IPs a escanear
    try:
        network = IPv4Network(network_base, strict=False)
        ips = [str(ip) for ip in network.hosts()][:254]  # Limitar a 254 IPs
    except:
        # Fallback manual
        parts = local_ip.split('.')
        ips = [f"{parts[0]}.{parts[1]}.{parts[2]}.{i}" for i in range(1, 255)]
    
    print(f"📡 Probando {len(ips)} direcciones IP...")
    
    def test_ip(ip):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.settimeout(0.5)
            sock.bind(('', 0))  # Puerto aleatorio
            local_port = sock.getsockname()[1]
            
            sock.sendto(DISCOVER_MSG.encode('utf-8'), (ip, UDP_PORT))
            
            try:
                data, addr = sock.recvfrom(1024)
                response = data.decode('utf-8', errors='ignore')
                if RESPONSE_MSG in response:
                    parts = response.split('|')
                    return {
                        'method': 'UDP Direct',
                        'ip': parts[1] if len(parts) > 1 else ip,
                        'device_id': parts[2] if len(parts) > 2 else 'UNKNOWN',
                        'device_name': parts[3] if len(parts) > 3 else 'Reefer',
                    }
            except socket.timeout:
                pass
            finally:
                sock.close()
        except:
            pass
        return None
    
    # Escanear en paralelo (limitado para no saturar)
    found = []
    chunk_size = 10
    for i in range(0, len(ips), chunk_size):
        chunk = ips[i:i+chunk_size]
        results = [test_ip(ip) for ip in chunk]
        found.extend([r for r in results if r])
        if found:
            for device in found:
                print(f"✅ Encontrado: {device['ip']} ({device['device_name']})")
        sys.stdout.write(f"\r📊 Progreso: {min(i+chunk_size, len(ips))}/{len(ips)} IPs escaneadas...")
        sys.stdout.flush()
    
    print()  # Nueva línea
    return found

def method3_http_scan(timeout=1):
    """Método 3: Buscar servidores HTTP en puerto 80"""
    print_section("MÉTODO 3: Escaneo HTTP (Puerto 80)")
    
    devices = []
    local_ip, network_base, _ = get_local_network_info()
    
    if not network_base:
        return devices
    
    try:
        network = IPv4Network(network_base, strict=False)
        ips = [str(ip) for ip in network.hosts()][:254]
    except:
        parts = local_ip.split('.')
        ips = [f"{parts[0]}.{parts[1]}.{parts[2]}.{i}" for i in range(1, 255)]
    
    print(f"🌐 Probando servidores HTTP en {len(ips)} IPs...")
    
    def test_http(ip):
        try:
            url = f"http://{ip}/api/status"
            req = urllib.request.Request(url)
            req.add_header('User-Agent', 'Reefer-Discovery/1.0')
            req.timeout = timeout
            
            with urllib.request.urlopen(req, timeout=timeout) as response:
                data = json.loads(response.read().decode())
                
                # Verificar si es un ESP32 Reefer
                if 'device' in data or 'sensor' in data or 'system' in data:
                    device_id = data.get('device', {}).get('id', 'UNKNOWN')
                    device_name = data.get('device', {}).get('name', 'Reefer')
                    
                    return {
                        'method': 'HTTP Scan',
                        'ip': ip,
                        'device_id': device_id,
                        'device_name': device_name,
                    }
        except:
            pass
        return None
    
    found = []
    for i, ip in enumerate(ips):
        result = test_http(ip)
        if result:
            found.append(result)
            print(f"✅ HTTP encontrado: {result['ip']} ({result['device_name']})")
        
        if (i + 1) % 50 == 0:
            sys.stdout.write(f"\r📊 Progreso: {i+1}/{len(ips)} IPs...")
            sys.stdout.flush()
    
    print()
    return found

def method4_mdns():
    """Método 4: Intentar mDNS (reefer.local)"""
    print_section("MÉTODO 4: mDNS (reefer.local)")
    
    devices = []
    
    try:
        import socket
        
        # Intentar resolver mDNS
        try:
            ip = socket.gethostbyname('reefer.local')
            print(f"✅ mDNS resuelto: reefer.local -> {ip}")
            
            # Verificar que responde
            url = f"http://{ip}/api/status"
            req = urllib.request.Request(url)
            req.timeout = 2
            
            with urllib.request.urlopen(req, timeout=2) as response:
                data = json.loads(response.read().decode())
                devices.append({
                    'method': 'mDNS',
                    'ip': ip,
                    'device_id': data.get('device', {}).get('id', 'UNKNOWN'),
                    'device_name': data.get('device', {}).get('name', 'Reefer'),
                })
                print(f"✅ Confirmado: {ip} responde correctamente")
        except socket.gaierror:
            print("ℹ️  reefer.local no resuelve (normal si mDNS no está configurado)")
        except Exception as e:
            print(f"[DEBUG] Error con mDNS: {e}")
    except ImportError:
        print("[SKIP] mDNS no disponible (requiere biblioteca adicional)")
    
    return devices

def main():
    print("=" * 70)
    print("  🔍 DIAGNÓSTICO AVANZADO - BUSCAR ESP32 REEFER")
    print("=" * 70)
    
    local_ip, network_base, broadcast = get_local_network_info()
    if local_ip:
        print(f"\n📍 Información de red:")
        print(f"   IP Local: {local_ip}")
        print(f"   Red: {network_base}")
        print(f"   Broadcast: {broadcast}")
    
    all_devices = []
    
    # Método 1: UDP Broadcast
    devices1 = method1_udp_broadcast(timeout=5)
    all_devices.extend(devices1)
    
    if not all_devices:
        # Método 2: Escaneo UDP directo (más lento pero más confiable)
        devices2 = method2_udp_direct_scan(timeout=1)
        all_devices.extend(devices2)
    
    if not all_devices:
        # Método 3: Escaneo HTTP
        devices3 = method3_http_scan(timeout=1)
        all_devices.extend(devices3)
    
    # Método 4: mDNS
    devices4 = method4_mdns()
    all_devices.extend(devices4)
    
    # Remover duplicados
    unique_devices = []
    seen_ips = set()
    for device in all_devices:
        if device['ip'] not in seen_ips:
            unique_devices.append(device)
            seen_ips.add(device['ip'])
    
    # RESULTADO FINAL
    print_section("RESULTADO FINAL")
    
    if unique_devices:
        print(f"✅ TOTAL ENCONTRADOS: {len(unique_devices)} dispositivo(s)\n")
        
        for i, device in enumerate(unique_devices, 1):
            print(f"{i}. {device['device_name']} ({device['device_id']})")
            print(f"   IP: {device['ip']}")
            print(f"   URL: http://{device['ip']}")
            print(f"   Método: {device['method']}")
            print()
        
        print("💡 Accedé desde tu navegador usando la URL mostrada arriba")
    else:
        print("❌ NO SE ENCONTRARON DISPOSITIVOS\n")
        print("🔧 Posibles causas:")
        print("   1. El ESP32 no está encendido o conectado al WiFi")
        print("   2. Estás en una red diferente")
        print("   3. Firewall bloqueando UDP/HTTP")
        print("   4. El firmware no tiene UDP Discovery activo")
        print()
        print("💡 Soluciones:")
        print("   • Verificá que el ESP32 esté encendido")
        print("   • Conectate a la misma red WiFi que el ESP32")
        print("   • Desactivá temporalmente el firewall")
        print("   • Si conocés la IP del ESP32, probá: http://[IP]/api/status")
        print()
        print("📝 Para más ayuda, verificá el Serial Monitor del ESP32")
        print("   Deberías ver: [OK] UDP Discovery escuchando en puerto 5555")
    
    print("=" * 70)
    
    return 0 if unique_devices else 1

if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\n[INFO] Interrumpido por el usuario")
        sys.exit(1)
