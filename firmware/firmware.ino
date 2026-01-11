/*
 * SISTEMA MONITOREO REEFER v2.0 - Campamento Parametican Silver
 * ESP32 con WiFiManager, mDNS, múltiples sensores
 * 
 * CARACTERÍSTICAS:
 * - WiFiManager: Configura WiFi via portal web (192.168.4.1)
 * - mDNS: Acceso via http://reefer.local
 * - Soporte para 1-3 sensores DS18B20 + DHT22 opcional
 * - Modo simulación para testing
 * - Telegram + Supabase
 */

#include <WiFi.h>
#include <WiFiManager.h>
#include <WiFiUdp.h>
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
WiFiUDP udpDiscovery;
Preferences prefs;

// UDP Discovery - Puerto donde escucha el ESP32
#define UDP_DISCOVERY_PORT 5555
#define UDP_DISCOVERY_MAGIC "REEFER_DISCOVER"
#define UDP_DISCOVERY_RESPONSE "REEFER_HERE"
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
  int defrostCooldownSec;  // Tiempo de espera post-descongelación
  bool defrostRelayNC;     // true = Normal Cerrado, false = Normal Abierto
  bool relayEnabled;
  bool buzzerEnabled;
  bool telegramEnabled;
  bool supabaseEnabled;    // Habilitar envío a Supabase
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
  bool alertAcknowledged;        // Usuario presionó DETENER - no volver a sonar hasta que vuelva a normal
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
  unsigned long lastSupabaseSync;   // Último envío a Supabase
  unsigned long uptime;
  int totalAlerts;
  String localIP;
  bool defrostMode;              // Modo descongelamiento - deshabilita TODAS las alertas
  unsigned long defrostStartTime; // Cuando se activó el modo descongelamiento
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

// ============================================
// SETUP
// ============================================
void setup() {
  Serial.begin(115200);
  delay(100);  // Esperar que Serial esté listo
  Serial.flush();
  
  Serial.println("\n\n========================================");
  Serial.println("  Reefer Monitor v2.0 - Parametican Silver");
  Serial.println("========================================\n");

  initPins();
  initSPIFFS();
  loadConfig();      // Cargar config PRIMERO
  
  // WiFi PRIMERO (WiFiManager puede bloquear Serial)
  connectWiFi();
  
  // Pulso de relé al conectar WiFi (si está habilitado)
  #ifdef RELAY_PULSE_ON_CONNECT
  if (state.wifiConnected) {
    Serial.println("[RELAY] Pulso de conexión...");
    digitalWrite(PIN_RELAY, RELAY_ON);
    delay(RELAY_PULSE_DURATION_MS);
    digitalWrite(PIN_RELAY, RELAY_OFF);
    Serial.println("[RELAY] Pulso completado");
  }
  #endif
  
  // Sensores DESPUÉS de WiFi para que Serial funcione bien
  Serial.flush();
  Serial.println("\n[SENSORES] Inicializando...");
  initSensors();
  
  setupMDNS();
  initUdpDiscovery();
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
  handleUdpDiscovery();  // Escuchar requests de discovery de la app
  
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
  
  supabaseSync();
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
  Serial.println("[SENSOR] Iniciando DS18B20...");
  Serial.flush();
  
  // Inicializar DS18B20
  ds18b20.begin();
  delay(500);  // Dar tiempo al sensor
  
  int count = ds18b20.getDeviceCount();
  Serial.printf("[SENSOR] Sensores detectados: %d\n", count);
  Serial.flush();
  
  // Si no detectó sensores, intentar de nuevo
  if (count == 0) {
    Serial.println("[SENSOR] Reintentando...");
    delay(1000);
    ds18b20.begin();
    delay(500);
    count = ds18b20.getDeviceCount();
    Serial.printf("[SENSOR] Reintento - detectados: %d\n", count);
  }
  
  sensorData.sensorCount = count;
  
  // Configurar resolución
  if (count > 0) {
    ds18b20.setResolution(12);
    ds18b20.setWaitForConversion(true);  // BLOQUEAR para lectura inicial
    
    // Hacer lectura inicial
    Serial.println("[SENSOR] Leyendo temperatura inicial...");
    Serial.flush();
    ds18b20.requestTemperatures();
    
    float t = ds18b20.getTempCByIndex(0);
    Serial.printf("[SENSOR] >>> TEMPERATURA: %.2f C <<<\n", t);
    Serial.flush();
    
    // Guardar lectura inicial
    if (t > -55.0 && t < 125.0 && t != -127.0 && t != 85.0) {
      sensorData.temp1 = t;
      sensorData.tempAvg = t;
      sensorData.valid = true;
      Serial.println("[SENSOR] Lectura OK!");
    } else {
      Serial.printf("[SENSOR] ERROR - valor invalido: %.2f\n", t);
      sensorData.valid = false;
    }
    
    // Volver a modo no bloqueante para el loop
    ds18b20.setWaitForConversion(false);
  } else {
    Serial.println("[SENSOR] ERROR: No se detectaron sensores DS18B20!");
    Serial.println("[SENSOR] Verificar: PIN, resistencia 4.7k, conexiones");
  }
  
  if (config.dht22Enabled) {
    dht.begin();
    Serial.println("[OK] DHT22 inicializado");
  }
  
  Serial.println("[SENSOR] Inicializacion completa");
  Serial.flush();
}

