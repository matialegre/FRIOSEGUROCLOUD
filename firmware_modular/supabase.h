/*
 * supabase.h - Integración completa con Supabase
 * Sistema Monitoreo Reefer v3.0
 * 
 * Envía TODOS los datos posibles cada 5 segundos a la tabla 'readings'
 * Preparado para expansión futura con múltiples sensores
 * 
 * Tablas utilizadas:
 * - readings: Lecturas de sensores (cada 5 seg)
 * - alerts: Historial de alertas
 * - power_events: Cortes de luz
 * - door_events: Apertura/cierre de puertas
 * - defrost_sessions: Sesiones de descongelamiento
 * - commands: Comandos remotos pendientes
 */

#ifndef SUPABASE_H
#define SUPABASE_H

#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "config.h"
#include "types.h"

extern Config config;
extern SystemState state;
extern SensorData sensorData;

// Variables externas opcionales (de módulos no integrados)
// Se definen como weak para que no fallen si no existen
extern float __attribute__((weak)) currentAmps;
extern bool __attribute__((weak)) compressorRunning;
extern bool __attribute__((weak)) acPowerPresent;
extern float __attribute__((weak)) batteryVoltage;
extern int __attribute__((weak)) gsmSignal;

// ============================================
// ENVIAR LECTURA COMPLETA A SUPABASE
// ============================================
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
  
  // Documento JSON grande para todos los datos
  StaticJsonDocument<1024> doc;
  
  // Identificación
  doc["device_id"] = DEVICE_ID;
  
  // Temperaturas (hasta 4 sensores DS18B20)
  doc["temp1"] = sensorData.temp1;
  doc["temp2"] = sensorData.temp2;
  // doc["temp3"] = sensorData.temp3;  // Futuro
  // doc["temp4"] = sensorData.temp4;  // Futuro
  doc["temp_avg"] = sensorData.tempAvg;
  doc["temp_dht"] = sensorData.tempDHT;
  doc["humidity"] = sensorData.humidity;
  
  // Estado de puertas (hasta 4)
  doc["door1_open"] = sensorData.doorOpen;
  // doc["door2_open"] = door2Open;  // Futuro
  // doc["door3_open"] = door3Open;  // Futuro
  // doc["door4_open"] = door4Open;  // Futuro
  
  // Estado eléctrico (si power_monitor está habilitado)
  #ifdef POWER_MONITOR_H
    doc["ac_power"] = acPowerPresent;
    doc["battery_voltage"] = batteryVoltage;
  #else
    doc["ac_power"] = true;  // Asumir que hay luz si no hay sensor
  #endif
  
  // Corriente del compresor (si current_sensor está habilitado)
  #ifdef CURRENT_SENSOR_H
    doc["current_amps"] = currentAmps;
    doc["compressor_running"] = compressorRunning;
  #endif
  
  // Estado del sistema
  doc["relay_on"] = state.relayState;
  doc["buzzer_on"] = false;  // TODO: agregar variable
  doc["alert_active"] = state.alertActive;
  doc["defrost_mode"] = state.defrostMode;
  doc["simulation_mode"] = config.simulationMode;
  
  // Conectividad
  doc["wifi_rssi"] = WiFi.RSSI();
  #ifdef SIM800_H
    doc["gsm_signal"] = gsmSignal;
  #endif
  
  // Metadata del sistema
  doc["uptime_sec"] = (millis() - state.uptime) / 1000;
  doc["free_heap"] = ESP.getFreeHeap();
  
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

// ============================================
// ENVIAR ALERTA A SUPABASE
// ============================================
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
// ACTUALIZAR ESTADO ONLINE E IP
// ============================================
bool supabaseUpdateDeviceStatus(bool isOnline) {
  if (!config.supabaseEnabled || !state.internetAvailable) {
    return false;
  }
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/devices?device_id=eq." + String(DEVICE_ID);
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  // Enviar estado online e IP
  StaticJsonDocument<256> doc;
  doc["is_online"] = isOnline;
  doc["ip_address"] = state.localIP;
  
  String payload;
  serializeJson(doc, payload);
  
  int code = http.PATCH(payload);
  http.end();
  
  if (code == 200 || code == 204) {
    Serial.printf("[SUPABASE] ✓ Estado actualizado (IP: %s)\n", state.localIP.c_str());
    return true;
  }
  return false;
}

