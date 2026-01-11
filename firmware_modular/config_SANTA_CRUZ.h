/*
 * CONFIGURACIÓN PARA SANTA CRUZ - PRODUCCIÓN
 * Campamento Parametican Silver
 * 
 * INSTRUCCIONES:
 * 1. Copiar este archivo como config.h
 * 2. Cambiar DEVICE_ID según el reefer (REEFER_01_SCZ, REEFER_02_SCZ, etc.)
 * 3. Cambiar DEVICE_NAME según corresponda
 * 4. Compilar y subir al ESP32
 */

#ifndef CONFIG_H
#define CONFIG_H

// ============================================
// WIFIMANAGER - Configuración del Access Point
// ============================================
#define AP_NAME "Reefer-Setup"
#define AP_PASSWORD "reefer1234"
#define AP_TIMEOUT 180

// ============================================
// mDNS - Acceso por nombre en vez de IP
// ============================================
#define MDNS_NAME "reefer"

// ============================================
// DEVICE ID - CAMBIAR PARA CADA REEFER
// ============================================
// Opciones para Santa Cruz:
//   REEFER_01_SCZ - Reefer Principal
//   REEFER_02_SCZ - Reefer Carnes
//   REEFER_03_SCZ - Reefer Lácteos
//   REEFER_04_SCZ - Reefer Verduras
//   REEFER_05_SCZ - Reefer Bebidas
//   REEFER_06_SCZ - Reefer Backup

#define DEVICE_ID "REEFER_01_SCZ"       // <-- CAMBIAR PARA CADA REEFER
#define DEVICE_NAME "Reefer Principal"   // <-- CAMBIAR NOMBRE

// ============================================
// TELEGRAM
// ============================================
#define TELEGRAM_BOT_TOKEN "8175168657:AAE5HJBnp4Hx6LOECBh7Ps3utw35WMRdGnI"
#define TELEGRAM_CHAT_ID "7713503644"

const char* TELEGRAM_CHAT_IDS[] = {
    "7713503644",
};
const int TELEGRAM_CHAT_COUNT = 1;

// ============================================
// SUPABASE
// ============================================
#define SUPABASE_URL "https://xhdeacnwdzvkivfjzard.supabase.co"
#define SUPABASE_ANON_KEY "sb_publishable_JhTUv1X2LHMBVILUaysJ3g_Ho11zu-Q"
#define SUPABASE_SYNC_INTERVAL 5000

// ============================================
// PINES ESP32
// ============================================
#define PIN_ONEWIRE 4
#define PIN_DHT22 18
#define PIN_DOOR_SENSOR 5
#define PIN_DOOR PIN_DOOR_SENSOR
#define PIN_RELAY 26
#define PIN_LED_OK 2
#define PIN_LED_ALERT 15
#define PIN_BUZZER 17
#define PIN_WIFI_RESET 0
#define PIN_DEFROST_INPUT 33
#define DEFAULT_DEFROST_PIN_NC false

// ============================================
// SENSORES HABILITADOS
// ============================================
#define SENSOR_DS18B20_1_ENABLED true
#define SENSOR_DS18B20_2_ENABLED false
#define SENSOR_DHT22_ENABLED false
#define SENSOR_DOOR_ENABLED false

// ============================================
// UMBRALES
// ============================================
#define DEFAULT_TEMP_MIN -40.0
#define DEFAULT_TEMP_MAX -18.0
#define DEFAULT_TEMP_CRITICAL -10.0
#define DEFAULT_ALERT_DELAY_SEC 300
#define DEFAULT_DOOR_OPEN_MAX_SEC 180
#define DEFAULT_DEFROST_COOLDOWN_MIN 30

// ============================================
// UBICACIÓN - SANTA CRUZ
// ============================================
#define LOCATION_NAME "Campamento Parametican Silver"
#define LOCATION_DETAIL "Cerro Moro, Santa Cruz, Argentina"
#define LOCATION_LAT -48.130438
#define LOCATION_LON -66.652895

// ============================================
// CONFIGURACIÓN DEL SISTEMA
// ============================================
#define SENSOR_READ_INTERVAL_MS 5000
#define INTERNET_CHECK_INTERVAL_MS 30000
#define HISTORY_MAX_POINTS 288

// ============================================
// RELAY
// ============================================
#define RELAY_ON LOW
#define RELAY_OFF HIGH
#define RELAY_PULSE_ON_CONNECT
#define RELAY_PULSE_DURATION_MS 1000

// ============================================
// MODO SIMULACIÓN
// ============================================
#define SIMULATION_MODE false

#endif