void loadConfig() {
  prefs.begin("reefer", false);
  
  config.tempMax = prefs.getFloat("tempMax", DEFAULT_TEMP_MAX);
  config.tempCritical = prefs.getFloat("tempCrit", DEFAULT_TEMP_CRITICAL);
  config.alertDelaySec = prefs.getInt("alertDelay", DEFAULT_ALERT_DELAY_SEC);
  config.doorOpenMaxSec = prefs.getInt("doorMax", DEFAULT_DOOR_OPEN_MAX_SEC);
  config.defrostCooldownSec = prefs.getInt("defrostCD", 1800);  // 30 minutos default
  config.defrostRelayNC = prefs.getBool("defrostNC", false);    // Normal Abierto por default
  config.relayEnabled = prefs.getBool("relayEn", true);
  config.buzzerEnabled = prefs.getBool("buzzerEn", true);
  config.telegramEnabled = prefs.getBool("telegramEn", true);
  config.supabaseEnabled = prefs.getBool("supabaseEn", false);  // Deshabilitado por defecto
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
  prefs.putInt("defrostCD", config.defrostCooldownSec);
  prefs.putBool("defrostNC", config.defrostRelayNC);
  prefs.putBool("relayEn", config.relayEnabled);
  prefs.putBool("buzzerEn", config.buzzerEnabled);
  prefs.putBool("telegramEn", config.telegramEnabled);
  prefs.putBool("supabaseEn", config.supabaseEnabled);
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

// ============================================
// UDP DISCOVERY - Para que la app encuentre el ESP32
// ============================================
void initUdpDiscovery() {
  udpDiscovery.begin(UDP_DISCOVERY_PORT);
  Serial.printf("[OK] UDP Discovery escuchando en puerto %d\n", UDP_DISCOVERY_PORT);
}

void handleUdpDiscovery() {
  int packetSize = udpDiscovery.parsePacket();
  if (packetSize > 0) {
    char buffer[64];
    int len = udpDiscovery.read(buffer, sizeof(buffer) - 1);
    if (len > 0) {
      buffer[len] = '\0';
      
      // Verificar si es un mensaje de discovery
      if (strcmp(buffer, UDP_DISCOVERY_MAGIC) == 0) {
        Serial.printf("[UDP] Discovery request de %s:%d\n", 
                      udpDiscovery.remoteIP().toString().c_str(), 
                      udpDiscovery.remotePort());
        
        // Responder con nuestra IP y datos
        String response = String(UDP_DISCOVERY_RESPONSE) + "|" + 
                          state.localIP + "|" + 
                          DEVICE_ID + "|" + 
                          DEVICE_NAME;
        
        udpDiscovery.beginPacket(udpDiscovery.remoteIP(), udpDiscovery.remotePort());
        udpDiscovery.print(response);
        udpDiscovery.endPacket();
        
        Serial.printf("[UDP] Respondido: %s\n", response.c_str());
      }
    }
  }
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
  static unsigned long lastLog = 0;
  
  if (config.simulationMode) {
    // Modo simulación
    sensorData.temp1 = config.simTemp1 + (random(-10, 10) / 10.0);
    sensorData.temp2 = config.simTemp2 + (random(-10, 10) / 10.0);
    sensorData.tempAvg = sensorData.temp1;
    sensorData.valid = true;
  } else {
    // Lectura real de DS18B20
    ds18b20.requestTemperatures();
    delay(750);  // Esperar conversión completa
    
    // SIEMPRE leer el sensor 1 si hay sensores detectados
    if (sensorData.sensorCount > 0) {
      float t1 = ds18b20.getTempCByIndex(0);
      
      // Log cada 10 segundos para no spamear
      if (millis() - lastLog > 10000) {
        Serial.printf("[LOOP] Temp: %.2f C\n", t1);
        lastLog = millis();
      }
      
      // Validar lectura
      if (t1 > -55.0 && t1 < 125.0 && t1 != -127.0 && t1 != 85.0) {
        sensorData.temp1 = t1;
        sensorData.tempAvg = t1;
        sensorData.valid = true;
      } else {
        Serial.printf("[LOOP] Lectura invalida: %.2f\n", t1);
        sensorData.valid = false;
      }
    } else {
      sensorData.valid = false;
    }
  }
  
  // Leer puerta (solo si está habilitado el sensor)
  bool doorNow = false;
  if (config.simulationMode) {
    doorNow = config.simDoorOpen;
  } else if (config.doorEnabled) {
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
  
  // *** MODO DESCONGELAMIENTO - Ignorar TODAS las alertas ***
  if (state.defrostMode) {
    // Solo mostrar en log cada 30 segundos
    static unsigned long lastDefrostLog = 0;
    if (millis() - lastDefrostLog > 30000) {
      unsigned long defrostMin = (millis() - state.defrostStartTime) / 60000;
      Serial.printf("[DESCONGELAMIENTO] Modo activo hace %lu minutos - Alertas deshabilitadas\n", defrostMin);
      lastDefrostLog = millis();
    }
    return;  // No verificar alertas
  }
  
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
  
  // Verificar temperatura - SOLO usar tempCritical
  if (temp > config.tempCritical) {
    // Temperatura sobre el límite crítico
    if (state.highTempStartTime == 0) {
      state.highTempStartTime = millis();
      Serial.printf("[ALERTA] Temp %.1f > %.1f - Esperando %d segundos...\n", 
                    temp, config.tempCritical, config.alertDelaySec);
    }
    
    unsigned long highTempSec = (millis() - state.highTempStartTime) / 1000;
    
    // Solo activar alarma si:
    // 1. Pasó el tiempo de espera
    // 2. No hay alerta activa
    // 3. No fue silenciada (acknowledged) - solo se resetea cuando temp vuelve a normal
    if (highTempSec >= config.alertDelaySec && !state.alertActive && !state.alertAcknowledged) {
      triggerAlert("🚨 ALERTA: " + String(temp, 1) + "°C (límite: " + String(config.tempCritical, 1) + "°C)", true);
    }
    
    // Mantener relé encendido mientras temp esté alta Y haya alerta activa (no silenciada)
    if (state.alertActive && !state.alertAcknowledged && config.relayEnabled && !state.relayState) {
      setRelay(true);
    }
  } else {
    // Temperatura normal (bajo el límite) - RESETEAR TODO
    state.highTempStartTime = 0;
    state.alertAcknowledged = false;  // Ahora sí puede volver a sonar
    if (state.alertActive) {
      clearAlert();
    }
  }
}

// Variable para trackear si ya se envió alerta a Telegram en esta sesión de alerta
bool telegramAlertSent = false;

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
  sendAlertToSupabase("temperature", critical ? "critical" : "warning", message);
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
  // SILENCIAR - Marcar como acknowledged pero mantener alerta visual
  state.alertAcknowledged = true;  // No volver a sonar hasta que temp vuelva a normal
  
  // Apagar relay y buzzer (silenciar sonido)
  setRelay(false);
  digitalWrite(PIN_BUZZER, LOW);
  
  // MANTENER alertActive = true para que la app siga mostrando alerta visual
  // Solo se limpia cuando la temperatura vuelve a la normalidad
  
  Serial.println("[ALERTA] *** SILENCIADA por usuario - esperando temp normal para resetear ***");
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
  // Cooldown de 5 MINUTOS entre mensajes de Telegram
  if (millis() - state.lastTelegramAlert < 300000) return;  // 5 min = 300000 ms
  
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
// SUPABASE - Envío de lecturas cada 5 segundos
// ============================================

// Enviar lectura a Supabase (tabla readings)
bool supabaseSendReading() {
  if (!config.supabaseEnabled || !state.internetAvailable) {
    return false;
  }
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/readings";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<512> doc;
  doc["device_id"] = DEVICE_ID;
  doc["temp1"] = sensorData.temp1;
  doc["temp2"] = sensorData.temp2;
  doc["temp_avg"] = sensorData.tempAvg;
  doc["humidity"] = sensorData.humidity;
  doc["door_open"] = sensorData.doorOpen;
  doc["relay_on"] = state.relayState;
  doc["alert_active"] = state.alertActive;
  doc["simulation_mode"] = config.simulationMode;
  
  String body;
  serializeJson(doc, body);
  
  int code = http.POST(body);
  http.end();
  
  if (code == 201 || code == 200) {
    Serial.println("[SUPABASE] ✓ Lectura enviada");
    return true;
  } else {
    Serial.printf("[SUPABASE] ✗ Error: %d\n", code);
    return false;
  }
}

// Sincronización periódica con Supabase
void supabaseSync() {
  if (!config.supabaseEnabled) return;
  
  unsigned long now = millis();
  
  // Enviar lectura cada SUPABASE_SYNC_INTERVAL (5 segundos)
  if (now - state.lastSupabaseSync >= SUPABASE_SYNC_INTERVAL) {
    state.lastSupabaseSync = now;
    
    if (state.internetAvailable) {
      supabaseSendReading();
    }
  }
}

void sendAlertToSupabase(String alertType, String severity, String message) {
  if (!config.supabaseEnabled || !state.internetAvailable) return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/alerts";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;
  doc["alert_type"] = alertType;
  doc["severity"] = severity;
  doc["message"] = message;
  
  String body;
  serializeJson(doc, body);
  
  int code = http.POST(body);
  http.end();
  
  if (code == 201 || code == 200) {
    Serial.println("[SUPABASE] ✓ Alerta enviada");
  }
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
  server.on("/api/defrost", HTTP_POST, handleApiDefrost);
  server.on("/api/wifi/reset", HTTP_POST, handleApiWifiReset);
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
  sys["alert_acknowledged"] = state.alertAcknowledged;
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
  sys["defrost_mode"] = state.defrostMode;
  sys["defrost_minutes"] = state.defrostMode ? (millis() - state.defrostStartTime) / 60000 : 0;
  sys["supabase_enabled"] = config.supabaseEnabled;
  
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
  doc["defrost_cooldown_sec"] = config.defrostCooldownSec;
  doc["defrost_relay_nc"] = config.defrostRelayNC;
  doc["relay_enabled"] = config.relayEnabled;
  doc["buzzer_enabled"] = config.buzzerEnabled;
  doc["telegram_enabled"] = config.telegramEnabled;
  doc["supabase_enabled"] = config.supabaseEnabled;
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
  if (doc.containsKey("defrost_cooldown_sec")) config.defrostCooldownSec = doc["defrost_cooldown_sec"];
  if (doc.containsKey("defrost_relay_nc")) config.defrostRelayNC = doc["defrost_relay_nc"];
  if (doc.containsKey("relay_enabled")) config.relayEnabled = doc["relay_enabled"];
  if (doc.containsKey("buzzer_enabled")) config.buzzerEnabled = doc["buzzer_enabled"];
  if (doc.containsKey("telegram_enabled")) config.telegramEnabled = doc["telegram_enabled"];
  if (doc.containsKey("supabase_enabled")) config.supabaseEnabled = doc["supabase_enabled"];
  if (doc.containsKey("sensor1_enabled")) config.sensor1Enabled = doc["sensor1_enabled"];
  if (doc.containsKey("sensor2_enabled")) config.sensor2Enabled = doc["sensor2_enabled"];
  if (doc.containsKey("dht22_enabled")) config.dht22Enabled = doc["dht22_enabled"];
  if (doc.containsKey("door_enabled")) config.doorEnabled = doc["door_enabled"];
  if (doc.containsKey("simulation_mode")) config.simulationMode = doc["simulation_mode"];
  if (doc.containsKey("sim_temp1")) config.simTemp1 = doc["sim_temp1"];
  if (doc.containsKey("sim_temp2")) config.simTemp2 = doc["sim_temp2"];
  if (doc.containsKey("sim_door_open")) config.simDoorOpen = doc["sim_door_open"];
  
  Serial.printf("[CONFIG] Guardado: tempCritical=%.1f, alertDelay=%d sec, defrostCooldown=%d sec, defrostNC=%d\n",
                config.tempCritical, config.alertDelaySec, config.defrostCooldownSec, config.defrostRelayNC);
  
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

void handleApiDefrost() {
  state.defrostMode = !state.defrostMode;
  
  if (state.defrostMode) {
    // Activando modo descongelamiento
    state.defrostStartTime = millis();
    // Detener cualquier alerta activa
    state.alertActive = false;
    state.criticalAlert = false;
    state.alertMessage = "";
    setRelay(false);
    digitalWrite(PIN_BUZZER, LOW);
    digitalWrite(PIN_LED_ALERT, LOW);
    
    Serial.println("[DESCONGELAMIENTO] *** MODO ACTIVADO - Alertas deshabilitadas ***");
    
    // Notificación simple a Telegram
    if (state.internetAvailable && config.telegramEnabled) {
      sendTelegramMessage("🧊 *MODO DESCONGELAMIENTO ACTIVADO*\n\n⚠️ Las alertas están deshabilitadas temporalmente.\n\n📍 " + String(LOCATION_DETAIL));
    }
  } else {
    // Desactivando modo descongelamiento
    unsigned long defrostMin = (millis() - state.defrostStartTime) / 60000;
    state.defrostStartTime = 0;
    telegramAlertSent = false;  // Permitir nuevas alertas
    
    Serial.printf("[DESCONGELAMIENTO] *** MODO DESACTIVADO - Duró %lu minutos ***\n", defrostMin);
    
    // Notificación simple a Telegram
    if (state.internetAvailable && config.telegramEnabled) {
      sendTelegramMessage("✅ *MODO DESCONGELAMIENTO FINALIZADO*\n\n🔔 Las alertas están activas nuevamente.\n⏱️ Duración: " + String(defrostMin) + " minutos\n\n📍 " + String(LOCATION_DETAIL));
    }
  }
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true,\"defrost_mode\":" + String(state.defrostMode ? "true" : "false") + "}");
}

void handleApiWifiReset() {
  server.send(200, "application/json", "{\"success\":true,\"message\":\"Reiniciando en modo AP...\"}");
  delay(1000);
  wifiManager.resetSettings();
  ESP.restart();
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
  <title>Alerta REEFER</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#0f172a;min-height:100vh;color:#fff;padding:16px}
    .container{max-width:500px;margin:0 auto}
    h1{text-align:center;margin-bottom:4px;font-size:1.8em;color:#60a5fa}
    .subtitle{text-align:center;color:#64748b;margin-bottom:20px;font-size:.9em}
    .card{background:#1e293b;border-radius:12px;padding:16px;margin-bottom:12px}
    .card h2{margin-bottom:12px;color:#fff;font-size:1em;display:flex;align-items:center;gap:8px}
    .temp-big{text-align:center;font-size:4em;font-weight:bold;margin:16px 0}
    .temp-ok{color:#22d3ee}
    .temp-warn{color:#fbbf24}
    .temp-crit{color:#ef4444;animation:pulse .5s infinite}
    @keyframes pulse{0%,100%{opacity:1}50%{opacity:.7}}
    .status-row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #334155}
    .status-row:last-child{border-bottom:none}
    .status-label{color:#94a3b8}
    .status-value{font-weight:bold}
    .alert-banner{background:#ef4444;padding:16px;border-radius:12px;text-align:center;margin-bottom:12px;display:none}
    .alert-banner.active{display:block}
    .alert-banner.silenced{background:#f97316}
    button{width:100%;padding:14px;border:none;border-radius:10px;font-size:1em;font-weight:600;cursor:pointer;margin-top:8px}
    .btn-green{background:#22c55e;color:#fff}
    .btn-blue{background:#3b82f6;color:#fff}
    .btn-orange{background:#f97316;color:#fff}
    .btn-red{background:#ef4444;color:#fff}
    .input-row{margin-bottom:12px}
    .input-row label{display:block;margin-bottom:6px;color:#94a3b8;font-size:.85em}
    .input-group{display:flex;gap:8px}
    .input-group input{flex:1;padding:12px;border-radius:8px;border:1px solid #334155;background:#0f172a;color:#fff;font-size:1em}
    .input-group span{color:#64748b;padding:12px 0;min-width:40px}
    .config-value{background:#0f172a;padding:8px 12px;border-radius:6px;margin-top:4px;font-size:.9em}
    .footer{text-align:center;padding:20px;color:#64748b;font-size:.75em}
    .footer strong{color:#94a3b8}
  </style>
</head>
<body>
  <div class="container">
    <h1>❄️ Alerta REEFER</h1>
    <p class="subtitle">PANDEMONIUM TECH × PAN AMERICAN SILVER</p>
    
    <div class="alert-banner" id="alertBanner">
      <div style="font-size:1.2em;font-weight:bold">🚨 ALERTA ACTIVA</div>
      <div id="alertMsg" style="margin:8px 0">Temperatura crítica</div>
      <button class="btn-green" onclick="stopAlert()">🛑 DETENER ALERTA</button>
    </div>
    
    <div class="card">
      <h2>🌡️ TEMPERATURA</h2>
      <div class="temp-big temp-ok" id="temp">--.-°C</div>
    </div>
    
    <div class="card">
      <h2>📊 Estado del Sistema</h2>
      <div class="status-row">
        <span class="status-label">Puerta</span>
        <span class="status-value" id="doorStatus">Inhabilitado</span>
      </div>
      <div class="status-row">
        <span class="status-label">Sirena</span>
        <span class="status-value" id="relayStatus">Apagada</span>
      </div>
      <div class="status-row">
        <span class="status-label">Señal Descongelamiento</span>
        <span class="status-value" id="defrostSignal">--</span>
      </div>
      <div class="status-row">
        <span class="status-label">Uptime</span>
        <span class="status-value" id="uptime">--</span>
      </div>
      <div class="status-row">
        <span class="status-label">WiFi</span>
        <span class="status-value" id="wifiRssi">-- dBm</span>
      </div>
      <div class="status-row">
        <span class="status-label">Internet</span>
        <span class="status-value" id="internetStatus">--</span>
      </div>
      <div class="status-row">
        <span class="status-label">IP</span>
        <span class="status-value" id="deviceIp">--</span>
      </div>
    </div>
    
    <div class="card">
      <h2>📋 Valores Configurados</h2>
      <div class="status-row">
        <span class="status-label">🌡️ Temp. Crítica</span>
        <span class="status-value" id="cfgTempCrit">--°C</span>
      </div>
      <div class="status-row">
        <span class="status-label">⏱️ Tiempo espera</span>
        <span class="status-value" id="cfgAlertDelay">-- min</span>
      </div>
      <div class="status-row">
        <span class="status-label">🧊 Post-descongelación</span>
        <span class="status-value" id="cfgDefrostCooldown">-- min</span>
      </div>
      <div class="status-row">
        <span class="status-label">🔌 Relé Descong.</span>
        <span class="status-value" id="cfgDefrostRelay">--</span>
      </div>
    </div>
    
    <div class="card">
      <h2>⚙️ Configuración</h2>
      <div class="input-row">
        <label>🌡️ Temperatura Crítica (°C)</label>
        <div class="input-group">
          <input type="number" id="inTempCrit" value="-10" step="0.5">
          <span>°C</span>
        </div>
      </div>
      <div class="input-row">
        <label>⏱️ Tiempo de espera (minutos)</label>
        <div class="input-group">
          <input type="number" id="inAlertDelay" value="5" step="1" min="1">
          <span>min</span>
        </div>
      </div>
      <div class="input-row">
        <label>🧊 Post-descongelación (minutos)</label>
        <div class="input-group">
          <input type="number" id="inDefrostCooldown" value="30" step="5" min="5">
          <span>min</span>
        </div>
      </div>
      <button class="btn-blue" onclick="saveConfig()">💾 GUARDAR</button>
    </div>
    
    <div class="card" id="defrostCard">
      <h2>🧊 Modo Descongelamiento</h2>
      <div id="defrostStatus" style="display:none;background:#f97316;padding:10px;border-radius:8px;margin-bottom:10px;text-align:center">
        ⏱️ Activo: <strong id="defrostTime">0</strong> min
      </div>
      <button class="btn-orange" id="btnDefrost" onclick="toggleDefrost()">🧊 ACTIVAR DESCONGELAMIENTO</button>
    </div>
    
    <div class="card">
      <h2>📱 Acciones</h2>
      <button class="btn-blue" onclick="testTelegram()">📲 Probar Telegram</button>
      <button class="btn-red" onclick="resetWifi()" style="margin-top:8px">📡 Reset WiFi</button>
    </div>
    
    <div class="footer">
      <div>Última actualización: <span id="lastUpdate">--</span></div>
      <div style="margin-top:8px"><strong>PANDEMONIUM TECH</strong> × <strong>PAN AMERICAN SILVER</strong></div>
    </div>
  </div>
  
  <script>
    let alertActive=false,alertAck=false,defrostMode=false;
    
    async function fetchStatus(){
      try{
        const r=await fetch('/api/status');
        const d=await r.json();
        const t=d.sensor.temp_avg.toFixed(1);
        const tempEl=document.getElementById('temp');
        tempEl.textContent=t+'°C';
        tempEl.className='temp-big';
        if(parseFloat(t)>-10)tempEl.classList.add('temp-crit');
        else if(parseFloat(t)>-18)tempEl.classList.add('temp-warn');
        else tempEl.classList.add('temp-ok');
        
        document.getElementById('doorStatus').textContent='Inhabilitado';
        document.getElementById('doorStatus').style.color='#64748b';
        
        const relayOn=d.system.relay_on;
        alertActive=d.system.alert_active;
        alertAck=d.system.alert_acknowledged||false;
        document.getElementById('relayStatus').textContent=(alertActive&&!alertAck)?'PRENDIDA':'Apagada';
        document.getElementById('relayStatus').style.color=(alertActive&&!alertAck)?'#ef4444':'#94a3b8';
        
        document.getElementById('defrostSignal').textContent=defrostMode?'ACTIVA':'Normal';
        document.getElementById('defrostSignal').style.color=defrostMode?'#f97316':'#22c55e';
        
        document.getElementById('uptime').textContent=formatUptime(d.system.uptime_sec);
        document.getElementById('wifiRssi').textContent=d.system.wifi_rssi+' dBm';
        document.getElementById('internetStatus').textContent=d.system.internet?'✓ Online':'✗ Offline';
        document.getElementById('internetStatus').style.color=d.system.internet?'#22c55e':'#ef4444';
        document.getElementById('deviceIp').textContent=d.device.ip;
        document.getElementById('lastUpdate').textContent=new Date().toLocaleTimeString();
        
        const banner=document.getElementById('alertBanner');
        if(alertActive){
          banner.classList.add('active');
          banner.classList.toggle('silenced',alertAck);
          document.getElementById('alertMsg').textContent=alertAck?'🔕 SILENCIADA - '+d.system.alert_message:d.system.alert_message;
        }else{
          banner.classList.remove('active');
        }
        
        defrostMode=d.system.defrost_mode||false;
        document.getElementById('defrostStatus').style.display=defrostMode?'block':'none';
        document.getElementById('defrostTime').textContent=d.system.defrost_minutes||0;
        document.getElementById('btnDefrost').textContent=defrostMode?'✅ DESACTIVAR':'🧊 ACTIVAR DESCONGELAMIENTO';
        document.getElementById('btnDefrost').className=defrostMode?'btn-green':'btn-orange';
      }catch(e){console.error(e);}
    }
    
    async function loadConfig(){
      try{
        const r=await fetch('/api/config');
        const c=await r.json();
        document.getElementById('inTempCrit').value=c.temp_critical;
        document.getElementById('inAlertDelay').value=Math.round(c.alert_delay_sec/60);
        document.getElementById('inDefrostCooldown').value=Math.round((c.defrost_cooldown_sec||1800)/60);
        document.getElementById('cfgTempCrit').textContent=c.temp_critical+'°C';
        document.getElementById('cfgAlertDelay').textContent=Math.round(c.alert_delay_sec/60)+' min';
        document.getElementById('cfgDefrostCooldown').textContent=Math.round((c.defrost_cooldown_sec||1800)/60)+' min';
        document.getElementById('cfgDefrostRelay').textContent=c.defrost_relay_nc?'Normal Cerrado (NC)':'Normal Abierto (NO)';
      }catch(e){}
    }
    
    async function saveConfig(){
      const tc=parseFloat(document.getElementById('inTempCrit').value);
      const ad=parseInt(document.getElementById('inAlertDelay').value)*60;
      const dc=parseInt(document.getElementById('inDefrostCooldown').value)*60;
      await fetch('/api/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({temp_critical:tc,alert_delay_sec:ad,defrost_cooldown_sec:dc})});
      alert('✅ Configuración guardada');
      loadConfig();
    }
    
    function formatUptime(s){const h=Math.floor(s/3600),m=Math.floor((s%3600)/60);if(h>0)return h+'h '+m+'m';if(m>0)return m+'m '+(s%60)+'s';return s+'s';}
    
    async function stopAlert(){await fetch('/api/alert/ack',{method:'POST'});fetchStatus();}
    async function testTelegram(){const r=await fetch('/api/telegram/test',{method:'POST'});alert(r.ok?'✅ Mensaje enviado':'❌ Error');}
    async function resetWifi(){if(confirm('¿Resetear WiFi?'))await fetch('/api/wifi/reset',{method:'POST'});}
    async function toggleDefrost(){
      if(confirm(defrostMode?'¿Desactivar descongelamiento?':'¿Activar descongelamiento?\n\nLas alertas se deshabilitarán.')){
        await fetch('/api/defrost',{method:'POST'});
        fetchStatus();
      }
    }
    
    setInterval(fetchStatus,2000);
    fetchStatus();
    loadConfig();
  </script>
</body>
</html>
)rawliteral";
}