// ============================================
// ENVIAR EVENTO DE PUERTA
// ============================================
void supabaseSendDoorEvent(int doorNumber, const char* doorName, bool opened, 
                           int openDurationSec = 0, float tempAtOpen = 0, float tempAtClose = 0) {
  if (!config.supabaseEnabled || !state.internetAvailable) return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/door_events";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;
  doc["door_number"] = doorNumber;
  doc["door_name"] = doorName;
  doc["event_type"] = opened ? "opened" : "closed";
  
  if (!opened && openDurationSec > 0) {
    doc["open_duration_sec"] = openDurationSec;
    doc["temp_at_open"] = tempAtOpen;
    doc["temp_at_close"] = tempAtClose;
    doc["temp_rise"] = tempAtClose - tempAtOpen;
  }
  
  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
}

// ============================================
// ENVIAR EVENTO DE CORTE DE LUZ
// ============================================
void supabaseSendPowerEvent(bool powerLost, int outageDurationSec = 0, 
                            float minBatteryVoltage = 0, int batteryUsedPercent = 0) {
  if (!config.supabaseEnabled || !state.internetAvailable) return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/power_events";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;
  doc["event_type"] = powerLost ? "power_lost" : "power_restored";
  
  if (!powerLost) {
    doc["outage_duration_sec"] = outageDurationSec;
    doc["min_battery_voltage"] = minBatteryVoltage;
    doc["battery_used_percent"] = batteryUsedPercent;
  }
  
  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
  
  Serial.printf("[SUPABASE] Evento de energía: %s\n", powerLost ? "CORTE" : "RESTAURADO");
}

// ============================================
// INICIAR SESIÓN DE DESCONGELAMIENTO
// ============================================
void supabaseSendDefrostStart(float tempAtStart, const char* triggeredBy = "manual") {
  if (!config.supabaseEnabled || !state.internetAvailable) return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/defrost_sessions";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;
  doc["temp_at_start"] = tempAtStart;
  doc["triggered_by"] = triggeredBy;
  
  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
}

// ============================================
// REGISTRAR DATOS DE MANTENIMIENTO
// ============================================
void supabaseSendMaintenanceLog(float compressorHours, int compressorStarts, 
                                 float maxCurrentEver, const char* notes = "") {
  if (!config.supabaseEnabled || !state.internetAvailable) return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/maintenance_logs";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  http.addHeader("Prefer", "return=minimal");
  
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;
  doc["maintenance_type"] = "compressor_hours";
  doc["compressor_hours"] = compressorHours;
  doc["compressor_starts"] = compressorStarts;
  doc["max_current_ever"] = maxCurrentEver;
  doc["notes"] = notes;
  
  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
}

// ============================================
// VERIFICAR COMANDOS PENDIENTES
// ============================================
String supabaseCheckCommands() {
  if (!config.supabaseEnabled || !state.internetAvailable) return "";
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/commands";
  url += "?device_id=eq." + String(DEVICE_ID);
  url += "&status=eq.pending";
  url += "&order=created_at.asc";
  url += "&limit=1";
  
  http.begin(url);
  http.addHeader("apikey", SUPABASE_ANON_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
  
  int code = http.GET();
  String command = "";
  
  if (code == 200) {
    String response = http.getString();
    StaticJsonDocument<512> doc;
    DeserializationError error = deserializeJson(doc, response);
    
    if (!error && doc.size() > 0) {
      command = doc[0]["command"].as<String>();
      int cmdId = doc[0]["id"];
      
      // Marcar como ejecutado
      http.end();
      
      String updateUrl = String(SUPABASE_URL) + "/rest/v1/commands?id=eq." + String(cmdId);
      http.begin(updateUrl);
      http.addHeader("Content-Type", "application/json");
      http.addHeader("apikey", SUPABASE_ANON_KEY);
      http.addHeader("Authorization", "Bearer " + String(SUPABASE_ANON_KEY));
      http.PATCH("{\"status\":\"executed\"}");
      
      Serial.printf("[SUPABASE] Comando recibido: %s\n", command.c_str());
    }
  }
  
  http.end();
  return command;
}

// ============================================
// SINCRONIZACIÓN PERIÓDICA
// ============================================
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
  
  // Actualizar estado del dispositivo (online + IP) cada 60 segundos
  static unsigned long lastDeviceUpdate = 0;
  if (now - lastDeviceUpdate >= 60000) {
    lastDeviceUpdate = now;
    
    if (state.internetAvailable) {
      supabaseUpdateDeviceStatus(true);
    }
  }
  
  // Verificar comandos cada 30 segundos
  static unsigned long lastCommandCheck = 0;
  if (now - lastCommandCheck >= 30000) {
    lastCommandCheck = now;
    
    if (state.internetAvailable) {
      String cmd = supabaseCheckCommands();
      if (cmd.length() > 0) {
        // Procesar comando (integrar con serial_api.h)
        // processCommand(cmd);
      }
    }
  }
}

#endif
