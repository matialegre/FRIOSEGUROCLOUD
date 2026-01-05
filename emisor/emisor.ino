/*
 * ============================================================================
 * EMISOR - Sistema de Monitoreo Campamento Parametican Silver
 * ============================================================================
 * Hardware: ESP8266 NodeMCU + 2x DS18B20 + Reed Switch
 * Ubicación: RIFT 1 (Depósito de comida refrigerado)
 * 
 * Conexiones:
 *   - D4 (GPIO2): DS18B20 sensores (bus OneWire)
 *   - D5 (GPIO14): Reed Switch (sensor magnético puerta)
 * ============================================================================
 */

#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClient.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <ArduinoJson.h>

// ============================================================================
// CONFIGURACIÓN - MODIFICAR SEGÚN INSTALACIÓN
// ============================================================================

// WiFi del campamento
const char* WIFI_SSID = "PARAMETICAN_WIFI";
const char* WIFI_PASSWORD = "password123";

// IP del receptor ESP32 (configurar IP fija en el receptor)
const char* RECEPTOR_IP = "192.168.1.100";
const int RECEPTOR_PORT = 80;

// Identificación de este RIFT
const int RIFT_ID = 1;
const char* RIFT_NAME = "RIFT-01";
const char* RIFT_LOCATION = "Deposito Principal";

// Intervalo de envío (milisegundos)
const unsigned long SEND_INTERVAL = 30000; // 30 segundos

// ============================================================================
// PINES
// ============================================================================

#define ONE_WIRE_BUS D4      // GPIO2 - Bus OneWire para DS18B20
#define DOOR_SENSOR_PIN D5   // GPIO14 - Reed Switch (puerta)
#define LED_STATUS LED_BUILTIN

// ============================================================================
// OBJETOS Y VARIABLES
// ============================================================================

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);

// Direcciones de los sensores DS18B20 (se detectan automáticamente)
DeviceAddress sensor1Address, sensor2Address;
int sensorCount = 0;

// Variables de estado
float temperature1 = -999.0;
float temperature2 = -999.0;
float temperatureAvg = -999.0;
bool doorOpen = false;
unsigned long doorOpenTime = 0;
unsigned long lastDoorChange = 0;

// Timing
unsigned long lastSendTime = 0;
unsigned long lastReconnectAttempt = 0;

// Estadísticas
unsigned long totalReadings = 0;
unsigned long successfulSends = 0;
unsigned long failedSends = 0;

// ============================================================================
// SETUP
// ============================================================================

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println();
  Serial.println("============================================");
  Serial.println("  EMISOR - Campamento Parametican Silver");
  Serial.println("  RIFT: " + String(RIFT_NAME));
  Serial.println("============================================");
  
  // Configurar pines
  pinMode(LED_STATUS, OUTPUT);
  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP);
  
  // Inicializar sensores de temperatura
  initTemperatureSensors();
  
  // Conectar WiFi
  connectWiFi();
  
  // Leer estado inicial de puerta
  doorOpen = (digitalRead(DOOR_SENSOR_PIN) == HIGH);
  
  Serial.println("\n[OK] Sistema inicializado correctamente");
  Serial.println("============================================\n");
}

// ============================================================================
// LOOP PRINCIPAL
// ============================================================================

void loop() {
  // Verificar conexión WiFi
  if (WiFi.status() != WL_CONNECTED) {
    reconnectWiFi();
  }
  
  // Leer estado de puerta (con debounce)
  checkDoorStatus();
  
  // Enviar datos periódicamente
  if (millis() - lastSendTime >= SEND_INTERVAL) {
    readTemperatures();
    sendDataToReceptor();
    lastSendTime = millis();
  }
  
  // Parpadeo LED según estado
  updateStatusLED();
  
  delay(100);
}

// ============================================================================
// FUNCIONES DE SENSORES
// ============================================================================

void initTemperatureSensors() {
  Serial.println("\n[INIT] Inicializando sensores de temperatura...");
  
  sensors.begin();
  
  // Configurar resolución (12 bits = mayor precisión)
  sensors.setResolution(12);
  
  // Detectar sensores conectados
  sensorCount = sensors.getDeviceCount();
  Serial.println("[INFO] Sensores detectados: " + String(sensorCount));
  
  if (sensorCount >= 1) {
    if (sensors.getAddress(sensor1Address, 0)) {
      Serial.print("[OK] Sensor 1: ");
      printAddress(sensor1Address);
    }
  }
  
  if (sensorCount >= 2) {
    if (sensors.getAddress(sensor2Address, 1)) {
      Serial.print("[OK] Sensor 2: ");
      printAddress(sensor2Address);
    }
  }
  
  if (sensorCount == 0) {
    Serial.println("[WARN] No se detectaron sensores DS18B20!");
    Serial.println("[WARN] Verificar conexiones:");
    Serial.println("       - DATA -> D4 (GPIO2)");
    Serial.println("       - VCC -> 3.3V");
    Serial.println("       - GND -> GND");
    Serial.println("       - Resistencia 4.7kΩ entre DATA y VCC");
  }
}

void printAddress(DeviceAddress deviceAddress) {
  for (uint8_t i = 0; i < 8; i++) {
    if (deviceAddress[i] < 16) Serial.print("0");
    Serial.print(deviceAddress[i], HEX);
  }
  Serial.println();
}

