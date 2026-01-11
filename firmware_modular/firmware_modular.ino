/*
 * SISTEMA MONITOREO REEFER v3.0 - MODULAR
 * Campamento Parametican Silver
 * 
 * ARQUITECTURA MODULAR:
 * - config.h      : Configuración de hardware y constantes
 * - types.h       : Estructuras de datos
 * - storage.h     : Almacenamiento en Preferences
 * - sensors.h     : Lectura de sensores DS18B20, DHT22
 * - alerts.h      : Lógica de alertas y alarmas
 * - telegram.h    : Notificaciones Telegram
 * - supabase.h    : Integración con Supabase
 * - wifi_utils.h  : Gestión de WiFi
 * - web_api.h     : Servidor web y API REST
 * - html_ui.h     : Página HTML embebida
 */

// ============================================
// LIBRERÍAS ESTÁNDAR
// ============================================
#include <WiFi.h>
#include <WiFiManager.h>
#include <WiFiUdp.h>
#include <ESPmDNS.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <DHT.h>
#include <Preferences.h>

// ============================================
// CONFIGURACIÓN Y TIPOS
// ============================================
#include "config.h"
#include "types.h"

// ============================================
// OBJETOS GLOBALES
// ============================================
WebServer server(80);
WiFiManager wifiManager;
WiFiUDP udpDiscovery;
Preferences prefs;

OneWire oneWire(PIN_ONEWIRE);
DallasTemperature ds18b20(&oneWire);
DHT dht(PIN_DHT22, DHT22);

// ============================================
// VARIABLES GLOBALES
// ============================================
Config config;
SensorData sensorData;
SystemState state;

#define HISTORY_SIZE 60
HistoryPoint history[HISTORY_SIZE];
int historyIndex = 0;

// ============================================
// FORWARD DECLARATIONS
// ============================================
void setRelay(bool on);
void sendTelegramAlert(String message);
void sendAlertToSupabase(String alertType, String severity, String message);
void acknowledgeAlert();
void clearAlert();
void triggerAlert(String message, bool critical);
void saveConfig();
void loadConfig();
bool testTelegram();
void resetWiFi();
String getEmbeddedHTML();

// ============================================
// INCLUIR MÓDULOS
// ============================================
#include "html_ui.h"
#include "storage.h"
#include "telegram.h"
#include "supabase.h"
#include "sensors.h"
#include "alerts.h"
#include "wifi_utils.h"
#include "web_api.h"

// ============================================
// CONTROL DE RELÉ
// ============================================
void setRelay(bool on) {
  state.relayState = on;
  digitalWrite(PIN_RELAY, on ? RELAY_ON : RELAY_OFF);
  Serial.printf("[RELAY] %s\n", on ? "ENCENDIDO" : "APAGADO");
}

// ============================================
// INICIALIZACIÓN DE PINES
// ============================================
void initPins() {
  pinMode(PIN_RELAY, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_DOOR, INPUT_PULLUP);
  pinMode(PIN_DEFROST_INPUT, INPUT_PULLUP);  // Entrada para señal de defrost del reefer
  
  digitalWrite(PIN_RELAY, RELAY_OFF);
  digitalWrite(PIN_BUZZER, LOW);
  
  Serial.println("[OK] Pines inicializados");
  Serial.printf("[OK] Pin defrost: GPIO%d (modo %s)\n", PIN_DEFROST_INPUT, 
                config.defrostRelayNC ? "NC" : "NA");
}

// ============================================
// HISTORIAL DE TEMPERATURAS
// ============================================
void updateHistory() {
  static unsigned long lastHistoryUpdate = 0;
  
  if (millis() - lastHistoryUpdate >= 60000) {
    lastHistoryUpdate = millis();
    
    history[historyIndex].temp = sensorData.tempAvg;
    history[historyIndex].timestamp = millis();
    historyIndex = (historyIndex + 1) % HISTORY_SIZE;
  }
}

