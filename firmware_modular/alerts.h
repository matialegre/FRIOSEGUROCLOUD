/*
 * alerts.h - Lógica de alertas y alarmas
 * Sistema Monitoreo Reefer v3.0
 */

#ifndef ALERTS_H
#define ALERTS_H

#include "config.h"
#include "types.h"

extern Config config;
extern SensorData sensorData;
extern SystemState state;

extern void setRelay(bool on);
extern void sendTelegramAlert(String message);
extern void sendAlertToSupabase(String alertType, String severity, String message);

static unsigned long highTempSec = 0;
static unsigned long lastAlertCheck = 0;

// ============================================
// ACTIVAR ALERTA
// ============================================
void triggerAlert(String message, bool critical = true) {
  if (state.alertActive) return;
  
  state.alertActive = true;
  state.criticalAlert = critical;
  state.alertMessage = message;
  state.alertStartTime = millis();
  state.totalAlerts++;
  state.alertAcknowledged = false;
  
  Serial.println("🚨 [ALERTA] " + message);
  
  if (config.relayEnabled) {
    setRelay(true);
  }
  if (config.buzzerEnabled) {
    digitalWrite(PIN_BUZZER, HIGH);
  }
  
  if (config.telegramEnabled && state.internetAvailable) {
    unsigned long now = millis();
    if (now - state.lastTelegramAlert > 300000) {
      sendTelegramAlert("🚨 *ALERTA CRÍTICA*\n\n" + message);
      state.lastTelegramAlert = now;
    }
  }
  
  if (config.supabaseEnabled && state.internetAvailable) {
    sendAlertToSupabase("temperature", critical ? "critical" : "warning", message);
  }
}

// ============================================
// DESACTIVAR ALERTA
// ============================================
void clearAlert() {
  state.alertActive = false;
  state.criticalAlert = false;
  state.alertMessage = "";
  state.alertAcknowledged = false;
  
  setRelay(false);
  digitalWrite(PIN_BUZZER, LOW);
  
  Serial.println("✅ [ALERTA] Alerta desactivada");
}

// ============================================
// RECONOCER ALERTA
// ============================================
void acknowledgeAlert() {
  state.alertAcknowledged = true;
  setRelay(false);
  digitalWrite(PIN_BUZZER, LOW);
  Serial.println("🔕 [ALERTA] Alerta silenciada");
}

// ============================================
// VERIFICAR ALERTAS
// ============================================
void checkAlerts() {
  if (!sensorData.valid) return;
  
  // Suspender alertas durante descongelamiento
  if (state.defrostMode) {
    static unsigned long lastDefrostLog = 0;
    if (millis() - lastDefrostLog > 30000) {
      unsigned long defrostMin = (millis() - state.defrostStartTime) / 60000;
      Serial.printf("[DESCONGELAMIENTO] Activo hace %lu min - Alertas suspendidas\n", defrostMin);
      lastDefrostLog = millis();
    }
    return;
  }
  
  // Suspender alertas durante cooldown post-descongelamiento
  if (state.cooldownMode) {
    static unsigned long lastCooldownLog = 0;
    if (millis() - lastCooldownLog > 30000) {
      int minRemaining = state.cooldownRemainingSec / 60;
      int secRemaining = state.cooldownRemainingSec % 60;
      Serial.printf("[COOLDOWN] Esperando %d:%02d min - Alertas suspendidas\n", minRemaining, secRemaining);
      lastCooldownLog = millis();
    }
    return;
  }
  
  float temp = sensorData.tempAvg;
  unsigned long now = millis();
  
  if (temp > config.tempCritical) {
    if (lastAlertCheck > 0) {
      highTempSec += (now - lastAlertCheck) / 1000;
    }
    
    if (highTempSec >= (unsigned long)config.alertDelaySec && !state.alertActive && !state.alertAcknowledged) {
      String msg = "🌡️ Temperatura CRÍTICA: " + String(temp, 1) + "°C (límite: " + String(config.tempCritical, 1) + "°C)";
      triggerAlert(msg, true);
    }
  } else {
    highTempSec = 0;
    state.alertAcknowledged = false;
    
    if (state.alertActive) {
      clearAlert();
    }
  }
  
  lastAlertCheck = now;
}

#endif
