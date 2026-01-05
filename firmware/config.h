/*
 * CONFIGURACIÓN - Sistema Monitoreo RIFT
 * Campamento Parametican Silver
 * 
 * IMPORTANTE: El WiFi se configura automáticamente via WiFiManager
 * Si no hay WiFi configurado, el ESP32 crea un AP llamado "RIFT-Setup"
 * Conectate a ese AP y entrá a 192.168.4.1 para configurar
 */

#ifndef CONFIG_H
#define CONFIG_H

// ============================================
// WIFIMANAGER - Configuración del Access Point
// ============================================
#define AP_NAME "RIFT-Setup"           // Nombre del AP cuando no hay WiFi
#define AP_PASSWORD "rift1234"         // Password del AP (mínimo 8 caracteres)
#define AP_TIMEOUT 180                 // Segundos antes de reiniciar si no se configura

// ============================================
// mDNS - Acceso por nombre en vez de IP
// ============================================
// Con esto configurado, podés acceder a: http://rift.local
// Funciona en la mayoría de dispositivos (iOS, macOS, Windows 10+, Linux con Avahi)
#define MDNS_NAME "rift"               // Acceso via http://rift.local

// ============================================
// DEVICE ID - Identificador único de este RIFT
// ============================================
#define DEVICE_ID "RIFT-01"            // Cambiar para cada RIFT: RIFT-01, RIFT-02, etc.
#define DEVICE_NAME "RIFT Principal"   // Nombre descriptivo

// ============================================
// TELEGRAM (para alertas cuando hay internet)
// ============================================
// Bot: @FrioSeguro_bot
#define TELEGRAM_BOT_TOKEN "8175168657:AAE5HJBnp4Hx6LOECBh7Ps3utw35WMRdGnI"

// Chat IDs de los usuarios que recibirán alertas
// Para obtener tu chat ID: habla con @userinfobot en Telegram
const char* TELEGRAM_CHAT_IDS[] = {
    "7713503644",  // Usuario 1
    // "7713503645",  // Usuario 2
    // "7713503646",  // Usuario 3
};
const int TELEGRAM_CHAT_COUNT = 1;  // Cantidad de chat IDs activos

// ============================================
// SUPABASE (para historial en la nube)
// ============================================
#define SUPABASE_URL "https://tkgszodjnkqxwdfgcvbg.supabase.co"
#define SUPABASE_ANON_KEY "TU_ANON_KEY_AQUI"  // Pasame la anon key de Supabase

// ============================================
// PINES ESP32
// ============================================
// Sensores de temperatura DS18B20 (hasta 3 en el mismo bus)
#define PIN_ONEWIRE 4          // GPIO4 - Bus OneWire para DS18B20

// Sensor DHT22 (opcional, para ambiente)
#define PIN_DHT22 18           // GPIO18 - DHT22 (temperatura + humedad)

// Sensor de puerta (Reed Switch)
#define PIN_DOOR_SENSOR 5      // GPIO5 - Reed switch (magnético)

// Relay para sirena/luz de alerta
#define PIN_RELAY 16           // GPIO16 - Relay (0=ON, 1=OFF)

// LEDs de estado
#define PIN_LED_OK 2           // GPIO2 - LED verde (built-in en muchos ESP32)
#define PIN_LED_ALERT 15       // GPIO15 - LED rojo

// Buzzer pequeño (opcional)
#define PIN_BUZZER 17          // GPIO17 - Buzzer

// Botón de reset WiFi (mantener 5 segundos para borrar WiFi)
#define PIN_WIFI_RESET 0       // GPIO0 - Botón BOOT del ESP32

// ============================================
// SENSORES HABILITADOS
// ============================================
// Cambiar a true/false según los sensores conectados
#define SENSOR_DS18B20_1_ENABLED true   // Primer DS18B20 (principal)
#define SENSOR_DS18B20_2_ENABLED false  // Segundo DS18B20 (redundancia)
#define SENSOR_DHT22_ENABLED false      // DHT22 para ambiente
#define SENSOR_DOOR_ENABLED false       // Sensor magnético de puerta (DESHABILITADO por defecto)

// ============================================
// UMBRALES POR DEFECTO (editables desde la web)
// ============================================
#define DEFAULT_TEMP_MIN -40.0       // Temperatura mínima esperada
#define DEFAULT_TEMP_MAX -18.0       // Umbral de alerta (por encima = warning)
#define DEFAULT_TEMP_CRITICAL -10.0  // Umbral crítico (por encima = alarma)

// Anti-falsos positivos
#define DEFAULT_ALERT_DELAY_SEC 300      // Segundos antes de alertar (5 min)
#define DEFAULT_DOOR_OPEN_MAX_SEC 180    // Máximo tiempo puerta abierta (3 min)

// ============================================
// UBICACIÓN DEL CAMPAMENTO
// ============================================
#define LOCATION_NAME "Campamento Parametican Silver"
#define LOCATION_DETAIL "Cerro Moro, Santa Cruz, Argentina"
#define LOCATION_LAT -48.130438
#define LOCATION_LON -66.652895

// ============================================
// CONFIGURACIÓN DEL SISTEMA
// ============================================
#define SENSOR_READ_INTERVAL_MS 5000     // Leer sensores cada 5 segundos
#define INTERNET_CHECK_INTERVAL_MS 30000 // Verificar internet cada 30 seg
#define HISTORY_MAX_POINTS 288           // Puntos de historial (24h a 5min c/u)

// ============================================
// RELAY - Lógica invertida
// ============================================
#define RELAY_ON LOW    // 0 = Encendido
#define RELAY_OFF HIGH  // 1 = Apagado

// ============================================
// MODO SIMULACIÓN (para testing sin sensores)
// ============================================
#define SIMULATION_MODE false  // Cambiar a true para simular sensores

#endif
