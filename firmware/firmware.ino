/*
 * SISTEMA MONITOREO RIFT v2.0 - Campamento Parametican Silver
 * ESP32 con WiFiManager, mDNS, múltiples sensores
 * 
 * CARACTERÍSTICAS:
 * - WiFiManager: Configura WiFi via portal web (192.168.4.1)
 * - mDNS: Acceso via http://rift.local
 * - Soporte para 1-3 sensores DS18B20 + DHT22 opcional
 * - Modo simulación para testing
 * - Telegram + Supabase
 */

#include <WiFi.h>
#include <WiFiManager.h>
#include <ESPmDNS.h>
#include <WebServer.h>
#include <SPIFFS.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <DHT.h>
#include <Preferences.h>
#include <time.h>

#include "config.h"

// ============================================
// OBJETOS GLOBALES
// ============================================
WebServer server(80);
WiFiManager wifiManager;
Preferences prefs;
OneWire oneWire(PIN_ONEWIRE);
DallasTemperature ds18b20(&oneWire);
DHT dht(PIN_DHT22, DHT22);

// ============================================
// ESTRUCTURAS DE DATOS
// ============================================
struct Config {
  float tempMax;
  float tempCritical;
  int alertDelaySec;
  int doorOpenMaxSec;
  bool relayEnabled;
  bool buzzerEnabled;
  bool telegramEnabled;
  bool sensor1Enabled;
  bool sensor2Enabled;
  bool dht22Enabled;
  bool doorEnabled;      // Sensor magnético de puerta
  bool simulationMode;
  float simTemp1;
  float simTemp2;
  bool simDoorOpen;      // Estado simulado de la puerta
};

struct SensorData {
  float temp1;
  float temp2;
  float tempDHT;
  float humidity;
  float tempAvg;
  bool doorOpen;
  unsigned long doorOpenSince;
  int sensorCount;
  bool valid;
};

struct SystemState {
  bool alertActive;
  bool criticalAlert;
  String alertMessage;
  unsigned long alertStartTime;
  unsigned long highTempStartTime;
  bool relayState;
  bool internetAvailable;
  bool wifiConnected;
  bool apMode;
  unsigned long lastInternetCheck;
  unsigned long lastSensorRead;
  unsigned long lastTelegramAlert;
  unsigned long uptime;
  int totalAlerts;
  String localIP;
};

struct HistoryPoint {
  unsigned long timestamp;
  float temperature;
  bool doorOpen;
  bool alert;
};

// ============================================
// VARIABLES GLOBALES
// ============================================
Config config;
SensorData sensorData;
SystemState state;
HistoryPoint history[HISTORY_MAX_POINTS];
int historyIndex = 0;
int historyCount = 0;

// Cola offline para Supabase (cuando no hay internet)
#define OFFLINE_QUEUE_SIZE 50
struct OfflineData {
  float temperature;
  bool doorOpen;
  bool alertActive;
  unsigned long timestamp;
  bool pending;
};
OfflineData offlineQueue[OFFLINE_QUEUE_SIZE];
int offlineQueueIndex = 0;

// Contador de conexiones de apps (para LED azul)
unsigned long lastAppConnection = 0;
bool appConnected = false;
int androidConnections = 0;  // Contador de conexiones Android activas
unsigned long lastAndroidPing = 0;

// Variables para alertas
bool telegramAlertSent = false;
bool manualAlert = false;  // Alertas manuales no se auto-desactivan

// ============================================
// SETUP
// ============================================
void setup() {
  Serial.begin(115200);
  Serial.println("\n\n========================================");
  Serial.println("  RIFT Monitor v2.0 - Parametican Silver");
  Serial.println("========================================\n");

  initPins();
  initSPIFFS();
  loadConfig();
  initSensors();
  connectWiFi();
  setupMDNS();
  setupWebServer();
  server.begin();
  
  configTime(-3 * 3600, 0, "pool.ntp.org");
  
  state.alertActive = false;
  state.criticalAlert = false;
  state.relayState = false;
  state.uptime = millis();
  state.totalAlerts = 0;
  
  setRelay(false);
  
  Serial.println("\n[SISTEMA] ¡Listo!");
  Serial.println("========================================");
  if (state.apMode) {
    Serial.println("  MODO AP: Conectate a '" AP_NAME "'");
    Serial.println("  Password: " AP_PASSWORD);
    Serial.println("  Entrá a: http://192.168.4.1");
  } else {
    Serial.print("  IP Local: ");
    Serial.println(state.localIP);
    Serial.println("  Acceso: http://" MDNS_NAME ".local");
  }
  Serial.println("========================================\n");
}

// ============================================
// LOOP PRINCIPAL
// ============================================
unsigned long wifiResetButtonPressed = 0;

void loop() {
  server.handleClient();
  
  // Verificar botón de reset WiFi (mantener 5 segundos)
  checkWiFiResetButton();
  
  if (WiFi.status() != WL_CONNECTED && !state.apMode) {
    reconnectWiFi();
  }
  
  if (millis() - state.lastSensorRead >= SENSOR_READ_INTERVAL_MS) {
    readSensors();
    checkAlerts();
    updateHistory();
    state.lastSensorRead = millis();
  }
  
  if (millis() - state.lastInternetCheck >= INTERNET_CHECK_INTERVAL_MS) {
    checkInternet();
    state.lastInternetCheck = millis();
  }
  
  syncToSupabase();
  checkCloudCommands();  // Verificar comandos desde la nube
  updateOutputs();
  
  delay(10);
}