// ============================================
// SETUP
// ============================================
void setup() {
  Serial.begin(115200);
  delay(100);
  
  Serial.println("\n");
  Serial.println("╔════════════════════════════════════════╗");
  Serial.println("║   REEFER MONITOR v3.0 - MODULAR        ║");
  Serial.println("║   Campamento Parametican Silver        ║");
  Serial.println("╚════════════════════════════════════════╝");
  Serial.println();
  
  // Inicializar estado
  state.uptime = millis();
  state.alertActive = false;
  state.criticalAlert = false;
  state.alertAcknowledged = false;
  state.defrostMode = false;
  state.lastSupabaseSync = 0;
  
  // Cargar configuración
  loadConfig();
  
  // Forzar Supabase habilitado (para no tener que configurar manualmente)
  if (!config.supabaseEnabled) {
    config.supabaseEnabled = true;
    saveConfig();
    Serial.println("[CONFIG] Supabase habilitado automáticamente");
  }
  
  // Inicializar pines
  initPins();
  
  // Conectar WiFi
  connectWiFi();
  
  // Inicializar sensores
  Serial.println("\n[SENSORES] Inicializando...");
  initSensors();
  
  // Configurar mDNS
  setupMDNS();
  
  // Configurar servidor web
  setupWebServer();
  
  // Actualizar estado online en Supabase
  if (config.supabaseEnabled && state.internetAvailable) {
    supabaseUpdateDeviceStatus(true);
  }
  
  Serial.println("\n[SISTEMA] ✓ Inicialización completa");
  Serial.printf("[SISTEMA] IP: %s\n", state.localIP.c_str());
  Serial.printf("[SISTEMA] mDNS: http://%s.local\n", MDNS_NAME);
  Serial.printf("[SISTEMA] Supabase: %s\n", config.supabaseEnabled ? "HABILITADO" : "DESHABILITADO");
  Serial.println();
}

// ============================================
// IMPRIMIR ESTADO COMPLETO POR SERIAL (JSON)
// ============================================
void printStatusJSON() {
  StaticJsonDocument<1536> doc;
  
  // Identificación
  doc["device_id"] = DEVICE_ID;
  doc["device_name"] = DEVICE_NAME;
  doc["ip"] = state.localIP;
  doc["mdns"] = String(MDNS_NAME) + ".local";
  
  // Temperaturas
  JsonObject temps = doc.createNestedObject("temperatures");
  temps["temp1"] = sensorData.temp1;
  temps["temp2"] = sensorData.temp2;
  temps["temp_avg"] = sensorData.tempAvg;
  temps["temp_dht"] = sensorData.tempDHT;
  temps["humidity"] = sensorData.humidity;
  temps["sensor_count"] = sensorData.sensorCount;
  temps["valid"] = sensorData.valid;
  
  // Umbrales configurados
  JsonObject thresholds = doc.createNestedObject("thresholds");
  thresholds["temp_max"] = config.tempMax;
  thresholds["temp_critical"] = config.tempCritical;
  thresholds["alert_delay_sec"] = config.alertDelaySec;
  thresholds["door_open_max_sec"] = config.doorOpenMaxSec;
  thresholds["defrost_cooldown_sec"] = config.defrostCooldownSec;
  
  // Estado de puertas
  JsonObject doors = doc.createNestedObject("doors");
  doors["door1_open"] = sensorData.doorOpen;
  doors["door1_enabled"] = config.doorEnabled;
  doors["door_open_sec"] = sensorData.doorOpen ? (millis() - sensorData.doorOpenSince) / 1000 : 0;
  
  // Estado del sistema
  JsonObject system = doc.createNestedObject("system");
  system["alert_active"] = state.alertActive;
  system["alert_acknowledged"] = state.alertAcknowledged;
  system["alert_message"] = state.alertMessage;
  system["relay_on"] = state.relayState;
  system["defrost_mode"] = state.defrostMode;
  system["defrost_minutes"] = state.defrostMode ? (millis() - state.defrostStartTime) / 60000 : 0;
  system["cooldown_mode"] = state.cooldownMode;
  system["cooldown_remaining_sec"] = state.cooldownRemainingSec;
  system["simulation_mode"] = config.simulationMode;
  system["uptime_sec"] = (millis() - state.uptime) / 1000;
  system["total_alerts"] = state.totalAlerts;
  system["free_heap"] = ESP.getFreeHeap();
  
  // Conectividad
  JsonObject network = doc.createNestedObject("network");
  network["wifi_connected"] = state.wifiConnected;
  network["wifi_rssi"] = WiFi.RSSI();
  network["internet_available"] = state.internetAvailable;
  network["ap_mode"] = state.apMode;
  
  // Supabase
  JsonObject supabase = doc.createNestedObject("supabase");
  supabase["enabled"] = config.supabaseEnabled;
  supabase["last_sync_ago_sec"] = (millis() - state.lastSupabaseSync) / 1000;
  supabase["can_send"] = config.supabaseEnabled && state.internetAvailable;
  
  // Flags de funcionalidades
  JsonObject features = doc.createNestedObject("features");
  features["telegram_enabled"] = config.telegramEnabled;
  features["relay_enabled"] = config.relayEnabled;
  features["buzzer_enabled"] = config.buzzerEnabled;
  features["sensor1_enabled"] = config.sensor1Enabled;
  features["sensor2_enabled"] = config.sensor2Enabled;
  features["dht22_enabled"] = config.dht22Enabled;
  
  // Imprimir JSON
  Serial.println("\n===== STATUS JSON =====");
  serializeJsonPretty(doc, Serial);
  Serial.println("\n=======================\n");
}

