/*
 * CONFIGURACIÓN - Sistema Monitoreo Reefer
 * Campamento Parametican Silver
 * 
 * IMPORTANTE: El WiFi se configura automáticamente via WiFiManager
 * Si no hay WiFi configurado, el ESP32 crea un AP llamado "Reefer-Setup"
 * Conectate a ese AP y entrá a 192.168.4.1 para configurar
 */

#ifndef CONFIG_H
#define CONFIG_H

// ============================================
// WIFIMANAGER - Configuración del Access Point
// ============================================
#define AP_NAME "Reefer-Setup"         // Nombre del AP cuando no hay WiFi
#define AP_PASSWORD "reefer1234"       // Password del AP (mínimo 8 caracteres)
#define AP_TIMEOUT 180                 // Segundos antes de reiniciar si no se configura

// ============================================
// mDNS - Acceso por nombre en vez de IP
// ============================================
// Con esto configurado, podés acceder a: http://reefer.local
// Funciona en la mayoría de dispositivos (iOS, macOS, Windows 10+, Linux con Avahi)
#define MDNS_NAME "reefer"             // Acceso via http://reefer.local

// ============================================
// DEVICE ID - Identificador único de este Reefer
// ============================================
#define DEVICE_ID "REEFER-01"          // Cambiar para cada Reefer: REEFER-01, REEFER-02, etc.
#define DEVICE_NAME "Reefer Principal" // Nombre descriptivo

// ============================================
// TELEGRAM (para alertas cuando hay internet)
// ============================================
// Bot: @FrioSeguro_bot
#define TELEGRAM_BOT_TOKEN "8175168657:AAE5HJBnp4Hx6LOECBh7Ps3utw35WMRdGnI"

// Chat ID principal para alertas
#define TELEGRAM_CHAT_ID "7713503644"

// Chat IDs de los usuarios que recibirán alertas (para múltiples usuarios)
const char* TELEGRAM_CHAT_IDS[] = {
    "7713503644",  // Usuario 1
    // "7713503645",  // Usuario 2
    // "7713503646",  // Usuario 3
};
const int TELEGRAM_CHAT_COUNT = 1;  // Cantidad de chat IDs activos

// ============================================
// SUPABASE (para historial en la nube)
// ============================================
#define SUPABASE_URL "https://xhdeacnwdzvkivfjzard.supabase.co"
#define SUPABASE_ANON_KEY "sb_publishable_JhTUv1X2LHMBVILUaysJ3g_Ho11zu-Q"
#define SUPABASE_SYNC_INTERVAL 5000  // Enviar cada 5 segundos

// ============================================
// PINES ESP32
// ============================================
// Sensores de temperatura DS18B20 (hasta 3 en el mismo bus)
#define PIN_ONEWIRE 4          // GPIO4 - Bus OneWire para DS18B20

// Sensor DHT22 (opcional, para ambiente)
#define PIN_DHT22 18           // GPIO18 - DHT22 (temperatura + humedad)

// Sensor de puerta (Reed Switch)
#define PIN_DOOR_SENSOR 5      // GPIO5 - Reed switch (magnético)
#define PIN_DOOR PIN_DOOR_SENSOR  // Alias para compatibilidad

// Relay para sirena/luz de alerta
#define PIN_RELAY 26           // GPIO26 - Relay (0=ON, 1=OFF)

// LEDs de estado
#define PIN_LED_OK 2           // GPIO2 - LED verde (built-in en muchos ESP32)
#define PIN_LED_ALERT 15       // GPIO15 - LED rojo

// Buzzer pequeño (opcional)
#define PIN_BUZZER 17          // GPIO17 - Buzzer

// Botón de reset WiFi (mantener 5 segundos para borrar WiFi)
#define PIN_WIFI_RESET 0       // GPIO0 - Botón BOOT del ESP32

// Pin de entrada para modo descongelación (relé del sistema de frío)
// Configurable como NO (Normalmente Abierto) o NC (Normalmente Cerrado)
#define PIN_DEFROST_INPUT 33   // GPIO33 - Entrada para relé de descongelación (usar pull-up interno)

// Modo del contacto del relé de descongelación (configurable desde la web)
// false = NO (Normalmente Abierto): pin HIGH = normal, pin LOW = descongelación
// true = NC (Normalmente Cerrado): pin LOW = normal, pin HIGH = descongelación
#define DEFAULT_DEFROST_PIN_NC false  // Por defecto asumimos NO (Normalmente Abierto)

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

// Tiempo de espera después de descongelación (el sistema se enfría antes de reactivar monitoreo)
#define DEFAULT_DEFROST_COOLDOWN_MIN 30  // Minutos de espera después de descongelar (30 min por defecto)

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
// PULSO DE RELÉ AL CONECTAR WIFI
// ============================================
// Descomentar la siguiente línea para activar pulso de relé al conectar
#define RELAY_PULSE_ON_CONNECT
#define RELAY_PULSE_DURATION_MS 1000  // Duración del pulso en milisegundos

// ============================================
// MODO SIMULACIÓN (para testing sin sensores)
// ============================================
#define SIMULATION_MODE false  // Cambiar a true para simular sensores

#endif
