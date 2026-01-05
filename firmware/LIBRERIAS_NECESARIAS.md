# Librerías Necesarias para el Firmware RIFT

## Librerías que YA VIENEN con ESP32 (no instalar):
- `WiFi.h`
- `ESPmDNS.h` 
- `WebServer.h`
- `SPIFFS.h`
- `Preferences.h`
- `HTTPClient.h`
- `time.h`

## Librerías a INSTALAR desde el Gestor de Bibliotecas:

### 1. WiFiManager (por tzapu)
- Abrir Arduino IDE
- Ir a: Herramientas > Administrar Bibliotecas
- Buscar: "WiFiManager"
- Instalar: **WiFiManager by tzapu** (versión 2.0.x o superior)

### 2. ArduinoJson (por Benoit Blanchon)
- Buscar: "ArduinoJson"
- Instalar: **ArduinoJson by Benoit Blanchon** (versión 6.x o 7.x)

### 3. OneWire (por Paul Stoffregen)
- Buscar: "OneWire"
- Instalar: **OneWire by Paul Stoffregen**

### 4. DallasTemperature (por Miles Burton)
- Buscar: "DallasTemperature"
- Instalar: **DallasTemperature by Miles Burton**

### 5. DHT sensor library (por Adafruit)
- Buscar: "DHT sensor library"
- Instalar: **DHT sensor library by Adafruit**
- También instalar: **Adafruit Unified Sensor** (dependencia)

## Configuración del ESP32 en Arduino IDE:

1. Ir a: Archivo > Preferencias
2. En "URLs adicionales de gestor de tarjetas" agregar:
   ```
   https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
   ```
3. Ir a: Herramientas > Placa > Gestor de tarjetas
4. Buscar "ESP32" e instalar **esp32 by Espressif Systems**

## Verificar instalación:
Una vez instaladas, el código debería compilar sin errores.