// ============================================
// INICIALIZACIÓN
// ============================================
void initPins() {
  pinMode(PIN_DOOR_SENSOR, INPUT_PULLUP);
  pinMode(PIN_WIFI_RESET, INPUT_PULLUP);  // Botón de reset WiFi
  pinMode(PIN_RELAY, OUTPUT);
  pinMode(PIN_LED_OK, OUTPUT);
  pinMode(PIN_LED_ALERT, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  
  digitalWrite(PIN_RELAY, RELAY_OFF);
  digitalWrite(PIN_LED_OK, HIGH);
  digitalWrite(PIN_LED_ALERT, LOW);
  digitalWrite(PIN_BUZZER, LOW);
  
  Serial.println("[OK] Pines configurados");
}

// Verificar botón de reset WiFi (mantener 5 segundos para borrar WiFi)
void checkWiFiResetButton() {
  static unsigned long buttonPressStart = 0;
  static bool wasPressed = false;
  
  bool isPressed = (digitalRead(PIN_WIFI_RESET) == LOW);
  
  if (isPressed && !wasPressed) {
    // Botón recién presionado
    buttonPressStart = millis();
    wasPressed = true;
    Serial.println("[WIFI] Botón presionado - mantener 5 segundos para reset");
  } else if (isPressed && wasPressed) {
    // Botón mantenido presionado
    unsigned long elapsed = millis() - buttonPressStart;
    
    // Feedback visual: parpadeo rápido del LED
    if (elapsed > 1000) {
      digitalWrite(PIN_LED_ALERT, (millis() / 200) % 2);
    }
    
    // Si pasaron 5 segundos, borrar WiFi
    if (elapsed >= 5000) {
      Serial.println("[WIFI] ¡Reset WiFi activado!");
      
      // Feedback: LED fijo + buzzer
      digitalWrite(PIN_LED_ALERT, HIGH);
      digitalWrite(PIN_BUZZER, HIGH);
      delay(500);
      digitalWrite(PIN_BUZZER, LOW);
      
      // Borrar credenciales WiFi
      WiFi.disconnect(true, true);  // Borrar credenciales guardadas
      
      Serial.println("[WIFI] Credenciales borradas. Reiniciando...");
      delay(1000);
      ESP.restart();
    }
  } else if (!isPressed && wasPressed) {
    // Botón soltado antes de 5 segundos
    wasPressed = false;
    digitalWrite(PIN_LED_ALERT, state.alertActive ? HIGH : LOW);
    Serial.println("[WIFI] Botón soltado - reset cancelado");
  }
}

void initSPIFFS() {
  if (!SPIFFS.begin(true)) {
    Serial.println("[ERROR] SPIFFS falló");
  } else {
    Serial.println("[OK] SPIFFS montado");
  }
}

void initSensors() {
  ds18b20.begin();
  int count = ds18b20.getDeviceCount();
  Serial.printf("[OK] DS18B20: %d sensor(es) encontrado(s)\n", count);
  
  if (config.dht22Enabled) {
    dht.begin();
    Serial.println("[OK] DHT22 inicializado");
  }
  
  sensorData.sensorCount = count;
}

void loadConfig() {
  prefs.begin("rift", false);
  
  config.tempMax = prefs.getFloat("tempMax", DEFAULT_TEMP_MAX);
  config.tempCritical = prefs.getFloat("tempCrit", DEFAULT_TEMP_CRITICAL);
  config.alertDelaySec = prefs.getInt("alertDelay", DEFAULT_ALERT_DELAY_SEC);
  config.doorOpenMaxSec = prefs.getInt("doorMax", DEFAULT_DOOR_OPEN_MAX_SEC);
  config.relayEnabled = prefs.getBool("relayEn", true);
  config.buzzerEnabled = prefs.getBool("buzzerEn", true);
  config.telegramEnabled = prefs.getBool("telegramEn", true);
  config.sensor1Enabled = prefs.getBool("sensor1", SENSOR_DS18B20_1_ENABLED);
  config.sensor2Enabled = prefs.getBool("sensor2", SENSOR_DS18B20_2_ENABLED);
  config.dht22Enabled = prefs.getBool("dht22", SENSOR_DHT22_ENABLED);
  config.doorEnabled = prefs.getBool("doorEn", SENSOR_DOOR_ENABLED);
  config.simulationMode = prefs.getBool("simMode", SIMULATION_MODE);
  config.simTemp1 = prefs.getFloat("simTemp1", -22.0);
  config.simTemp2 = prefs.getFloat("simTemp2", -22.0);
  config.simDoorOpen = prefs.getBool("simDoor", false);
  
  Serial.println("[OK] Configuración cargada");
}

void saveConfig() {
  prefs.putFloat("tempMax", config.tempMax);
  prefs.putFloat("tempCrit", config.tempCritical);
  prefs.putInt("alertDelay", config.alertDelaySec);
  prefs.putInt("doorMax", config.doorOpenMaxSec);
  prefs.putBool("relayEn", config.relayEnabled);
  prefs.putBool("buzzerEn", config.buzzerEnabled);
  prefs.putBool("telegramEn", config.telegramEnabled);
  prefs.putBool("sensor1", config.sensor1Enabled);
  prefs.putBool("sensor2", config.sensor2Enabled);
  prefs.putBool("dht22", config.dht22Enabled);
  prefs.putBool("doorEn", config.doorEnabled);
  prefs.putBool("simMode", config.simulationMode);
  prefs.putFloat("simTemp1", config.simTemp1);
  prefs.putFloat("simTemp2", config.simTemp2);
  prefs.putBool("simDoor", config.simDoorOpen);
  
  Serial.println("[OK] Configuración guardada");
}

// ============================================
// WIFI CON WIFIMANAGER
// ============================================
void connectWiFi() {
  Serial.println("[WIFI] Iniciando WiFiManager...");
  
  wifiManager.setConfigPortalTimeout(AP_TIMEOUT);
  wifiManager.setAPCallback(configModeCallback);
  wifiManager.setSaveConfigCallback(saveConfigCallback);
  
  // Intentar conectar, si falla abre portal de configuración
  if (!wifiManager.autoConnect(AP_NAME, AP_PASSWORD)) {
    Serial.println("[WIFI] Falló conexión, entrando en modo AP");
    state.apMode = true;
    state.wifiConnected = false;
  } else {
    Serial.println("[WIFI] ¡Conectado!");
    state.apMode = false;
    state.wifiConnected = true;
    state.localIP = WiFi.localIP().toString();
    Serial.print("[WIFI] IP: ");
    Serial.println(state.localIP);
  }
}

void configModeCallback(WiFiManager *myWiFiManager) {
  Serial.println("[WIFI] Modo AP activado");
  Serial.print("[WIFI] AP IP: ");
  Serial.println(WiFi.softAPIP());
  state.apMode = true;
}

void saveConfigCallback() {
  Serial.println("[WIFI] Configuración WiFi guardada");
}

void reconnectWiFi() {
  static unsigned long lastAttempt = 0;
  if (millis() - lastAttempt < 30000) return;
  
  Serial.println("[WIFI] Reconectando...");
  WiFi.reconnect();
  lastAttempt = millis();
  
  delay(5000);
  if (WiFi.status() == WL_CONNECTED) {
    state.wifiConnected = true;
    state.localIP = WiFi.localIP().toString();
    Serial.println("[WIFI] Reconectado: " + state.localIP);
  }
}

// ============================================
// mDNS
// ============================================
void setupMDNS() {
  if (MDNS.begin(MDNS_NAME)) {
    MDNS.addService("http", "tcp", 80);
    Serial.printf("[OK] mDNS: http://%s.local\n", MDNS_NAME);
  } else {
    Serial.println("[ERROR] mDNS falló");
  }
}

// ============================================
// LECTURA DE SENSORES
// ============================================
void readSensors() {
  if (config.simulationMode) {
    // Modo simulación
    sensorData.temp1 = config.simTemp1 + (random(-10, 10) / 10.0);
    sensorData.temp2 = config.simTemp2 + (random(-10, 10) / 10.0);
    sensorData.valid = true;
  } else {
    // Lectura real
    ds18b20.requestTemperatures();
    
    float t1 = -999, t2 = -999;
    int validCount = 0;
    float sum = 0;
    
    if (config.sensor1Enabled && sensorData.sensorCount >= 1) {
      t1 = ds18b20.getTempCByIndex(0);
      if (t1 > -100 && t1 < 100) {
        sensorData.temp1 = t1;
        sum += t1;
        validCount++;
      }
    }
    
    if (config.sensor2Enabled && sensorData.sensorCount >= 2) {
      t2 = ds18b20.getTempCByIndex(1);
      if (t2 > -100 && t2 < 100) {
        sensorData.temp2 = t2;
        sum += t2;
        validCount++;
      }
    }
    
    if (config.dht22Enabled) {
      float h = dht.readHumidity();
      float t = dht.readTemperature();
      if (!isnan(h) && !isnan(t)) {
        sensorData.tempDHT = t;
        sensorData.humidity = h;
      }
    }
    
    sensorData.valid = validCount > 0;
    if (validCount > 0) {
      sensorData.tempAvg = sum / validCount;
    }
  }
  
  // Calcular promedio
  if (config.sensor1Enabled && config.sensor2Enabled) {
    sensorData.tempAvg = (sensorData.temp1 + sensorData.temp2) / 2.0;
  } else if (config.sensor1Enabled) {
    sensorData.tempAvg = sensorData.temp1;
  } else if (config.sensor2Enabled) {
    sensorData.tempAvg = sensorData.temp2;
  }
  
  // Leer puerta (solo si está habilitado el sensor)
  bool doorNow = false;
  if (config.simulationMode) {
    // En modo simulación, usar el estado simulado
    doorNow = config.simDoorOpen;
  } else if (config.doorEnabled) {
    // Solo leer el sensor si está habilitado
    doorNow = digitalRead(PIN_DOOR_SENSOR) == HIGH;
  }
  // Si el sensor de puerta está deshabilitado, siempre reportar cerrada
  
  if (doorNow && !sensorData.doorOpen) {
    sensorData.doorOpenSince = millis();
  }
  sensorData.doorOpen = doorNow;
}

void checkInternet() {
  if (!state.wifiConnected) {
    state.internetAvailable = false;
    return;
  }
  
  HTTPClient http;
  http.begin("http://www.google.com/generate_204");
  http.setTimeout(5000);
  int code = http.GET();
  state.internetAvailable = (code == 204 || code == 200);
  http.end();
}

// ============================================
// ALERTAS
// ============================================
void checkAlerts() {
  if (!sensorData.valid) return;
  
  float temp = sensorData.tempAvg;
  bool doorOpen = sensorData.doorOpen;
  
  // Ignorar temperatura si puerta está abierta (falso positivo)
  if (doorOpen) {
    unsigned long doorOpenSec = (millis() - sensorData.doorOpenSince) / 1000;
    if (doorOpenSec > config.doorOpenMaxSec) {
      triggerAlert("🚪 PUERTA ABIERTA por más de " + String(doorOpenSec / 60) + " minutos", false);
    }
    return;
  }
  
  // Verificar temperatura
  if (temp > config.tempCritical) {
    if (state.highTempStartTime == 0) {
      state.highTempStartTime = millis();
    }
    unsigned long highTempSec = (millis() - state.highTempStartTime) / 1000;
    
    if (highTempSec >= config.alertDelaySec) {
      triggerAlert("🚨 CRÍTICO: " + String(temp, 1) + "°C (máx: " + String(config.tempCritical, 1) + "°C)", true);
    }
  } else if (temp > config.tempMax) {
    if (state.highTempStartTime == 0) {
      state.highTempStartTime = millis();
    }
    unsigned long highTempSec = (millis() - state.highTempStartTime) / 1000;
    
    if (highTempSec >= config.alertDelaySec) {
      triggerAlert("⚠️ Advertencia: " + String(temp, 1) + "°C (máx: " + String(config.tempMax, 1) + "°C)", false);
    }
  } else {
    // Temperatura normal
    state.highTempStartTime = 0;
    // Solo auto-desactivar si NO es una alerta manual
    if (state.alertActive && !manualAlert) {
      clearAlert();
    }
  }
}

void triggerAlert(String message, bool critical) {
  // Si ya hay una alerta activa, no hacer nada (evita spam)
  if (state.alertActive) return;
  
  state.alertActive = true;
  state.criticalAlert = critical;
  state.alertMessage = message;
  state.alertStartTime = millis();
  state.totalAlerts++;
  
  Serial.println("[ALERTA] " + message);
  
  if (config.relayEnabled) {
    setRelay(true);
  }
  
  // Telegram - SOLO UNA VEZ por alerta
  if (state.internetAvailable && config.telegramEnabled && !telegramAlertSent) {
    sendTelegramAlert(message);
    telegramAlertSent = true;  // Marcar que ya se envió
  }
  
  // Supabase
  sendAlertToSupabase(critical ? "critical" : "warning", message);
}

void clearAlert() {
  state.alertActive = false;
  state.criticalAlert = false;
  state.alertMessage = "";
  setRelay(false);
  telegramAlertSent = false;  // Resetear para permitir nueva alerta
  
  Serial.println("[ALERTA] Resuelta automáticamente");
  
  // NO enviar mensaje de "resuelta" para evitar spam
  // Solo se envía cuando el usuario presiona DETENER
}

void acknowledgeAlert() {
  // DETENER TODO - Poner estado en false para que todas las apps lo vean
  state.alertActive = false;
  state.criticalAlert = false;
  state.alertMessage = "";
  state.highTempStartTime = 0;
  telegramAlertSent = false;  // Resetear para permitir nueva alerta en el futuro
  manualAlert = false;  // Resetear flag de alerta manual
  
  // Apagar relay y buzzer
  setRelay(false);
  digitalWrite(PIN_BUZZER, LOW);
  digitalWrite(PIN_LED_ALERT, LOW);
  
  Serial.println("[ALERTA] *** DETENIDA por usuario ***");
}

// ============================================
// RELAY Y OUTPUTS
// ============================================
void setRelay(bool on) {
  state.relayState = on;
  digitalWrite(PIN_RELAY, on ? RELAY_ON : RELAY_OFF);
}

void updateOutputs() {
  // Verificar si hay app conectada (timeout 30 segundos)
  if (appConnected && millis() - lastAppConnection > 30000) {
    appConnected = false;
  }
  
  if (state.alertActive) {
    digitalWrite(PIN_LED_OK, LOW);
    digitalWrite(PIN_LED_ALERT, HIGH);
    
    if (config.buzzerEnabled && state.criticalAlert) {
      static unsigned long lastBuzz = 0;
      if (millis() - lastBuzz > 1000) {
        digitalWrite(PIN_BUZZER, !digitalRead(PIN_BUZZER));
        lastBuzz = millis();
      }
    }
  } else {
    // LED azul: ON si hay app conectada, parpadeo lento si no
    if (appConnected) {
      digitalWrite(PIN_LED_OK, HIGH);
    } else {
      // Parpadeo lento cuando no hay app (cada 2 segundos)
      static unsigned long lastBlink = 0;
      if (millis() - lastBlink > 2000) {
        digitalWrite(PIN_LED_OK, !digitalRead(PIN_LED_OK));
        lastBlink = millis();
      }
    }
    digitalWrite(PIN_LED_ALERT, LOW);
    digitalWrite(PIN_BUZZER, LOW);
  }
}

// ============================================
// HISTORIAL
// ============================================
void updateHistory() {
  static unsigned long lastHistoryUpdate = 0;
  
  if (millis() - lastHistoryUpdate >= 300000) {
    history[historyIndex].timestamp = millis();
    history[historyIndex].temperature = sensorData.tempAvg;
    history[historyIndex].doorOpen = sensorData.doorOpen;
    history[historyIndex].alert = state.alertActive;
    
    historyIndex = (historyIndex + 1) % HISTORY_MAX_POINTS;
    if (historyCount < HISTORY_MAX_POINTS) historyCount++;
    
    lastHistoryUpdate = millis();
  }
}

// ============================================
// TELEGRAM
// ============================================
void sendTelegramAlert(String message) {
  String fullMsg = "🏔️ *" + String(DEVICE_NAME) + "*\n\n" + message;
  fullMsg += "\n\n📍 " + String(LOCATION_DETAIL);
  fullMsg += "\n🌡️ Temp: " + String(sensorData.tempAvg, 1) + "°C";
  sendTelegramMessage(fullMsg);
}

void sendTelegramMessage(String message) {
  if (strlen(TELEGRAM_BOT_TOKEN) < 10) return;
  // Cooldown de 30 MINUTOS entre mensajes de Telegram para no spamear
  if (millis() - state.lastTelegramAlert < 1800000) return;  // 30 min = 1800000 ms
  
  HTTPClient http;
  String url = "https://api.telegram.org/bot" + String(TELEGRAM_BOT_TOKEN) + "/sendMessage";
  
  for (int i = 0; i < TELEGRAM_CHAT_COUNT; i++) {
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    
    StaticJsonDocument<512> doc;
    doc["chat_id"] = TELEGRAM_CHAT_IDS[i];
    doc["text"] = message;
    doc["parse_mode"] = "Markdown";
    
    String body;
    serializeJson(doc, body);
    
    int code = http.POST(body);
    Serial.printf("[TELEGRAM] Enviado a %s: %d\n", TELEGRAM_CHAT_IDS[i], code);
    http.end();
  }
  
  state.lastTelegramAlert = millis();
}

// ============================================
// SUPABASE - SYNC EN TIEMPO REAL (cada 5 seg)
// ============================================
unsigned long lastSupabaseSync = 0;
bool deviceRegistered = false;

// Registrar dispositivo en Supabase (una sola vez)
void registerDeviceInSupabase() {
  if (!state.internetAvailable || deviceRegistered) return;
  if (strlen(SUPABASE_URL) < 10) return;
  
  HTTPClient http;
  
  // Primero intentar actualizar si ya existe
  String url = String(SUPABASE_URL) + "/rest/v1/devices?device_id=eq." + String(DEVICE_ID);
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<512> doc;
  doc["device_id"] = DEVICE_ID;
  doc["name"] = DEVICE_NAME;
  doc["location"] = LOCATION_NAME;
  doc["location_detail"] = LOCATION_DETAIL;
  doc["latitude"] = LOCATION_LAT;
  doc["longitude"] = LOCATION_LON;
  doc["is_online"] = true;
  doc["firmware_version"] = "2.0";
  
  String body;
  serializeJson(doc, body);
  
  int code = http.PATCH(body);
  
  if (code == 200 || code == 204) {
    deviceRegistered = true;
    Serial.println("[SUPABASE] Dispositivo actualizado");
  } else {
    // Si no existe, crear nuevo
    http.end();
    url = String(SUPABASE_URL) + "/rest/v1/devices";
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("apikey", SUPABASE_ANON_KEY);
    http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
    http.addHeader("Prefer", "return=minimal");
    
    code = http.POST(body);
    if (code == 201) {
      deviceRegistered = true;
      Serial.println("[SUPABASE] Dispositivo registrado");
    } else {
      Serial.printf("[SUPABASE] Error registro: %d\n", code);
    }
  }
  http.end();
}

// Enviar lectura a Supabase (cada 5 segundos)
void syncToSupabase() {
  if (millis() - lastSupabaseSync < SUPABASE_SYNC_INTERVAL_MS) return;
  if (!state.internetAvailable) return;
  if (strlen(SUPABASE_URL) < 10) return;
  
  // Registrar dispositivo si no está registrado
  if (!deviceRegistered) {
    registerDeviceInSupabase();
  }
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/readings";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<384> doc;
  doc["device_id"] = DEVICE_ID;
  doc["temp1"] = sensorData.temp1;
  doc["temp2"] = sensorData.temp2;
  doc["temp_avg"] = sensorData.tempAvg;
  doc["humidity"] = sensorData.humidity;
  doc["door_open"] = sensorData.doorOpen;
  doc["door_open_seconds"] = sensorData.doorOpen ? (millis() - sensorData.doorOpenSince) / 1000 : 0;
  doc["relay_on"] = state.relayState;
  doc["alert_active"] = state.alertActive;
  doc["simulation_mode"] = config.simulationMode;
  
  String body;
  serializeJson(doc, body);
  
  int code = http.POST(body);
  if (code == 201) {
    Serial.println("[SUPABASE] ✓ Lectura enviada");
  } else {
    Serial.printf("[SUPABASE] Error: %d\n", code);
  }
  http.end();
  
  lastSupabaseSync = millis();
}

// Enviar alerta a Supabase
void sendAlertToSupabase(String severity, String message) {
  if (!state.internetAvailable) return;
  if (strlen(SUPABASE_URL) < 10) return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/alerts";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<384> doc;
  doc["device_id"] = DEVICE_ID;
  doc["alert_type"] = "temperature";
  doc["severity"] = severity;
  doc["message"] = message;
  doc["temp_value"] = sensorData.tempAvg;
  
  String body;
  serializeJson(doc, body);
  
  int code = http.POST(body);
  if (code == 201) {
    Serial.println("[SUPABASE] Alerta registrada");
  }
  http.end();
}

// Verificar comandos pendientes desde la nube
void checkCloudCommands() {
  if (!state.internetAvailable) return;
  if (strlen(SUPABASE_URL) < 10) return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/commands?device_id=eq." + String(DEVICE_ID) + "&executed=eq.false&order=created_at.asc&limit=1";
  
  http.begin(url);
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  
  int code = http.GET();
  if (code == 200) {
    String response = http.getString();
    
    StaticJsonDocument<512> doc;
    DeserializationError error = deserializeJson(doc, response);
    
    if (!error && doc.size() > 0) {
      JsonObject cmd = doc[0];
      String command = cmd["command"].as<String>();
      int cmdId = cmd["id"];
      
      Serial.printf("[CLOUD] Comando recibido: %s\n", command.c_str());
      
      // Ejecutar comando
      if (command == "stop_alert") {
        acknowledgeAlert();
      } else if (command == "toggle_relay") {
        setRelay(!state.relayState);
      }
      
      // Marcar como ejecutado
      http.end();
      url = String(SUPABASE_URL) + "/rest/v1/commands?id=eq." + String(cmdId);
      http.begin(url);
      http.addHeader("Content-Type", "application/json");
      http.addHeader("apikey", SUPABASE_ANON_KEY);
      http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
      http.PATCH("{\"executed\":true}");
    }
  }
  http.end();
}

// ============================================
// WEB SERVER
// ============================================
void setupWebServer() {
  server.on("/", HTTP_GET, handleRoot);
  server.on("/api/status", HTTP_GET, handleApiStatus);
  server.on("/api/config", HTTP_GET, handleApiGetConfig);
  server.on("/api/config", HTTP_POST, handleApiSetConfig);
  server.on("/api/history", HTTP_GET, handleApiHistory);
  server.on("/api/alert/ack", HTTP_POST, handleApiAckAlert);
  server.on("/api/alert/test", HTTP_POST, handleApiTestAlert);
  server.on("/api/relay", HTTP_POST, handleApiRelay);
  server.on("/api/telegram/test", HTTP_POST, handleApiTelegramTest);
  server.on("/api/simulation", HTTP_POST, handleApiSimulation);
  server.on("/api/door/toggle", HTTP_POST, handleApiToggleDoor);
  server.on("/api/wifi/reset", HTTP_POST, handleApiWifiReset);
  server.on("/api/ping", HTTP_POST, handleApiPing);
  server.on("/api/ping", HTTP_GET, handleApiPing);
  server.onNotFound(handleNotFound);
  
  Serial.println("[OK] Web server configurado");
}

void handleRoot() {
  if (SPIFFS.exists("/index.html")) {
    File file = SPIFFS.open("/index.html", "r");
    server.streamFile(file, "text/html");
    file.close();
  } else {
    server.send(200, "text/html", getEmbeddedHTML());
  }
}

void handleApiStatus() {
  StaticJsonDocument<1024> doc;
  
  // Sensor data
  JsonObject sensor = doc.createNestedObject("sensor");
  sensor["temp1"] = sensorData.temp1;
  sensor["temp2"] = sensorData.temp2;
  sensor["temp_avg"] = sensorData.tempAvg;
  sensor["temp_dht"] = sensorData.tempDHT;
  sensor["humidity"] = sensorData.humidity;
  sensor["door_open"] = sensorData.doorOpen;
  sensor["door_open_sec"] = sensorData.doorOpen ? (millis() - sensorData.doorOpenSince) / 1000 : 0;
  sensor["sensor_count"] = sensorData.sensorCount;
  sensor["valid"] = sensorData.valid;
  
  // System state
  JsonObject sys = doc.createNestedObject("system");
  sys["alert_active"] = state.alertActive;
  sys["critical"] = state.criticalAlert;
  sys["alert_message"] = state.alertMessage;
  sys["relay_on"] = state.relayState;
  sys["internet"] = state.internetAvailable;
  sys["wifi_connected"] = state.wifiConnected;
  sys["ap_mode"] = state.apMode;
  sys["uptime_sec"] = (millis() - state.uptime) / 1000;
  sys["total_alerts"] = state.totalAlerts;
  sys["wifi_rssi"] = WiFi.RSSI();
  sys["simulation_mode"] = config.simulationMode;
  sys["door_enabled"] = config.doorEnabled;
  sys["sensor1_enabled"] = config.sensor1Enabled;
  sys["sensor2_enabled"] = config.sensor2Enabled;
  sys["android_connections"] = androidConnections;
  sys["last_android_ping"] = lastAndroidPing > 0 ? (millis() - lastAndroidPing) / 1000 : -1;
  
  // Device info
  JsonObject device = doc.createNestedObject("device");
  device["id"] = DEVICE_ID;
  device["name"] = DEVICE_NAME;
  device["ip"] = state.localIP;
  device["mdns"] = String(MDNS_NAME) + ".local";
  
  // Location
  JsonObject loc = doc.createNestedObject("location");
  loc["name"] = LOCATION_NAME;
  loc["detail"] = LOCATION_DETAIL;
  loc["lat"] = LOCATION_LAT;
  loc["lon"] = LOCATION_LON;
  
  // Timestamp
  struct tm timeinfo;
  if (getLocalTime(&timeinfo)) {
    char buf[32];
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &timeinfo);
    doc["timestamp"] = buf;
  }
  
  String response;
  serializeJson(doc, response);
  
  // Registrar conexión de app y encender LED azul
  lastAppConnection = millis();
  appConnected = true;
  digitalWrite(PIN_LED_OK, HIGH);  // LED azul ON cuando hay app conectada
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", response);
}

