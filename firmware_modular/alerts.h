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
// ACTIVAR ALERTA (con protección anti-duplicados)
// ============================================
static unsigned long lastTriggerTime = 0;

void triggerAlert(String message, bool critical = true) {
  // Si ya hay alerta activa, no hacer nada
  if (state.alertActive) return;
  
  // Debounce: no disparar otra alerta si pasaron menos de 5 segundos
  unsigned long now = millis();
  if (lastTriggerTime > 0 && (now - lastTriggerTime) < 5000) {
    Serial.println("[ALERTA] Ignorando trigger duplicado (debounce)");
    return;
  }
  lastTriggerTime = now;
  
  state.alertActive = true;
  state.criticalAlert = critical;
  state.alertMessage = message;
  state.alertStartTime = now;
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
// RECONOCER ALERTA (con debounce)
// ============================================
void acknowledgeAlert() {
  // Debounce: ignorar si ya se silenció hace menos de 2 segundos
  unsigned long now = millis();
  if (state.lastAckTime > 0 && (now - state.lastAckTime) < 2000) {
    Serial.println("[ALERTA] Ignorando ack duplicado (debounce)");
    return;
  }
  
  state.alertAcknowledged = true;
  state.lastAckTime = now;
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
    state.tempOverCritical = false;
    state.highTempElapsedSec = 0;
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
    state.tempOverCritical = false;
    state.highTempElapsedSec = 0;
    return;
  }
  
  float temp = sensorData.tempAvg;
  unsigned long now = millis();
  
  if (temp > config.tempCritical) {
    state.tempOverCritical = true;
    
    if (lastAlertCheck > 0) {
      highTempSec += (now - lastAlertCheck) / 1000;
    }
    state.highTempElapsedSec = highTempSec;  // Sincronizar con state
    
    // Log de progreso hacia alerta
    static unsigned long lastProgressLog = 0;
    if (now - lastProgressLog > 10000) {
      Serial.printf("⚠️ [ALERTA] Temp %.1f°C > %.1f°C - Tiempo: %lu/%d seg\n", 
        temp, config.tempCritical, highTempSec, config.alertDelaySec);
      lastProgressLog = now;
    }
    
    if (highTempSec >= (unsigned long)config.alertDelaySec && !state.alertActive && !state.alertAcknowledged) {
      String msg = "🌡️ Temperatura CRÍTICA: " + String(temp, 1) + "°C (límite: " + String(config.tempCritical, 1) + "°C)";
      triggerAlert(msg, true);
    }
  } else {
    highTempSec = 0;
    state.highTempElapsedSec = 0;
    state.tempOverCritical = false;
    state.alertAcknowledged = false;
    
    if (state.alertActive) {
      clearAlert();
    }
  }
  
  lastAlertCheck = now;
}

#endif
