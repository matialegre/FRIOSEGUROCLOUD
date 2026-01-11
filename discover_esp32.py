#!/usr/bin/env python3
"""
Script para descubrir ESP32 Reefer en la red local
Usa el sistema UDP Discovery implementado en el firmware
"""

import socket
import json
import sys
from datetime import datetime

# Configuración del sistema de discovery
UDP_DISCOVERY_PORT = 5555
UDP_DISCOVERY_MAGIC = "REEFER_DISCOVER"
UDP_DISCOVERY_RESPONSE = "REEFER_HERE"

def get_local_ip():
    """Obtiene la IP local de la PC"""
    try:
        # Conecta a una IP externa para determinar la IP local
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        return local_ip
    except Exception as e:
        print(f"[ERROR] No se pudo obtener IP local: {e}")
        return None

def get_broadcast_address(local_ip):
    """Obtiene la dirección de broadcast basada en la IP local"""
    parts = local_ip.split('.')
    if len(parts) == 4:
        # Asume máscara /24 (255.255.255.0)
        return f"{parts[0]}.{parts[1]}.{parts[2]}.255"
    return "255.255.255.255"

def discover_reefers(timeout=3, broadcast=True):
    """
    Descubre dispositivos ESP32 Reefer en la red
    
    Args:
        timeout: Tiempo de espera en segundos para recibir respuestas
        broadcast: Si True, envía broadcast. Si False, solo escucha.
    """
    print("=" * 60)
    print("  🔍 BUSCANDO ESP32 REEFER EN LA RED")
    print("=" * 60)
    print()
    
    # Obtener IP local
    local_ip = get_local_ip()
    if local_ip:
        print(f"📍 IP Local: {local_ip}")
    
    # Crear socket UDP
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    
    # IMPORTANTE: Bind a un puerto específico conocido antes de enviar
    # El ESP32 responde al puerto desde donde vino el mensaje
    sock.bind(('', 5556))  # Puerto conocido para recibir respuestas
    sock.settimeout(timeout)
    
    devices_found = []
    
    try:
        if broadcast:
            # Enviar mensaje de discovery por broadcast
            broadcast_addr = get_broadcast_address(local_ip) if local_ip else "255.255.255.255"
            print(f"📡 Enviando discovery por broadcast a {broadcast_addr}...")
            print()
            
            message = UDP_DISCOVERY_MAGIC.encode('utf-8')
            sock.sendto(message, (broadcast_addr, UDP_DISCOVERY_PORT))
        else:
            # Solo escuchar sin enviar (por si algún dispositivo envía periódicamente)
            print("👂 Escuchando respuestas (sin enviar broadcast)...")
            print()
        
        # Escuchar respuestas
        start_time = datetime.now()
        print(f"⏳ Esperando respuestas (timeout: {timeout}s)...")
        print()
        
        while True:
            try:
                data, addr = sock.recvfrom(1024)
                response = data.decode('utf-8', errors='ignore')
                
                # Verificar si es una respuesta válida
                if UDP_DISCOVERY_RESPONSE in response:
                    # Parsear respuesta: "REEFER_HERE|IP|DEVICE_ID|DEVICE_NAME"
                    parts = response.split('|')
                    
                    device_info = {
                        'ip': parts[1] if len(parts) > 1 else addr[0],
                        'device_id': parts[2] if len(parts) > 2 else 'UNKNOWN',
                        'device_name': parts[3] if len(parts) > 3 else 'Reefer',
                        'port': addr[1],
                        'response_raw': response
                    }
                    
                    devices_found.append(device_info)
                    
                    # Mostrar dispositivo encontrado
                    print("✅ DISPOSITIVO ENCONTRADO:")
                    print(f"   IP: {device_info['ip']}")
                    print(f"   ID: {device_info['device_id']}")
                    print(f"   Nombre: {device_info['device_name']}")
                    print(f"   URL: http://{device_info['ip']}")
                    print(f"   mDNS: http://reefer.local (si está configurado)")
                    print()
                    
            except socket.timeout:
                # Timeout - no hay más respuestas
                elapsed = (datetime.now() - start_time).total_seconds()
                break
            except Exception as e:
                print(f"[ERROR] Error recibiendo datos: {e}")
                break
                
    except KeyboardInterrupt:
        print("\n[INFO] Búsqueda cancelada por el usuario")
    except Exception as e:
        print(f"[ERROR] Error en la búsqueda: {e}")
    finally:
        sock.close()
    
    return devices_found

def main():
    """Función principal"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Descubre dispositivos ESP32 Reefer en la red local',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos:
  python discover_esp32.py              # Búsqueda rápida (3s timeout)
  python discover_esp32.py -t 5         # Timeout de 5 segundos
  python discover_esp32.py -t 10        # Timeout de 10 segundos (más tiempo)
  python discover_esp32.py --listen     # Solo escuchar sin enviar broadcast
        """
    )
    
    parser.add_argument(
        '-t', '--timeout',
        type=int,
        default=3,
        help='Tiempo de espera en segundos (default: 3)'
    )
    
    parser.add_argument(
        '--listen',
        action='store_true',
        help='Solo escuchar sin enviar broadcast'
    )
    
    parser.add_argument(
        '--json',
        action='store_true',
        help='Mostrar resultado en formato JSON'
    )
    
    args = parser.parse_args()
    
    # Buscar dispositivos
    devices = discover_reefers(timeout=args.timeout, broadcast=not args.listen)
    
    # Mostrar resumen
    print("=" * 60)
    if devices:
        print(f"✅ TOTAL ENCONTRADOS: {len(devices)} dispositivo(s)")
        print()
        
        if args.json:
            # Salida JSON para scripts
            print(json.dumps(devices, indent=2))
        else:
            # Resumen legible
            print("📋 RESUMEN:")
            for i, dev in enumerate(devices, 1):
                print(f"  {i}. {dev['device_name']} ({dev['device_id']})")
                print(f"     → http://{dev['ip']}")
                print()
            
            print("💡 Tip: Copiá la IP y accedé desde el navegador")
            print("   O usá mDNS si está configurado: http://reefer.local")
    else:
        print("❌ NO SE ENCONTRARON DISPOSITIVOS")
        print()
        print("💡 Sugerencias:")
        print("   1. Verificá que el ESP32 esté encendido y conectado a la red WiFi")
        print("   2. Asegurate de estar en la misma red que el ESP32")
        print("   3. Intentá con un timeout mayor: python discover_esp32.py -t 10")
        print("   4. Verificá el firewall - necesita permitir UDP en el puerto 5555")
    
    print("=" * 60)
    
    return 0 if devices else 1

if __name__ == "__main__":
    sys.exit(main())