void handleApiGetConfig() {
  StaticJsonDocument<512> doc;
  
  doc["temp_max"] = config.tempMax;
  doc["temp_critical"] = config.tempCritical;
  doc["alert_delay_sec"] = config.alertDelaySec;
  doc["door_open_max_sec"] = config.doorOpenMaxSec;
  doc["relay_enabled"] = config.relayEnabled;
  doc["buzzer_enabled"] = config.buzzerEnabled;
  doc["telegram_enabled"] = config.telegramEnabled;
  doc["sensor1_enabled"] = config.sensor1Enabled;
  doc["sensor2_enabled"] = config.sensor2Enabled;
  doc["dht22_enabled"] = config.dht22Enabled;
  doc["door_enabled"] = config.doorEnabled;
  doc["simulation_mode"] = config.simulationMode;
  doc["sim_temp1"] = config.simTemp1;
  doc["sim_temp2"] = config.simTemp2;
  doc["sim_door_open"] = config.simDoorOpen;
  
  String response;
  serializeJson(doc, response);
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", response);
}

void handleApiSetConfig() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No body\"}");
    return;
  }
  
  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, server.arg("plain"));
  
  if (error) {
    server.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
    return;
  }
  
  if (doc.containsKey("temp_max")) config.tempMax = doc["temp_max"];
  if (doc.containsKey("temp_critical")) config.tempCritical = doc["temp_critical"];
  if (doc.containsKey("alert_delay_sec")) config.alertDelaySec = doc["alert_delay_sec"];
  if (doc.containsKey("door_open_max_sec")) config.doorOpenMaxSec = doc["door_open_max_sec"];
  if (doc.containsKey("relay_enabled")) config.relayEnabled = doc["relay_enabled"];
  if (doc.containsKey("buzzer_enabled")) config.buzzerEnabled = doc["buzzer_enabled"];
  if (doc.containsKey("telegram_enabled")) config.telegramEnabled = doc["telegram_enabled"];
  if (doc.containsKey("sensor1_enabled")) config.sensor1Enabled = doc["sensor1_enabled"];
  if (doc.containsKey("sensor2_enabled")) config.sensor2Enabled = doc["sensor2_enabled"];
  if (doc.containsKey("dht22_enabled")) config.dht22Enabled = doc["dht22_enabled"];
  if (doc.containsKey("door_enabled")) config.doorEnabled = doc["door_enabled"];
  if (doc.containsKey("simulation_mode")) config.simulationMode = doc["simulation_mode"];
  if (doc.containsKey("sim_temp1")) config.simTemp1 = doc["sim_temp1"];
  if (doc.containsKey("sim_temp2")) config.simTemp2 = doc["sim_temp2"];
  if (doc.containsKey("sim_door_open")) config.simDoorOpen = doc["sim_door_open"];
  
  saveConfig();
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

