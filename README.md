# 🏔️ Sistema de Monitoreo RIFT - Campamento Parametican Silver

## Ubicación
**Cerro Moro, Santa Cruz, Argentina**
- Coordenadas: 48°7'49.5768" S, 66°39'10.4231" W
- Campamento minero de oro y plata
- +400 personas alojadas

---

## 🎯 Descripción del Sistema

Sistema **UNIFICADO** de monitoreo de temperatura para **RIFTs** (depósitos de comida refrigerados). Un solo ESP32 hace todo: sensores, web server, alertas locales y notificaciones.

### Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                      ESP32 UNIFICADO                        │
├─────────────────────────────────────────────────────────────┤
│  📡 WiFi Local (192.168.1.100)                              │
│  🌡️ Sensores DS18B20 (temperatura)                          │
│  🚪 Reed Switch (puerta)                                    │
│  🔔 Relay (sirena/luz 12V)                                  │
│  💡 LEDs (estado)                                           │
│  🔊 Buzzer (alerta sonora)                                  │
│  🌐 Web Server (dashboard completo)                         │
│  📱 API REST (para app Android)                             │
│  📨 Telegram (cuando hay internet)                          │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
    ┌─────────┐         ┌─────────┐         ┌─────────┐
    │ 📱 App  │         │ 💻 Web  │         │ 📨 Tg   │
    │ Android │         │ Browser │         │ Bot     │
    └─────────┘         └─────────┘         └─────────┘
```

### Características

✅ **Sistema unificado** - Un solo ESP32 hace todo  
✅ **Funciona 100% offline** - No necesita internet  
✅ **App Android nativa** - Alarma aunque el celular esté bloqueado  
✅ **Sirena/Luz 12V** - Relay para alerta física potente  
✅ **Anti-falsos positivos** - Ignora picos por apertura de puerta  
✅ **Telegram** - Alertas cuando hay internet disponible  
✅ **Dashboard web** - Diseño moderno con animaciones  
✅ **Configuración desde web/app** - Umbrales editables  
✅ **Mapa integrado** - Ubicación del campamento  

---

## 📁 Estructura del Proyecto

```
campamento-parametican/
├── firmware/                    # Código ESP32
│   ├── firmware.ino            # Código principal
│   ├── config.h                # Configuración (WiFi, pines, etc)
│   └── data/                   # Archivos para SPIFFS
│       └── index.html          # Dashboard web
├── android-app/                # App Android
│   ├── app/src/main/
│   │   ├── java/.../           # Código Kotlin
│   │   ├── res/                # Layouts y recursos
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── docs/                       # Documentación
│   ├── ALERTAS_LOCALES.md
│   └── LISTA_MATERIALES.md
├── .github/workflows/          # CI/CD
│   └── build-apk.yml          # Compila APK automáticamente
└── README.md
```

---

## 🔧 Hardware Necesario

### ESP32 + Sensores

| Componente | Cantidad | Pin | Precio Est. |
|------------|----------|-----|-------------|
| ESP32 DevKit | 1 | - | $8 |
| DS18B20 Waterproof | 2 | GPIO4 | $4 c/u |
| Reed Switch | 1 | GPIO5 | $1 |
| Resistencia 4.7kΩ | 1 | - | $0.10 |

### Alertas Locales

| Componente | Cantidad | Pin | Precio Est. |
|------------|----------|-----|-------------|
| Módulo Relay 5V | 1 | GPIO16 | $2 |
| Sirena 12V 110dB | 1 | Via relay | $12 |
| Fuente 12V 1A | 1 | - | $5 |
| LED Rojo | 1 | GPIO15 | $0.10 |
| LED Verde | 1 | GPIO2 | $0.10 |
| Buzzer 5V | 1 | GPIO17 | $1 |

**Total estimado: ~$40 USD**

---

## ⚡ Instalación Rápida

### 1. Configurar el ESP32

1. Abrí `firmware/config.h` y editá:
   ```cpp
   #define WIFI_SSID "TU_WIFI"
   #define WIFI_PASSWORD "TU_PASSWORD"
   #define TELEGRAM_BOT_TOKEN "TU_TOKEN"
   ```

2. En Arduino IDE:
   - Instalá las librerías: `OneWire`, `DallasTemperature`, `ArduinoJson`
   - Seleccioná: ESP32 Dev Module
   - Subí `firmware/firmware.ino`

3. Subí los archivos de `firmware/data/` a SPIFFS:
   - Herramientas → ESP32 Sketch Data Upload

### 2. Obtener la App Android

**Opción A: GitHub Actions (automático)**
1. Subí este proyecto a GitHub
2. El workflow compilará el APK automáticamente
3. Descargá desde Actions → Artifacts

**Opción B: Compilar localmente**
```bash
cd android-app
./gradlew assembleDebug
# APK en: app/build/outputs/apk/debug/
```

### 3. Configurar la App

1. Instalá el APK en los celulares
2. Abrí la app, ingresá la IP: `192.168.1.100`
3. Tocá "INICIAR MONITOREO"
4. ¡Listo! Recibirás alertas 24/7

---

## 🌐 API REST

El ESP32 expone estos endpoints:

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/` | GET | Dashboard web |
| `/api/status` | GET | Estado actual (temp, puerta, alertas) |
| `/api/config` | GET | Configuración actual |
| `/api/config` | POST | Actualizar configuración |
| `/api/history` | GET | Historial de temperatura |
| `/api/alert/ack` | POST | Silenciar alarma |
| `/api/alert/test` | POST | Probar alerta |
| `/api/relay` | POST | Control manual del relay |

