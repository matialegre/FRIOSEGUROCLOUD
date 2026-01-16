/*
 * types.h - Estructuras de datos del sistema
 * Sistema Monitoreo Reefer v3.0
 */

#ifndef TYPES_H
#define TYPES_H

// ============================================
// ESTRUCTURAS DE DATOS
// ============================================

struct Config {
  float tempMax;
  float tempCritical;
  int alertDelaySec;
  int doorOpenMaxSec;
  int defrostCooldownSec;
  bool defrostRelayNC;
  bool relayEnabled;
  bool buzzerEnabled;
  bool telegramEnabled;
  bool supabaseEnabled;
  bool sensor1Enabled;
  bool sensor2Enabled;
  bool dht22Enabled;
  bool doorEnabled;
  bool simulationMode;
  float simTemp1;
  float simTemp2;
  bool simDoorOpen;
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
  bool alertAcknowledged;
  String alertMessage;
  unsigned long alertStartTime;
  unsigned long highTempStartTime;
  int highTempElapsedSec;          // Segundos que lleva la temp por encima del crítico
  bool tempOverCritical;           // Si la temp está actualmente sobre el crítico
  bool relayState;
  bool internetAvailable;
  bool wifiConnected;
  bool apMode;
  unsigned long lastInternetCheck;
  unsigned long lastSensorRead;
  unsigned long lastTelegramAlert;
  unsigned long lastSupabaseSync;
  unsigned long uptime;
  int totalAlerts;
  String localIP;
  bool defrostMode;
  unsigned long defrostStartTime;
  bool cooldownMode;              // En período de espera post-descongelamiento
  unsigned long cooldownStartTime; // Cuándo empezó el cooldown
  int cooldownRemainingSec;        // Segundos restantes de cooldown
};

struct HistoryPoint {
  float temp;
  unsigned long timestamp;
};

#endif