void handleApiHistory() {
  StaticJsonDocument<4096> doc;
  JsonArray arr = doc.createNestedArray("history");
  
  for (int i = 0; i < historyCount; i++) {
    int idx = (historyIndex - historyCount + i + HISTORY_MAX_POINTS) % HISTORY_MAX_POINTS;
    JsonObject point = arr.createNestedObject();
    point["t"] = history[idx].timestamp;
    point["temp"] = history[idx].temperature;
    point["door"] = history[idx].doorOpen;
    point["alert"] = history[idx].alert;
  }
  
  String response;
  serializeJson(doc, response);
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", response);
}

void handleApiAckAlert() {
  acknowledgeAlert();
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

void handleApiTestAlert() {
  manualAlert = true;  // Marcar como alerta manual para que no se auto-desactive
  triggerAlert("🔔 ALERTA DE PRUEBA - Sistema funcionando correctamente", true);
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

void handleApiRelay() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No body\"}");
    return;
  }
  
  StaticJsonDocument<64> doc;
  deserializeJson(doc, server.arg("plain"));
  
  bool on = doc["state"] | false;
  setRelay(on);
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true,\"relay\":" + String(on ? "true" : "false") + "}");
}

void handleApiTelegramTest() {
  if (state.internetAvailable) {
    sendTelegramMessage("🧪 *Test de Telegram*\n\n✅ El sistema está funcionando correctamente.\n\n📍 " + String(LOCATION_DETAIL) + "\n🌡️ Temp actual: " + String(sensorData.tempAvg, 1) + "°C");
    server.send(200, "application/json", "{\"success\":true}");
  } else {
    server.send(503, "application/json", "{\"error\":\"No internet\"}");
  }
}

