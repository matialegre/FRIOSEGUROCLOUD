# 📋 Guía de Instalación - Sistema Parametican Silver

## Requisitos de Hardware

### EMISOR (por cada RIFT)
- **ESP8266 NodeMCU** (ESP-12E)
- **2x DS18B20** (sensores de temperatura waterproof, rango -55°C a +125°C)
- **1x Reed Switch** (sensor magnético para puerta)
- **1x Resistencia 4.7kΩ** (pull-up para OneWire)
- Cables, caja estanca, fuente 5V

### RECEPTOR (uno central)
- **ESP32 DevKit** (cualquier variante)
- Fuente 5V
- Conexión a la red WiFi del campamento

## Requisitos de Software

### Arduino IDE
1. Descargar Arduino IDE: https://www.arduino.cc/en/software
2. Instalar soporte para ESP8266:
   - Ir a `Archivo > Preferencias`
   - En "URLs adicionales de gestor de tarjetas" agregar:
     ```
     http://arduino.esp8266.com/stable/package_esp8266com_index.json
     ```
3. Instalar soporte para ESP32:
   - Agregar también:
     ```
     https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
     ```
4. Ir a `Herramientas > Placa > Gestor de tarjetas`
   - Buscar e instalar "esp8266"
   - Buscar e instalar "esp32"

### Librerías necesarias
Ir a `Herramientas > Administrar bibliotecas` e instalar:

| Librería | Versión | Uso |
|----------|---------|-----|
| OneWire | 2.3.7+ | Comunicación con DS18B20 |
| DallasTemperature | 3.9.0+ | Lectura de sensores |
| ArduinoJson | 6.21.0+ | Manejo de JSON |
| ESP8266WiFi | (incluida) | WiFi para ESP8266 |
| ESP8266HTTPClient | (incluida) | HTTP para ESP8266 |
| WiFi (ESP32) | (incluida) | WiFi para ESP32 |
| WebServer (ESP32) | (incluida) | Servidor web |
| SPIFFS | (incluida) | Sistema de archivos |
| Preferences | (incluida) | Almacenamiento persistente |

## Conexiones de Hardware

### EMISOR - ESP8266 NodeMCU

```
                    ESP8266 NodeMCU
                    ┌─────────────┐
                    │             │
    DS18B20 #1 ─────┤ D4 (GPIO2)  │
    DS18B20 #2 ─────┤             │
         │          │             │
         └──[4.7kΩ]─┤ 3.3V        │
                    │             │
    Reed Switch ────┤ D5 (GPIO14) │
         │          │             │
         └──────────┤ GND         │
                    │             │
    DS18B20 VCC ────┤ 3.3V        │
    DS18B20 GND ────┤ GND         │
                    └─────────────┘
```

**Diagrama DS18B20:**
```
    ┌─────────────┐
    │   DS18B20   │
    │  (vista     │
    │  frontal)   │
    └──┬──┬──┬────┘
       │  │  │
      GND DQ VCC
       │  │  │
       │  │  └── 3.3V
       │  └───── D4 + Resistencia 4.7kΩ a 3.3V
       └──────── GND
```

### RECEPTOR - ESP32
Solo necesita alimentación y conexión WiFi. No requiere sensores adicionales.

## Configuración del Código

### 1. EMISOR (emisor.ino)

Editar las siguientes líneas:

```cpp
// WiFi del campamento
const char* WIFI_SSID = "PARAMETICAN_WIFI";      // ← Cambiar
const char* WIFI_PASSWORD = "password123";        // ← Cambiar

// IP del receptor ESP32
const char* RECEPTOR_IP = "192.168.1.100";        // ← Verificar

// Identificación de este RIFT
const int RIFT_ID = 1;                            // ← 1-6 según el RIFT
const char* RIFT_NAME = "RIFT-01";                // ← Nombre descriptivo
const char* RIFT_LOCATION = "Deposito Principal"; // ← Ubicación
```

### 2. RECEPTOR (receptor.ino)

Editar las siguientes líneas:

```cpp
// WiFi del campamento
const char* WIFI_SSID = "PARAMETICAN_WIFI";      // ← Cambiar
const char* WIFI_PASSWORD = "password123";        // ← Cambiar

// IP fija del receptor
IPAddress local_IP(192, 168, 1, 100);            // ← Ajustar según red
IPAddress gateway(192, 168, 1, 1);               // ← Gateway de la red
IPAddress subnet(255, 255, 255, 0);

// Telegram (opcional)
const char* TELEGRAM_BOT_TOKEN = "TU_BOT_TOKEN"; // ← Ver sección Telegram
const char* TELEGRAM_CHAT_ID = "TU_CHAT_ID";     // ← Ver sección Telegram

// Supabase (opcional)
const char* SUPABASE_URL = "https://xxx.supabase.co";  // ← Tu proyecto
const char* SUPABASE_KEY = "tu-anon-key";              // ← Tu API key
```

## Subir el Código

### EMISOR (ESP8266)

1. Conectar ESP8266 por USB
2. Abrir `emisor/emisor.ino` en Arduino IDE
3. Seleccionar:
   - Placa: `NodeMCU 1.0 (ESP-12E Module)`
   - Puerto: El COM correspondiente
   - Upload Speed: `115200`
4. Click en "Subir"
5. Abrir Monitor Serie (115200 baud) para verificar

### RECEPTOR (ESP32)

1. Conectar ESP32 por USB
2. Abrir `receptor/receptor.ino` en Arduino IDE
3. Seleccionar:
   - Placa: `ESP32 Dev Module`
   - Puerto: El COM correspondiente
4. **IMPORTANTE**: Subir archivos SPIFFS primero:
   - Instalar plugin: https://github.com/me-no-dev/arduino-esp32fs-plugin
   - Ir a `Herramientas > ESP32 Sketch Data Upload`
   - Esto sube la carpeta `data/` con el HTML
5. Click en "Subir" para el código
6. Abrir Monitor Serie para verificar

## Configuración de Telegram (Alertas)

### Crear Bot
1. Abrir Telegram y buscar `@BotFather`
2. Enviar `/newbot`
3. Seguir instrucciones (nombre, username)
4. Copiar el **token** que te da

### Obtener Chat ID
1. Buscar `@userinfobot` en Telegram
2. Enviar `/start`
3. Copiar tu **ID** numérico

### Configurar en el código
```cpp
const char* TELEGRAM_BOT_TOKEN = "123456789:ABCdefGHIjklMNOpqrsTUVwxyz";
const char* TELEGRAM_CHAT_ID = "987654321";
```

## Configuración de Supabase (Historial Cloud)

### Crear proyecto
1. Ir a https://supabase.com
2. Crear cuenta y nuevo proyecto
3. Esperar que se inicialice

### Crear tablas
1. Ir a `SQL Editor` en el dashboard
2. Copiar y ejecutar el contenido de `supabase/schema.sql`

### Obtener credenciales
1. Ir a `Settings > API`
2. Copiar:
   - **Project URL**: `https://xxx.supabase.co`
   - **anon public key**: `eyJhbGciOiJIUzI1NiIs...`

### Configurar en el código
```cpp
const char* SUPABASE_URL = "https://xxx.supabase.co";
const char* SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIs...";
```

## Verificación

### Checklist de funcionamiento

- [ ] EMISOR conecta a WiFi (LED parpadea lento)
- [ ] EMISOR detecta sensores DS18B20 (ver Monitor Serie)
- [ ] EMISOR envía datos cada 30 segundos
- [ ] RECEPTOR conecta a WiFi con IP fija
- [ ] RECEPTOR muestra página web en `http://192.168.1.100`
- [ ] Dashboard muestra RIFT-01 como ONLINE
- [ ] Temperaturas se actualizan en tiempo real
- [ ] Sensor de puerta funciona (abrir/cerrar)
- [ ] Alertas de Telegram llegan (si está configurado)

### Solución de problemas

| Problema | Solución |
|----------|----------|
| No detecta sensores | Verificar conexiones, resistencia 4.7kΩ |
| No conecta WiFi | Verificar SSID/password, distancia al router |
| No llegan datos al receptor | Verificar IP del receptor, firewall |
| Página web no carga | Verificar que se subió SPIFFS |
| No llegan alertas Telegram | Verificar token y chat_id, internet |

## Mantenimiento

- **Calibración**: Los DS18B20 vienen calibrados de fábrica (±0.5°C)
- **Batería**: Si se usa batería, considerar deep sleep
- **Limpieza**: Limpiar sensores periódicamente
- **Logs**: Revisar Supabase para historial largo plazo
