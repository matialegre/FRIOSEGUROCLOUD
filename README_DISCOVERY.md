# 🔍 Scripts de Discovery para ESP32 Reefer

Herramientas en Python para encontrar y diagnosticar dispositivos ESP32 Reefer en la red local.

## 📚 Scripts Disponibles

| Script | Descripción | Cuándo Usar |
|--------|-------------|-------------|
| `discover_esp32_diagnostico.py` | **Diagnóstico avanzado** con múltiples métodos | ⭐ **RECOMENDADO** - Si no encuentra el dispositivo |
| `discover_esp32.py` | Búsqueda básica con UDP Discovery | Búsqueda rápida cuando funciona |
| `discover_esp32_simple.py` | Versión mínima (solo IP) | Script rápido para automatización |
| `probar_ip.py` | Prueba una IP específica | Si conocés la IP del ESP32 |
| `verificar_red.py` | Verifica configuración de red | Antes de buscar, para diagnosticar problemas |

## 🚀 Uso Rápido

**Si no encontrás el dispositivo (recomendado):**
```bash
python discover_esp32_diagnostico.py
```

**Si conocés la IP:**
```bash
python probar_ip.py 192.168.1.50
```

**Verificar red primero:**
```bash
python verificar_red.py
```

## 📋 Requisitos

- Python 3.6 o superior
- Estar en la misma red WiFi que el ESP32
- El ESP32 debe estar encendido y conectado

## 🚀 Uso Básico

### Versión completa (recomendada)

```bash
python discover_esp32.py
```

### Versión simplificada (solo muestra la IP)

```bash
python discover_esp32_simple.py
```

### En Windows (doble click)

Ejecutá `buscar_reefer.bat` - busca automáticamente y muestra el resultado.

## ⚙️ Opciones Avanzadas

### Con timeout personalizado

```bash
python discover_esp32.py -t 10
```

### Solo escuchar (sin enviar broadcast)

```bash
python discover_esp32.py --listen
```

### Salida en formato JSON (para scripts)

```bash
python discover_esp32.py --json
```

## 📤 Ejemplo de Salida

```
============================================================
  🔍 BUSCANDO ESP32 REEFER EN LA RED
============================================================

📍 IP Local: 192.168.1.100
📡 Enviando discovery por broadcast a 192.168.1.255...

⏳ Esperando respuestas (timeout: 3s)...

✅ DISPOSITIVO ENCONTRADO:
   IP: 192.168.1.50
   ID: REEFER-01
   Nombre: Reefer Principal
   URL: http://192.168.1.50
   mDNS: http://reefer.local (si está configurado)

============================================================
✅ TOTAL ENCONTRADOS: 1 dispositivo(s)

📋 RESUMEN:
  1. Reefer Principal (REEFER-01)
     → http://192.168.1.50

💡 Tip: Copiá la IP y accedé desde el navegador
   O usá mDNS si está configurado: http://reefer.local
============================================================
```

## 🔧 Cómo Funciona

1. El script envía un mensaje UDP `REEFER_DISCOVER` por broadcast (255.255.255.255)
2. Todos los ESP32 en la red que tengan el firmware instalado responden con `REEFER_HERE|IP|ID|NOMBRE`
3. El script muestra todos los dispositivos encontrados con su información

## ❓ Problemas Comunes - NO ENCUENTRA EL DISPOSITIVO

### 🔧 Pasos de Diagnóstico

**1. Verificá la red primero:**
```bash
python verificar_red.py
```
Este script verifica:
- ✅ IP local
- ✅ Permisos UDP
- ✅ Configuración de red

**2. Probá el diagnóstico avanzado:**
```bash
python discover_esp32_diagnostico.py
```
Este script prueba **4 métodos diferentes**:
- UDP Broadcast
- Escaneo UDP directo
- Escaneo HTTP (puerto 80)
- mDNS (reefer.local)

**3. Si conocés la IP del ESP32:**
```bash
python probar_ip.py 192.168.1.50
```
Reemplazá `192.168.1.50` con la IP real del ESP32.

**4. Verificaciones manuales:**
- ✅ Verificá que el ESP32 esté encendido
- ✅ Asegurate de estar en la **MISMA red WiFi** que el ESP32
- ✅ Verificá el firewall de Windows - debe permitir UDP en puerto 5555-5556
- ✅ Verificá el firewall - debe permitir HTTP en puerto 80
- ✅ Si usás VPN, desconectala temporalmente
- ✅ Verificá el Serial Monitor del ESP32 - debería mostrar:
  ```
  [OK] UDP Discovery escuchando en puerto 5555
  ```

**5. Soluciones específicas:**

**Windows Firewall:**
- Windows Defender → Firewall → Configuración avanzada
- Permitir UDP puertos 5555 y 5556
- Permitir HTTP puerto 80

**Linux/Mac - Permisos:**
```bash
sudo python3 discover_esp32_diagnostico.py
```

**Router/VPN:**
- Algunos routers bloquean broadcast UDP entre dispositivos
- Desactivá temporalmente el "AP Isolation" en el router
- Si usás VPN corporativa, desconectala

**IP conocida:**
Si sabés la IP del ESP32 (por ejemplo, desde el router), probá directamente:
```bash
python probar_ip.py 192.168.1.XXX
```

### Error de permisos en Linux/Mac

Ejecutá con sudo:
```bash
sudo python3 discover_esp32.py
```

### Python no encontrado en Windows

1. Descargá Python desde python.org
2. Durante la instalación, marcá "Add Python to PATH"
3. Reiniciá la terminal y probá de nuevo

## 📝 Integración con Otros Scripts

Para usar en scripts de automatización:

```python
import subprocess
import json

result = subprocess.run(
    ['python', 'discover_esp32.py', '--json', '-t', '5'],
    capture_output=True,
    text=True
)

devices = json.loads(result.stdout)
for device in devices:
    print(f"IP: {device['ip']}")
```

## 🔗 Más Información

Este script usa el sistema de UDP Discovery implementado en el firmware del ESP32 que escucha en el puerto **5555**.