void handleApiSimulation() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No body\"}");
    return;
  }
  
  StaticJsonDocument<128> doc;
  deserializeJson(doc, server.arg("plain"));
  
  if (doc.containsKey("enabled")) config.simulationMode = doc["enabled"];
  if (doc.containsKey("temp1")) config.simTemp1 = doc["temp1"];
  if (doc.containsKey("temp2")) config.simTemp2 = doc["temp2"];
  if (doc.containsKey("door")) config.simDoorOpen = doc["door"];
  
  saveConfig();
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

void handleApiToggleDoor() {
  config.simDoorOpen = !config.simDoorOpen;
  saveConfig();
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true,\"door_open\":" + String(config.simDoorOpen ? "true" : "false") + "}");
}

void handleApiWifiReset() {
  server.send(200, "application/json", "{\"success\":true,\"message\":\"Reiniciando en modo AP...\"}");
  delay(1000);
  wifiManager.resetSettings();
  ESP.restart();
}

void handleApiPing() {
  // Registrar conexión de app Android
  androidConnections++;
  lastAndroidPing = millis();
  appConnected = true;
  lastAppConnection = millis();
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true,\"android_connections\":" + String(androidConnections) + "}");
}

void handleNotFound() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
  
  if (server.method() == HTTP_OPTIONS) {
    server.send(204);
  } else {
    server.send(404, "text/plain", "Not Found");
  }
}