// ============================================
// LOOP PRINCIPAL
// ============================================
void loop() {
  // Manejar peticiones web
  server.handleClient();
  
  // Leer sensores cada 2 segundos
  static unsigned long lastSensorRead = 0;
  if (millis() - lastSensorRead >= 2000) {
    lastSensorRead = millis();
    readSensors();
  }
  
  // Imprimir estado JSON cada 5 segundos
  static unsigned long lastStatusPrint = 0;
  if (millis() - lastStatusPrint >= 5000) {
    lastStatusPrint = millis();
    printStatusJSON();
  }
  
  // Verificar señal de defrost del reefer (cada 500ms)
  static unsigned long lastDefrostCheck = 0;
  if (millis() - lastDefrostCheck >= 500) {
    lastDefrostCheck = millis();
    checkDefrostSignal();
  }
  
  // Actualizar timer de cooldown (cada segundo)
  static unsigned long lastCooldownUpdate = 0;
  if (millis() - lastCooldownUpdate >= 1000) {
    lastCooldownUpdate = millis();
    if (state.cooldownMode) {
      unsigned long elapsed = (millis() - state.cooldownStartTime) / 1000;
      int remaining = config.defrostCooldownSec - elapsed;
      if (remaining <= 0) {
        // Cooldown terminado - reactivar monitoreo normal
        state.cooldownMode = false;
        state.cooldownRemainingSec = 0;
        state.cooldownStartTime = 0;
        Serial.println("[COOLDOWN] ✓ FINALIZADO - Sistema vuelve a monitoreo normal");
      } else {
        state.cooldownRemainingSec = remaining;
      }
    }
  }
  
  // Verificar alertas cada segundo
  static unsigned long lastAlertCheck = 0;
  if (millis() - lastAlertCheck >= 1000) {
    lastAlertCheck = millis();
    checkAlerts();
  }
  
  // Verificar conexión a internet
  checkInternet();
  
  // Actualizar historial
  updateHistory();
  
  // Sincronizar con Supabase
  supabaseSync();
  
  // Pequeña pausa
  delay(10);
}
