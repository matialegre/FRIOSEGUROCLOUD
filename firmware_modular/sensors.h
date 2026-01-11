/*
 * sensors.h - Lectura de sensores DS18B20 y DHT22
 * Sistema Monitoreo Reefer v3.0
 */

#ifndef SENSORS_H
#define SENSORS_H

#include <OneWire.h>
#include <DallasTemperature.h>
#include <DHT.h>
#include "config.h"
#include "types.h"

extern OneWire oneWire;
extern DallasTemperature ds18b20;
extern DHT dht;
extern Config config;
extern SensorData sensorData;

// ============================================
// INICIALIZACIÓN DE SENSORES
// ============================================
void initSensors() {
  Serial.println("[SENSOR] Iniciando DS18B20...");
  
  ds18b20.begin();
  delay(500);
  
  int count = ds18b20.getDeviceCount();
  Serial.printf("[SENSOR] Sensores detectados: %d\n", count);
  
  sensorData.sensorCount = count;
  
  if (count == 0) {
    Serial.println("[SENSOR] ⚠️ NO HAY SENSORES - Reintentando...");
    delay(1000);
    ds18b20.begin();
    delay(500);
    count = ds18b20.getDeviceCount();
    sensorData.sensorCount = count;
    Serial.printf("[SENSOR] Segundo intento: %d sensores\n", count);
  }
  
  if (count > 0) {
    ds18b20.setResolution(12);
    ds18b20.setWaitForConversion(true);
    ds18b20.requestTemperatures();
    float t = ds18b20.getTempCByIndex(0);
    Serial.printf("[SENSOR] >>> TEMPERATURA INICIAL: %.2f C <<<\n", t);
    
    if (t > -55 && t < 125) {
      sensorData.temp1 = t;
      sensorData.tempAvg = t;
      sensorData.valid = true;
    }
    
    ds18b20.setWaitForConversion(false);
  }
  
  if (config.dht22Enabled) {
    dht.begin();
    Serial.println("[SENSOR] DHT22 inicializado");
  }
}

// ============================================
// LECTURA DE SENSORES
// ============================================
void readSensors() {
  static unsigned long lastLog = 0;
  
  if (config.simulationMode) {
    sensorData.temp1 = config.simTemp1;
    sensorData.temp2 = config.simTemp2;
    sensorData.tempAvg = (config.simTemp1 + config.simTemp2) / 2.0;
    sensorData.doorOpen = config.simDoorOpen;
    sensorData.valid = true;
    return;
  }
  
  ds18b20.requestTemperatures();
  delay(750);
  
  if (sensorData.sensorCount > 0) {
    float t1 = ds18b20.getTempCByIndex(0);
    
    if (millis() - lastLog > 10000) {
      Serial.printf("[LOOP] Temp: %.2f C\n", t1);
      lastLog = millis();
    }
    
    bool valid = (t1 > -55 && t1 < 125);
    if (valid) {
      sensorData.temp1 = t1;
      sensorData.valid = true;
      
      if (config.sensor2Enabled && sensorData.sensorCount > 1) {
        float t2 = ds18b20.getTempCByIndex(1);
        if (t2 > -55 && t2 < 125) {
          sensorData.temp2 = t2;
          sensorData.tempAvg = (t1 + t2) / 2.0;
        } else {
          sensorData.tempAvg = t1;
        }
      } else {
        sensorData.tempAvg = t1;
      }
    }
  }
  
  if (config.dht22Enabled) {
    float h = dht.readHumidity();
    float t = dht.readTemperature();
    if (!isnan(h)) sensorData.humidity = h;
    if (!isnan(t)) sensorData.tempDHT = t;
  }
  
  if (config.doorEnabled) {
    sensorData.doorOpen = digitalRead(PIN_DOOR) == HIGH;
  } else {
    sensorData.doorOpen = false;
  }
  
  if (sensorData.doorOpen) {
    if (sensorData.doorOpenSince == 0) {
      sensorData.doorOpenSince = millis();
    }
  } else {
    sensorData.doorOpenSince = 0;
  }
}

// ============================================
// DETECCIÓN AUTOMÁTICA DE DESCONGELAMIENTO
// ============================================
// Lee el pin del relé del reefer para detectar cuando entra en defrost
// - NC (Normalmente Cerrado): LOW = normal, HIGH = defrost
// - NA (Normalmente Abierto): HIGH = normal, LOW = defrost
extern SystemState state;
extern void supabaseSendDefrostStart(float tempAtStart, const char* triggeredBy);

void checkDefrostSignal() {
  static bool lastDefrostSignal = false;
  static unsigned long defrostDebounce = 0;
  
  // Leer el pin con debounce
  bool pinState = digitalRead(PIN_DEFROST_INPUT);
  
  // Determinar si está en defrost según la configuración NC/NA
  // NC (defrostRelayNC = true): LOW = normal, HIGH = defrost
  // NA (defrostRelayNC = false): HIGH = normal, LOW = defrost
  bool defrostDetected;
  if (config.defrostRelayNC) {
    // Normalmente Cerrado: HIGH significa que se abrió = defrost
    defrostDetected = (pinState == HIGH);
  } else {
    // Normalmente Abierto: LOW significa que se cerró = defrost
    defrostDetected = (pinState == LOW);
  }
  
  // Debounce de 2 segundos para evitar falsos positivos
  if (defrostDetected != lastDefrostSignal) {
    if (defrostDebounce == 0) {
      defrostDebounce = millis();
    } else if (millis() - defrostDebounce > 2000) {
      // Cambio confirmado después de 2 segundos
      lastDefrostSignal = defrostDetected;
      defrostDebounce = 0;
      
      if (defrostDetected && !state.defrostMode) {
        // Entró en descongelamiento
        state.defrostMode = true;
        state.defrostStartTime = millis();
        state.alertActive = false;  // Suspender alertas durante defrost
        state.criticalAlert = false;
        state.alertAcknowledged = false;
        Serial.println("[DEFROST] ⚡ DETECTADO AUTOMÁTICAMENTE - Señal del relé");
        
        // Registrar en Supabase
        supabaseSendDefrostStart(sensorData.tempAvg, "relay_signal");
        
      } else if (!defrostDetected && state.defrostMode) {
        // Salió del descongelamiento - INICIAR COOLDOWN
        unsigned long defrostMin = (millis() - state.defrostStartTime) / 60000;
        state.defrostMode = false;
        state.defrostStartTime = 0;
        
        // Iniciar período de cooldown (espera post-descongelamiento)
        state.cooldownMode = true;
        state.cooldownStartTime = millis();
        state.cooldownRemainingSec = config.defrostCooldownSec;
        
        Serial.printf("[DEFROST] ✓ FINALIZADO - Duró %lu minutos\n", defrostMin);
        Serial.printf("[COOLDOWN] ⏳ Iniciando espera de %d minutos\n", config.defrostCooldownSec / 60);
      }
    }
  } else {
    defrostDebounce = 0;
  }
}

#endif