// ============================================
// HTML EMBEBIDO PRO - Panel de Control Completo
// ============================================
String getEmbeddedHTML() {
  return R"rawliteral(
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Reefer Monitor Pro</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,#0f172a 0%,#1e293b 100%);min-height:100vh;color:#fff;padding:16px}
    .container{max-width:900px;margin:0 auto}
    h1{text-align:center;margin-bottom:8px;font-size:2em}
    h1 span{background:linear-gradient(90deg,#60a5fa,#a78bfa);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
    .subtitle{text-align:center;color:#64748b;margin-bottom:24px}
    .card{background:rgba(30,41,59,0.8);border-radius:16px;padding:20px;margin-bottom:16px;border:1px solid rgba(255,255,255,0.1)}
    .card h2{margin-bottom:16px;color:#f1f5f9;font-size:1.1em;display:flex;align-items:center;gap:8px;flex-wrap:wrap}
    .status-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
    @media(max-width:600px){.status-grid{grid-template-columns:repeat(2,1fr)}}
    .status-item{background:rgba(0,0,0,0.3);padding:16px;border-radius:12px;text-align:center}
    .status-item .value{font-size:1.8em;font-weight:bold;margin:8px 0}
    .status-item .label{color:#94a3b8;font-size:0.75em;text-transform:uppercase}
    .temp-ok{color:#22d3ee;text-shadow:0 0 15px rgba(34,211,238,0.4)}
    .temp-warn{color:#fbbf24;text-shadow:0 0 15px rgba(251,191,36,0.4);animation:pulse 1s infinite}
    .temp-crit{color:#ef4444;text-shadow:0 0 15px rgba(239,68,68,0.4);animation:pulse .5s infinite}
    @keyframes pulse{0%,100%{opacity:1}50%{opacity:.7}}
    .controls{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-top:12px}
    @media(max-width:500px){.controls{grid-template-columns:1fr}}
    button{padding:14px;border:none;border-radius:12px;font-size:.95em;font-weight:600;cursor:pointer;transition:all .2s}
    button:active{transform:scale(.98)}
    button:disabled{opacity:0.5;cursor:not-allowed}
    .btn-cyan{background:linear-gradient(135deg,#22d3ee,#06b6d4);color:#0f172a}
    .btn-yellow{background:linear-gradient(135deg,#fbbf24,#f59e0b);color:#0f172a}
    .btn-red{background:linear-gradient(135deg,#ef4444,#dc2626);color:#fff}
    .btn-green{background:linear-gradient(135deg,#22c55e,#16a34a);color:#fff}
    .btn-purple{background:linear-gradient(135deg,#8b5cf6,#7c3aed);color:#fff}
    .btn-gray{background:linear-gradient(135deg,#475569,#334155);color:#fff}
    .btn-blue{background:linear-gradient(135deg,#3b82f6,#2563eb);color:#fff}
    .btn-orange{background:linear-gradient(135deg,#f97316,#ea580c);color:#fff}
    .slider-container{margin:16px 0}
    .slider-value{text-align:center;font-size:2.5em;font-weight:bold;margin:8px 0}
    input[type="range"]{width:100%;height:10px;border-radius:5px;background:linear-gradient(90deg,#22d3ee 0%,#fbbf24 50%,#ef4444 100%);-webkit-appearance:none;cursor:pointer}
    input[type="range"]::-webkit-slider-thumb{-webkit-appearance:none;width:24px;height:24px;border-radius:50%;background:#fff;box-shadow:0 2px 8px rgba(0,0,0,.3)}
    .alert-banner{background:linear-gradient(90deg,#ef4444,#dc2626);padding:16px;border-radius:12px;text-align:center;margin-bottom:16px;display:none;animation:alert-pulse .5s infinite alternate}
    .alert-banner.active{display:block}
    .alert-banner h3{margin-bottom:8px}
    @keyframes alert-pulse{0%{box-shadow:0 0 20px rgba(239,68,68,.5)}100%{box-shadow:0 0 40px rgba(239,68,68,.8)}}
    .badge{display:inline-block;padding:3px 10px;border-radius:12px;font-size:.7em;font-weight:bold;margin-left:8px}
    .badge-online{background:#22c55e}
    .badge-sim{background:#8b5cf6}
    .badge-real{background:#3b82f6}
    .two-cols{display:grid;grid-template-columns:1fr 1fr;gap:16px}
    @media(max-width:600px){.two-cols{grid-template-columns:1fr}}
    .mode-btns{display:flex;gap:10px;margin-bottom:16px}
    .mode-btns button{flex:1;padding:12px}
    .mode-btn-active{box-shadow:0 0 0 3px rgba(255,255,255,0.5)}
    .toggle-row{display:flex;align-items:center;justify-content:space-between;padding:12px;background:rgba(0,0,0,0.2);border-radius:10px;margin-bottom:8px}
    .toggle-row .label{font-weight:500}
    .toggle{width:50px;height:28px;background:#475569;border-radius:14px;position:relative;cursor:pointer;transition:background .3s}
    .toggle.on{background:#22c55e}
    .toggle::after{content:'';position:absolute;width:22px;height:22px;background:#fff;border-radius:50%;top:3px;left:3px;transition:left .3s}
    .toggle.on::after{left:25px}
    .info-box{background:rgba(59,130,246,.15);padding:16px;border-radius:12px;border:1px solid rgba(59,130,246,.3)}
    .info-box code{background:rgba(0,0,0,.3);padding:6px 12px;border-radius:6px;font-weight:bold;color:#60a5fa}
    .input-row{margin-bottom:16px}
    .input-row label{display:block;margin-bottom:8px;color:#94a3b8;font-size:.9em}
    .input-group{display:flex;align-items:center;gap:8px}
    .input-group input{flex:1;padding:12px;border-radius:8px;border:1px solid rgba(255,255,255,.2);background:rgba(0,0,0,.3);color:#fff;font-size:1.1em;font-weight:bold}
    .input-group span{color:#64748b;min-width:40px}
  </style>
</head>
<body>
  <div class="container">
    <h1>❄️ Reefer <span>Monitor Pro</span></h1>
    <p class="subtitle">Campamento Parametican Silver</p>
    
    <div class="alert-banner" id="alertBanner">
      <h3>🚨 ¡ALERTA ACTIVA!</h3>
      <div id="alertMsg">Temperatura fuera de rango</div>
      <button class="btn-green" onclick="stopAlert()" style="margin-top:12px;font-size:1.1em;padding:16px 32px">🛑 DETENER ALERTA</button>
    </div>
    
    <div class="card">
      <h2>⚙️ Modo de Operación</h2>
      <div class="mode-btns">
        <button id="btnModoReal" class="btn-blue" onclick="setMode(false)">📡 MODO REAL</button>
        <button id="btnModoSim" class="btn-purple" onclick="setMode(true)">🧪 MODO SIMULACIÓN</button>
      </div>
    </div>
    
    <div class="card">
      <h2>📊 Estado en Tiempo Real <span class="badge badge-online">ONLINE</span><span class="badge" id="modeBadge">REAL</span></h2>
      <div class="status-grid">
        <div class="status-item">
          <div class="label">Temperatura</div>
          <div class="value temp-ok" id="temp">-22.5°C</div>
        </div>
        <div class="status-item">
          <div class="label">Puerta</div>
          <div class="value" id="door">🔒</div>
          <div class="label" id="doorText">Cerrada</div>
        </div>
        <div class="status-item">
          <div class="label">Sirena</div>
          <div class="value" id="relay">⚫</div>
          <div class="label" id="relayText">Apagada</div>
        </div>
        <div class="status-item">
          <div class="label">Uptime</div>
          <div class="value" id="uptime" style="font-size:1.3em">0:00</div>
        </div>
      </div>
    </div>
    
    <div class="two-cols">
      <div class="card" id="simControls">
        <h2>🌡️ Control de Temperatura</h2>
        <div class="slider-container">
          <div class="slider-value temp-ok" id="sliderValue">-22.5°C</div>
          <input type="range" id="tempSlider" min="-40" max="10" step="0.5" value="-22.5">
          <div style="display:flex;justify-content:space-between;color:#64748b;font-size:.75em;margin-top:4px">
            <span>-40°C</span><span>-18°C (límite)</span><span>+10°C</span>
          </div>
        </div>
        <div class="controls">
          <button class="btn-cyan" onclick="setTemp(-25)">❄️ -25°C</button>
          <button class="btn-yellow" onclick="setTemp(-15)">⚠️ -15°C</button>
          <button class="btn-red" onclick="setTemp(-5)">🔥 -5°C</button>
          <button class="btn-gray" onclick="setTemp(-22)">🔄 Normal</button>
        </div>
      </div>
      
      <div class="card">
        <h2>🎛️ Controles</h2>
        <div class="controls" style="grid-template-columns:1fr">
          <button class="btn-purple" id="btnDoor" onclick="toggleDoor()">🚪 Puerta: CERRADA</button>
          <button class="btn-red" id="btnAlert" onclick="toggleAlert()">🚨 Activar Alerta</button>
          <button class="btn-gray" onclick="toggleRelay()">🔔 Toggle Sirena</button>
        </div>
      </div>
    </div>
    
    <div class="card">
      <h2>🔧 Configuración de Sensores</h2>
      <div class="toggle-row">
        <span class="label">🌡️ Sensor Temperatura 1 (DS18B20)</span>
        <div class="toggle" id="togSensor1" onclick="toggleSensor('sensor1')"></div>
      </div>
      <div class="toggle-row">
        <span class="label">🌡️ Sensor Temperatura 2 (DS18B20)</span>
        <div class="toggle" id="togSensor2" onclick="toggleSensor('sensor2')"></div>
      </div>
      <div class="toggle-row">
        <span class="label">🚪 Sensor Magnético de Puerta</span>
        <div class="toggle" id="togDoor" onclick="toggleSensor('door')"></div>
      </div>
      <p style="color:#64748b;font-size:.8em;margin-top:12px">⚠️ Desactiva los sensores que NO tengas conectados para evitar falsas alarmas</p>
    </div>
    
    <div class="card">
      <h2>🎚️ Umbrales de Alerta</h2>
      <div class="input-row">
        <label>🌡️ Temperatura máxima (alertar si supera)</label>
        <div class="input-group">
          <input type="number" id="inTempMax" value="-18" step="0.5" min="-40" max="10">
          <span>°C</span>
        </div>
      </div>
      <div class="input-row">
        <label>🚪 Tiempo máximo puerta abierta</label>
        <div class="input-group">
          <input type="number" id="inDoorMax" value="180" step="10" min="10" max="600">
          <span>seg</span>
        </div>
      </div>
      <div class="input-row">
        <label>⏱️ Tiempo antes de alertar (anti falsos positivos)</label>
        <div class="input-group">
          <input type="number" id="inAlertDelay" value="300" step="30" min="30" max="900">
          <span>seg</span>
        </div>
      </div>
      <button class="btn-blue" onclick="saveThresholds()" style="width:100%;margin-top:16px">💾 Guardar Umbrales</button>
      <p style="color:#64748b;font-size:.8em;margin-top:12px">Ejemplo: Si pones -20°C, la alerta saltará cuando la temperatura suba de -20°C</p>
    </div>
    
    <div class="card">
      <h2>📱 Telegram y WiFi</h2>
      <div class="controls">
        <button class="btn-blue" onclick="testTelegram()">📲 Probar Telegram</button>
        <button class="btn-red" onclick="resetWifi()">📡 Reset WiFi</button>
      </div>
    </div>
    
    <div class="card">
      <h2>📱 Apps Android Conectadas</h2>
      <div class="status-grid" style="grid-template-columns:repeat(2,1fr)">
        <div class="status-item">
          <div class="label">Conexiones Totales</div>
          <div class="value" id="androidCount" style="color:#22c55e">0</div>
        </div>
        <div class="status-item">
          <div class="label">Último Ping</div>
          <div class="value" id="lastPing" style="font-size:1em">--</div>
        </div>
      </div>
    </div>
    
    <div class="card">
      <h2>ℹ️ Información del Dispositivo</h2>
      <div class="info-box">
        <p><strong>ID:</strong> <code id="deviceId">REEFER-01</code></p>
        <p style="margin-top:8px"><strong>IP:</strong> <code id="deviceIp">--</code></p>
        <p style="margin-top:8px"><strong>mDNS:</strong> <code id="deviceMdns">reefer.local</code></p>
        <p style="margin-top:8px"><strong>WiFi:</strong> <span id="wifiRssi">--</span> dBm</p>
        <p style="margin-top:8px"><strong>Internet:</strong> <span id="internetStatus">--</span></p>
      </div>
    </div>
    
    <div style="text-align:center;padding:16px;color:#475569;font-size:.75em">
      <div>Última actualización: <span id="lastUpdate">--</span></div>
    </div>
  </div>
  
  <script>
    let relayState=false,simMode=false,alertActive=false,doorOpen=false;
    let sensor1En=true,sensor2En=false,doorEn=false;
    
    async function fetchStatus(){
      try{
        const res=await fetch('/api/status');
        const d=await res.json();
        const t=d.sensor.temp_avg.toFixed(1);
        const tempEl=document.getElementById('temp');
        const sliderVal=document.getElementById('sliderValue');
        tempEl.textContent=t+'°C';
        sliderVal.textContent=t+'°C';
        tempEl.className='value';sliderVal.className='slider-value';
        if(parseFloat(t)>-10){tempEl.classList.add('temp-crit');sliderVal.classList.add('temp-crit');}
        else if(parseFloat(t)>-18){tempEl.classList.add('temp-warn');sliderVal.classList.add('temp-warn');}
        else{tempEl.classList.add('temp-ok');sliderVal.classList.add('temp-ok');}
        
        doorOpen=d.sensor.door_open;
        document.getElementById('door').textContent=doorOpen?'🔓':'🔒';
        document.getElementById('doorText').textContent=doorOpen?'ABIERTA':'Cerrada';
        document.getElementById('doorText').style.color=doorOpen?'#fbbf24':'#94a3b8';
        document.getElementById('btnDoor').textContent=doorOpen?'🚪 Puerta: ABIERTA':'🚪 Puerta: CERRADA';
        document.getElementById('btnDoor').className=doorOpen?'btn-orange':'btn-purple';
        
        document.getElementById('relay').textContent=d.system.relay_on?'🔴':'⚫';
        document.getElementById('relayText').textContent=d.system.relay_on?'ACTIVA':'Apagada';
        document.getElementById('relayText').style.color=d.system.relay_on?'#ef4444':'#94a3b8';
        document.getElementById('uptime').textContent=formatUptime(d.system.uptime_sec);
        document.getElementById('tempSlider').value=t;
        
        simMode=d.system.simulation_mode;
        const modeBadge=document.getElementById('modeBadge');
        modeBadge.textContent=simMode?'SIMULACIÓN':'REAL';
        modeBadge.className=simMode?'badge badge-sim':'badge badge-real';
        document.getElementById('btnModoReal').className=simMode?'btn-blue':'btn-blue mode-btn-active';
        document.getElementById('btnModoSim').className=simMode?'btn-purple mode-btn-active':'btn-purple';
        
        alertActive=d.system.alert_active;
        document.getElementById('btnAlert').textContent=alertActive?'🔕 Desactivar Alerta':'🚨 Activar Alerta';
        document.getElementById('btnAlert').className=alertActive?'btn-green':'btn-red';
        
        sensor1En=d.system.sensor1_enabled;
        sensor2En=d.system.sensor2_enabled;
        doorEn=d.system.door_enabled;
        document.getElementById('togSensor1').className=sensor1En?'toggle on':'toggle';
        document.getElementById('togSensor2').className=sensor2En?'toggle on':'toggle';
        document.getElementById('togDoor').className=doorEn?'toggle on':'toggle';
        
        document.getElementById('deviceId').textContent=d.device.id;
        document.getElementById('deviceIp').textContent=d.device.ip;
        document.getElementById('deviceMdns').textContent=d.device.mdns;
        document.getElementById('wifiRssi').textContent=d.system.wifi_rssi;
        document.getElementById('internetStatus').textContent=d.system.internet?'✓ Online':'✗ Offline';
        document.getElementById('internetStatus').style.color=d.system.internet?'#22c55e':'#ef4444';
        document.getElementById('lastUpdate').textContent=new Date().toLocaleTimeString();
        
        // Android connections
        document.getElementById('androidCount').textContent=d.system.android_connections||0;
        const lastPing=d.system.last_android_ping;
        document.getElementById('lastPing').textContent=lastPing>=0?formatUptime(lastPing)+' atrás':'Sin conexiones';
        
        const banner=document.getElementById('alertBanner');
        if(alertActive){banner.classList.add('active');document.getElementById('alertMsg').textContent=d.system.alert_message||'Alerta activa';}
        else{banner.classList.remove('active');}
        relayState=d.system.relay_on;
      }catch(e){console.error(e);}
    }
    
    function formatUptime(s){
      const h=Math.floor(s/3600),m=Math.floor((s%3600)/60);
      if(h>0)return h+'h '+m+'m';
      if(m>0)return m+'m '+(s%60)+'s';
      return s+'s';
    }
    
    async function setMode(sim){
      await fetch('/api/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({simulation_mode:sim})});
      fetchStatus();
    }
    
    document.getElementById('tempSlider').addEventListener('input',async(e)=>{
      document.getElementById('sliderValue').textContent=e.target.value+'°C';
      await fetch('/api/simulation',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:true,temp1:parseFloat(e.target.value),temp2:parseFloat(e.target.value)})});
    });
    
    async function setTemp(t){
      document.getElementById('tempSlider').value=t;
      document.getElementById('sliderValue').textContent=t+'°C';
      await fetch('/api/simulation',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:true,temp1:t,temp2:t})});
    }
    
    async function toggleDoor(){
      document.getElementById('btnDoor').textContent='⏳ Cambiando...';
      await fetch('/api/door/toggle',{method:'POST'});
      await fetchStatus();
    }
    
    async function toggleAlert(){
      document.getElementById('btnAlert').textContent='⏳ Procesando...';
      if(alertActive){await fetch('/api/alert/ack',{method:'POST'});}
      else{await fetch('/api/alert/test',{method:'POST'});}
      await fetchStatus();
    }
    
    async function stopAlert(){
      document.getElementById('alertBanner').style.opacity='0.5';
      await fetch('/api/alert/ack',{method:'POST'});
      alertActive=false;
      document.getElementById('alertBanner').classList.remove('active');
      document.getElementById('btnAlert').textContent='🚨 Activar Alerta';
      document.getElementById('btnAlert').className='btn-red';
      await fetchStatus();
    }
    
    async function toggleRelay(){await fetch('/api/relay',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({state:!relayState})});await fetchStatus();}
    
    async function toggleSensor(which){
      let body={};
      if(which==='sensor1'){body.sensor1_enabled=!sensor1En;document.getElementById('togSensor1').className=!sensor1En?'toggle on':'toggle';}
      if(which==='sensor2'){body.sensor2_enabled=!sensor2En;document.getElementById('togSensor2').className=!sensor2En?'toggle on':'toggle';}
      if(which==='door'){body.door_enabled=!doorEn;document.getElementById('togDoor').className=!doorEn?'toggle on':'toggle';}
      await fetch('/api/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
      await fetchStatus();
    }
    
    async function saveThresholds(){
      const tempMax=parseFloat(document.getElementById('inTempMax').value);
      const doorMax=parseInt(document.getElementById('inDoorMax').value);
      const alertDelay=parseInt(document.getElementById('inAlertDelay').value);
      await fetch('/api/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({temp_max:tempMax,door_open_max_sec:doorMax,alert_delay_sec:alertDelay})});
      alert('✅ Umbrales guardados!\n\nTemp máx: '+tempMax+'°C\nPuerta máx: '+doorMax+' seg\nDelay alerta: '+alertDelay+' seg');
    }
    
    async function loadConfig(){
      try{
        const r=await fetch('/api/config');
        const c=await r.json();
        document.getElementById('inTempMax').value=c.temp_max;
        document.getElementById('inDoorMax').value=c.door_open_max_sec;
        document.getElementById('inAlertDelay').value=c.alert_delay_sec;
      }catch(e){}
    }
    
    async function testTelegram(){
      const r=await fetch('/api/telegram/test',{method:'POST'});
      alert(r.ok?'✅ Mensaje enviado a Telegram':'❌ Error: Sin internet');
    }
    async function resetWifi(){
      if(confirm('¿Resetear configuración WiFi? El dispositivo se reiniciará en modo AP.')){
        await fetch('/api/wifi/reset',{method:'POST'});
      }
    }
    
    setInterval(fetchStatus,1000);
    fetchStatus();
    loadConfig();
  </script>
</body>
</html>
)rawliteral";
}