void readTemperatures() {
  Serial.println("\n[READ] Leyendo temperaturas...");
  
  sensors.requestTemperatures();
  delay(750); // Esperar conversión (12 bits)
  
  if (sensorCount >= 1) {
    temperature1 = sensors.getTempC(sensor1Address);
    if (temperature1 == DEVICE_DISCONNECTED_C) {
      temperature1 = -999.0;
      Serial.println("[ERROR] Sensor 1 desconectado");
    } else {
      Serial.println("[OK] Sensor 1: " + String(temperature1, 2) + "°C");
    }
  }
  
  if (sensorCount >= 2) {
    temperature2 = sensors.getTempC(sensor2Address);
    if (temperature2 == DEVICE_DISCONNECTED_C) {
      temperature2 = -999.0;
      Serial.println("[ERROR] Sensor 2 desconectado");
    } else {
      Serial.println("[OK] Sensor 2: " + String(temperature2, 2) + "°C");
    }
  }
  
  // Calcular promedio (solo de sensores válidos)
  if (temperature1 != -999.0 && temperature2 != -999.0) {
    temperatureAvg = (temperature1 + temperature2) / 2.0;
  } else if (temperature1 != -999.0) {
    temperatureAvg = temperature1;
  } else if (temperature2 != -999.0) {
    temperatureAvg = temperature2;
  } else {
    temperatureAvg = -999.0;
  }
  
  if (temperatureAvg != -999.0) {
    Serial.println("[OK] Promedio: " + String(temperatureAvg, 2) + "°C");
  }
  
  totalReadings++;
}

// ============================================================================
// FUNCIONES DE PUERTA
// ============================================================================

void checkDoorStatus() {
  static bool lastDoorState = false;
  static unsigned long lastDebounceTime = 0;
  const unsigned long debounceDelay = 50;
  
  bool currentState = (digitalRead(DOOR_SENSOR_PIN) == HIGH);
  
  if (currentState != lastDoorState) {
    lastDebounceTime = millis();
  }
  
  if ((millis() - lastDebounceTime) > debounceDelay) {
    if (currentState != doorOpen) {
      doorOpen = currentState;
      lastDoorChange = millis();
      
      if (doorOpen) {
        doorOpenTime = millis();
        Serial.println("\n[DOOR] >>> PUERTA ABIERTA <<<");
      } else {
        unsigned long openDuration = millis() - doorOpenTime;
        Serial.println("\n[DOOR] >>> PUERTA CERRADA <<< (abierta por " + 
                       String(openDuration / 1000) + " segundos)");
      }
    }
  }
  
  lastDoorState = currentState;
}

// ============================================================================
// FUNCIONES DE COMUNICACIÓN
// ============================================================================

void connectWiFi() {
  Serial.println("\n[WIFI] Conectando a " + String(WIFI_SSID) + "...");
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[OK] WiFi conectado!");
    Serial.println("[INFO] IP: " + WiFi.localIP().toString());
    Serial.println("[INFO] RSSI: " + String(WiFi.RSSI()) + " dBm");
  } else {
    Serial.println("\n[ERROR] No se pudo conectar al WiFi");
  }
}

void reconnectWiFi() {
  if (millis() - lastReconnectAttempt > 10000) {
    Serial.println("[WIFI] Reconectando...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    lastReconnectAttempt = millis();
  }
}

void sendDataToReceptor() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[ERROR] Sin conexión WiFi, no se puede enviar");
    failedSends++;
    return;
  }
  
  Serial.println("\n[SEND] Enviando datos al receptor...");
  
  // Crear JSON con los datos
  StaticJsonDocument<512> doc;
  
  doc["rift_id"] = RIFT_ID;
  doc["rift_name"] = RIFT_NAME;
  doc["location"] = RIFT_LOCATION;
  doc["temp1"] = temperature1;
  doc["temp2"] = temperature2;
  doc["temp_avg"] = temperatureAvg;
  doc["door_open"] = doorOpen;
  doc["door_open_since"] = doorOpen ? (millis() - doorOpenTime) / 1000 : 0;
  doc["sensor_count"] = sensorCount;
  doc["rssi"] = WiFi.RSSI();
  doc["uptime"] = millis() / 1000;
  doc["readings"] = totalReadings;
  doc["timestamp"] = millis();
  
  String jsonString;
  serializeJson(doc, jsonString);
  
  // Enviar HTTP POST
  WiFiClient client;
  HTTPClient http;
  
  String url = "http://" + String(RECEPTOR_IP) + ":" + String(RECEPTOR_PORT) + "/api/data";
  
  http.begin(client, url);
  http.addHeader("Content-Type", "application/json");
  http.setTimeout(5000);
  
  int httpCode = http.POST(jsonString);
  
  if (httpCode > 0) {
    if (httpCode == HTTP_CODE_OK) {
      String response = http.getString();
      Serial.println("[OK] Datos enviados correctamente");
      Serial.println("[RESP] " + response);
      successfulSends++;
    } else {
      Serial.println("[ERROR] HTTP Code: " + String(httpCode));
      failedSends++;
    }
  } else {
    Serial.println("[ERROR] Fallo de conexión: " + http.errorToString(httpCode));
    failedSends++;
  }
  
  http.end();
  
  // Mostrar estadísticas
  Serial.println("[STATS] Enviados: " + String(successfulSends) + 
                 " | Fallidos: " + String(failedSends));
}

// ============================================================================
// FUNCIONES DE ESTADO
// ============================================================================

void updateStatusLED() {
  static unsigned long lastBlink = 0;
  static bool ledState = false;
  
  unsigned long blinkInterval;
  
  if (WiFi.status() != WL_CONNECTED) {
    blinkInterval = 200; // Parpadeo rápido = sin WiFi
  } else if (temperatureAvg == -999.0) {
    blinkInterval = 500; // Parpadeo medio = sin sensores
  } else {
    blinkInterval = 2000; // Parpadeo lento = todo OK
  }
  
  if (millis() - lastBlink >= blinkInterval) {
    ledState = !ledState;
    digitalWrite(LED_STATUS, ledState ? LOW : HIGH); // LED invertido en NodeMCU
    lastBlink = millis();
  }
}
