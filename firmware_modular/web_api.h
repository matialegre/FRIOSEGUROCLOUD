/*
 * web_api.h - Servidor web y API REST
 * Sistema Monitoreo Reefer v3.0
 */

#ifndef WEB_API_H
#define WEB_API_H

#include <WebServer.h>
#include <ArduinoJson.h>
#include "config.h"
#include "types.h"

extern WebServer server;
extern Config config;
extern SensorData sensorData;
extern SystemState state;

extern void saveConfig();
extern void acknowledgeAlert();
extern void clearAlert();
extern void triggerAlert(String message, bool critical);
extern void setRelay(bool on);
extern bool testTelegram();
extern void resetWiFi();
extern String getEmbeddedHTML();
extern bool supabaseUploadConfig();

// ============================================
// HANDLER: Página principal
// ============================================
void handleRoot() {
  server.send(200, "text/html", getEmbeddedHTML());
}

// ============================================
// HANDLER: API Status
// ============================================
void handleApiStatus() {
  StaticJsonDocument<1024> doc;
  
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
  sys["cooldown_mode"] = state.cooldownMode;
  sys["cooldown_remaining_sec"] = state.cooldownRemainingSec;
  sys["supabase_enabled"] = config.supabaseEnabled;
  
  JsonObject device = doc.createNestedObject("device");
  device["id"] = DEVICE_ID;
  device["name"] = DEVICE_NAME;
  device["ip"] = state.localIP;
  device["mdns"] = String(MDNS_NAME) + ".local";
  
  JsonObject loc = doc.createNestedObject("location");
  loc["name"] = LOCATION_NAME;
  loc["detail"] = LOCATION_DETAIL;
  
  String response;
  serializeJson(doc, response);
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", response);
}

// ============================================
// HANDLER: GET Config
// ============================================
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
  
  String response;
  serializeJson(doc, response);
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", response);
}

// ============================================
// HANDLER: POST Config
// ============================================
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
  
  Serial.printf("[CONFIG] Guardado: tempCrit=%.1f, supabase=%d\n", config.tempCritical, config.supabaseEnabled);
  
  // Marcar timestamp de config local para evitar que Supabase la pise
  state.lastLocalConfigChange = millis();
  
  saveConfig();
  
  // Subir config a Supabase para mantener sincronizado (si está habilitado)
  if (config.supabaseEnabled && state.internetAvailable) {
    supabaseUploadConfig();
  }
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

// ============================================
// HANDLER: Acknowledge Alert
// ============================================
void handleApiAckAlert() {
  acknowledgeAlert();
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

// ============================================
// HANDLER: Test Alert
// ============================================
void handleApiTestAlert() {
  triggerAlert("🧪 Alerta de prueba", true);
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

// ============================================
// HANDLER: Relay Control
// ============================================
void handleApiRelay() {
  if (server.hasArg("plain")) {
    StaticJsonDocument<64> doc;
    deserializeJson(doc, server.arg("plain"));
    if (doc.containsKey("state")) {
      setRelay(doc["state"]);
    }
  }
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
}

// ============================================
// HANDLER: Test Telegram
// ============================================
void handleApiTelegramTest() {
  bool success = testTelegram();
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(success ? 200 : 500, "application/json", 
              success ? "{\"success\":true}" : "{\"error\":\"Failed\"}");
}

// ============================================
// HANDLER: Defrost Mode
// ============================================
void handleApiDefrost() {
  state.defrostMode = !state.defrostMode;
  
  if (state.defrostMode) {
    state.defrostStartTime = millis();
    state.alertActive = false;
    state.criticalAlert = false;
    state.alertAcknowledged = false;
    // Cancelar cooldown si estaba activo
    state.cooldownMode = false;
    state.cooldownRemainingSec = 0;
    state.cooldownStartTime = 0;
    setRelay(false);
    digitalWrite(PIN_BUZZER, LOW);
    Serial.println("[DESCONGELAMIENTO] ACTIVADO MANUALMENTE");
  } else {
    unsigned long defrostMin = (millis() - state.defrostStartTime) / 60000;
    state.defrostStartTime = 0;
    // INICIAR COOLDOWN cuando se desactiva manualmente
    state.cooldownMode = true;
    state.cooldownStartTime = millis();
    state.cooldownRemainingSec = config.defrostCooldownSec;
    Serial.printf("[DESCONGELAMIENTO] DESACTIVADO - Duró %lu min\n", defrostMin);
    Serial.printf("[COOLDOWN] Iniciando espera de %d minutos\n", config.defrostCooldownSec / 60);
  }
  
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true,\"defrost_mode\":" + String(state.defrostMode ? "true" : "false") + ",\"cooldown_mode\":" + String(state.cooldownMode ? "true" : "false") + "}");
}

// ============================================
// HANDLER: WiFi Reset
// ============================================
void handleApiWifiReset() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", "{\"success\":true}");
  delay(500);
  resetWiFi();
}

// ============================================
// HANDLER: CORS Preflight
// ============================================
void handleCORS() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
  server.send(204);
}

// ============================================
// HANDLER: Not Found
// ============================================
void handleNotFound() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(404, "application/json", "{\"error\":\"Not found\"}");
}

// ============================================
// CONFIGURAR RUTAS
// ============================================
void setupWebServer() {
  server.on("/", HTTP_GET, handleRoot);
  server.on("/api/status", HTTP_GET, handleApiStatus);
  server.on("/api/config", HTTP_GET, handleApiGetConfig);
  server.on("/api/config", HTTP_POST, handleApiSetConfig);
  server.on("/api/config", HTTP_OPTIONS, handleCORS);
  server.on("/api/alert/ack", HTTP_POST, handleApiAckAlert);
  server.on("/api/alert/test", HTTP_POST, handleApiTestAlert);
  server.on("/api/relay", HTTP_POST, handleApiRelay);
  server.on("/api/telegram/test", HTTP_POST, handleApiTelegramTest);
  server.on("/api/defrost", HTTP_POST, handleApiDefrost);
  server.on("/api/wifi/reset", HTTP_POST, handleApiWifiReset);
  server.onNotFound(handleNotFound);
  
  server.begin();
  Serial.println("[OK] Web server iniciado");
}

#endif