### Ejemplo de respuesta `/api/status`:

```json
{
  "sensor": {
    "temp1": -22.5,
    "temp2": -21.8,
    "temp_avg": -22.15,
    "door_open": false,
    "door_open_sec": 0,
    "sensor_count": 2,
    "valid": true
  },
  "system": {
    "alert_active": false,
    "critical": false,
    "alert_message": "",
    "relay_on": false,
    "internet": true,
    "uptime_sec": 3600,
    "total_alerts": 0,
    "wifi_rssi": -65
  },
  "location": {
    "name": "Campamento Parametican Silver",
    "lat": -48.130438,
    "lon": -66.652895
  }
}
```

---

## 📱 Telegram Bot

Bot: **@FrioSeguro_bot**

### Configurar:
1. Obtené el token del bot desde BotFather o Netlify
2. Agregá tu Chat ID en `config.h`
3. El sistema enviará alertas cuando haya internet

### Mensajes que envía:
- 🚨 **CRÍTICO**: Temperatura > -10°C
- ⚠️ **Alerta**: Temperatura > -18°C por más de 5 min
- 🚪 **Puerta**: Abierta por más de 3 min
- ✅ **Resuelto**: Cuando se normaliza

---

## 🔔 Sistema de Alertas

### Niveles de alerta:

| Nivel | Condición | Acciones |
|-------|-----------|----------|
| **Normal** | Temp ≤ -18°C | LED verde, sin alarma |
| **Warning** | Temp > -18°C | LED rojo, espera 5 min |
| **Crítico** | Temp > -10°C | Sirena + Buzzer + Telegram |
| **Puerta** | Abierta > 3 min | Alerta sin sirena |

### Anti-falsos positivos:
- Si la puerta está abierta, no alerta por temperatura
- Espera 5 minutos antes de alertar (configurable)
- La sirena es intermitente (3s on, 2s off)

---

## 🛠️ Troubleshooting

### El ESP32 no conecta al WiFi
- Verificá SSID y password en `config.h`
- Asegurate que el router esté en 2.4GHz (no 5GHz)

### No detecta sensores de temperatura
- Verificá la resistencia de 4.7kΩ entre DATA y VCC
- Probá con un solo sensor primero

### La app Android no conecta
- Verificá que el celular esté en el mismo WiFi
- Probá acceder a `http://192.168.1.100` desde el navegador

### El relay no activa la sirena
- Recordá: el relay es lógica invertida (0=ON, 1=OFF)
- Verificá la fuente de 12V para la sirena

---

## 📄 Licencia

Proyecto desarrollado para Campamento Parametican Silver.
Cerro Moro, Santa Cruz, Argentina.
