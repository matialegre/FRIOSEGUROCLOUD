/*
 * RECEPTOR - Sistema de Monitoreo Campamento Parametican Silver
 * Hardware: ESP32 DevKit
 * Función: Web Server local + Base de datos + Alertas
 */

#include <WiFi.h>
#include <WebServer.h>
#include <SPIFFS.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <time.h>
#include <Preferences.h>

// CONFIGURACIÓN
const char* WIFI_SSID = "PARAMETICAN_WIFI";
const char* WIFI_PASSWORD = "password123";

IPAddress local_IP(192, 168, 1, 100);
IPAddress gateway(192, 168, 1, 1);
IPAddress subnet(255, 255, 255, 0);

const char* TELEGRAM_BOT_TOKEN = "TU_BOT_TOKEN_AQUI";
const char* TELEGRAM_CHAT_ID = "TU_CHAT_ID_AQUI";
const char* SUPABASE_URL = "https://tu-proyecto.supabase.co";
const char* SUPABASE_KEY = "tu-anon-key-aqui";
const char* NTP_SERVER = "pool.ntp.org";
const long GMT_OFFSET = -3 * 3600;

// PINES PARA ALERTA LOCAL (sin internet)
#define BUZZER_PIN 25      // GPIO25 - Buzzer pequeño (transistor 2N2222)
#define LED_ALERT_PIN 26   // GPIO26 - LED rojo de alerta
#define LED_OK_PIN 27      // GPIO27 - LED verde (todo OK)
#define SIREN_PIN 32       // GPIO32 - SIRENA POTENTE 12V (relay)

// UMBRALES
struct AlertThresholds {
  float tempMax = -18.0;
  float tempCritical = -10.0;
  int minDurationSeconds = 300;
  int doorOpenMaxSeconds = 180;
};
AlertThresholds thresholds;

// ESTRUCTURAS
#define MAX_RIFTS 6
#define MAX_HISTORY 1440

struct RiftData {
  int id;
  String name;
  String location;
  float temp1, temp2, tempAvg;
  bool doorOpen;
  unsigned long doorOpenSince;
  int sensorCount, rssi;
  unsigned long lastUpdate;
  bool online, alertActive;
  String alertMessage;
  unsigned long alertStartTime;
};

struct HistoryPoint {
  unsigned long timestamp;
  float temperature;
  bool doorOpen;
};

RiftData rifts[MAX_RIFTS];
HistoryPoint history[MAX_RIFTS][MAX_HISTORY];
int historyIndex[MAX_RIFTS] = {0};

WebServer server(80);
Preferences preferences;

bool internetAvailable = false;
unsigned long lastInternetCheck = 0;
unsigned long lastSupabaseSync = 0;
unsigned long systemStartTime = 0;
unsigned long totalDataReceived = 0;
unsigned long totalAlertsSent = 0;

// Variables para alerta local
bool localAlertActive = false;
bool criticalAlertActive = false;  // Para sirena potente
unsigned long lastBuzzerToggle = 0;
bool buzzerState = false;
bool sirenAcknowledged = false;     // Si alguien silenció la sirena
unsigned long sirenAckTime = 0;     // Cuándo se silenció

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=== RECEPTOR - Campamento Parametican Silver ===");
  
  // Configurar pines de alerta local
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(LED_ALERT_PIN, OUTPUT);
  pinMode(LED_OK_PIN, OUTPUT);
  pinMode(SIREN_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);
  digitalWrite(LED_ALERT_PIN, LOW);
  digitalWrite(LED_OK_PIN, HIGH); // LED verde encendido = todo OK
  digitalWrite(SIREN_PIN, LOW);   // Sirena apagada
  
  if (!SPIFFS.begin(true)) {
    Serial.println("[ERROR] SPIFFS");
  }
  
  loadConfiguration();
  initRifts();
  connectWiFi();
  configTime(GMT_OFFSET, 0, NTP_SERVER);
  setupWebServer();
  server.begin();
  
  systemStartTime = millis();
  Serial.println("Sistema listo: http://" + WiFi.localIP().toString());
}

void loop() {
  server.handleClient();
  
  if (WiFi.status() != WL_CONNECTED) reconnectWiFi();
  
  if (millis() - lastInternetCheck > 30000) {
    checkInternetConnection();
    lastInternetCheck = millis();
  }
  
  if (internetAvailable && (millis() - lastSupabaseSync > 300000)) {
    syncToSupabase();
    lastSupabaseSync = millis();
  }
  
  checkAlerts();
  checkRiftStatus();
  updateLocalAlerts(); // Manejar buzzer y LEDs
  delay(10);
}

void initRifts() {
  const char* names[] = {"RIFT-01","RIFT-02","RIFT-03","RIFT-04","RIFT-05","RIFT-06"};
  const char* locs[] = {"Deposito Principal","Deposito Carnes","Deposito Lacteos",
                        "Deposito Verduras","Deposito Bebidas","Deposito Reserva"};
  
  for (int i = 0; i < MAX_RIFTS; i++) {
    rifts[i].id = i + 1;
    rifts[i].name = names[i];
    rifts[i].location = locs[i];
    rifts[i].online = false;
    rifts[i].temp1 = rifts[i].temp2 = rifts[i].tempAvg = -999.0;
    rifts[i].alertActive = false;
  }
}

void loadConfiguration() {
  preferences.begin("parametican", false);
  thresholds.tempMax = preferences.getFloat("tempMax", -18.0);
  thresholds.tempCritical = preferences.getFloat("tempCrit", -10.0);
  thresholds.minDurationSeconds = preferences.getInt("minDur", 300);
  thresholds.doorOpenMaxSeconds = preferences.getInt("doorMax", 180);
  preferences.end();
}

void saveConfiguration() {
  preferences.begin("parametican", false);
  preferences.putFloat("tempMax", thresholds.tempMax);
  preferences.putFloat("tempCrit", thresholds.tempCritical);
  preferences.putInt("minDur", thresholds.minDurationSeconds);
  preferences.putInt("doorMax", thresholds.doorOpenMaxSeconds);
  preferences.end();
}

void connectWiFi() {
  WiFi.config(local_IP, gateway, subnet);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("WiFi OK - IP: " + WiFi.localIP().toString());
  }
}

void reconnectWiFi() {
  static unsigned long lastAttempt = 0;
  if (millis() - lastAttempt > 10000) {
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    lastAttempt = millis();
  }
}

void checkInternetConnection() {
  HTTPClient http;
  http.begin("http://www.google.com/generate_204");
  http.setTimeout(3000);
  internetAvailable = (http.GET() == 204);
  http.end();
}

void setupWebServer() {
  server.on("/", HTTP_GET, handleRoot);
  server.on("/app", HTTP_GET, handleMobileApp);  // App para celulares
  server.on("/api/status", HTTP_GET, handleGetStatus);
  server.on("/api/data", HTTP_POST, handlePostData);
  server.on("/api/history", HTTP_GET, handleGetHistory);
  server.on("/api/alerts", HTTP_GET, handleGetAlerts);
  server.on("/api/config", HTTP_GET, handleGetConfig);
  server.on("/api/config", HTTP_POST, handlePostConfig);
  server.on("/api/test-alert", HTTP_POST, handleTestAlert);
  server.on("/api/ack-alert", HTTP_POST, handleAckAlert); // Silenciar buzzer
  server.onNotFound([]() { server.send(404, "text/plain", "Not found"); });
}

void handleRoot() {
  File file = SPIFFS.open("/index.html", "r");
  if (file) {
    server.streamFile(file, "text/html");
    file.close();
  } else {
    server.send(200, "text/html", "<h1>Campamento Parametican Silver</h1><p>Subir archivos SPIFFS</p>");
  }
}

void handleGetStatus() {
  StaticJsonDocument<2048> doc;
  JsonArray arr = doc.createNestedArray("rifts");
  
  for (int i = 0; i < MAX_RIFTS; i++) {
    JsonObject r = arr.createNestedObject();
    r["id"] = rifts[i].id;
    r["name"] = rifts[i].name;
    r["location"] = rifts[i].location;
    r["temp1"] = rifts[i].temp1;
    r["temp2"] = rifts[i].temp2;
    r["temp_avg"] = rifts[i].tempAvg;
    r["door_open"] = rifts[i].doorOpen;
    r["door_open_since"] = rifts[i].doorOpenSince;
    r["online"] = rifts[i].online;
    r["last_update"] = rifts[i].lastUpdate;
    r["alert_active"] = rifts[i].alertActive;
    r["alert_message"] = rifts[i].alertMessage;
    r["rssi"] = rifts[i].rssi;
  }
  
  doc["internet"] = internetAvailable;
  doc["uptime"] = (millis() - systemStartTime) / 1000;
  doc["total_data"] = totalDataReceived;
  
  struct tm timeinfo;
  if (getLocalTime(&timeinfo)) {
    char timeStr[64];
    strftime(timeStr, sizeof(timeStr), "%Y-%m-%d %H:%M:%S", &timeinfo);
    doc["current_time"] = timeStr;
  }
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handlePostData() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No data\"}");
    return;
  }
  
  StaticJsonDocument<512> doc;
  deserializeJson(doc, server.arg("plain"));
  
  int riftId = doc["rift_id"] | 0;
  if (riftId < 1 || riftId > MAX_RIFTS) {
    server.send(400, "application/json", "{\"error\":\"Invalid rift_id\"}");
    return;
  }
  
  int idx = riftId - 1;
  rifts[idx].temp1 = doc["temp1"] | -999.0;
  rifts[idx].temp2 = doc["temp2"] | -999.0;
  rifts[idx].tempAvg = doc["temp_avg"] | -999.0;
  rifts[idx].doorOpen = doc["door_open"] | false;
  rifts[idx].doorOpenSince = doc["door_open_since"] | 0;
  rifts[idx].sensorCount = doc["sensor_count"] | 0;
  rifts[idx].rssi = doc["rssi"] | 0;
  rifts[idx].lastUpdate = millis();
  rifts[idx].online = true;
  
  addToHistory(idx, rifts[idx].tempAvg, rifts[idx].doorOpen);
  totalDataReceived++;
  
  Serial.println("[DATA] " + rifts[idx].name + ": " + String(rifts[idx].tempAvg, 1) + "C");
  server.send(200, "application/json", "{\"status\":\"ok\"}");
}

void handleGetHistory() {
  int riftId = server.arg("rift").toInt();
  if (riftId < 1 || riftId > MAX_RIFTS) riftId = 1;
  int idx = riftId - 1;
  
  StaticJsonDocument<4096> doc;
  doc["rift_id"] = riftId;
  JsonArray data = doc.createNestedArray("data");
  
  for (int i = 0; i < 100; i++) {
    int hIdx = (historyIndex[idx] - i + MAX_HISTORY) % MAX_HISTORY;
    if (history[idx][hIdx].timestamp > 0) {
      JsonObject p = data.createNestedObject();
      p["t"] = history[idx][hIdx].timestamp;
      p["temp"] = history[idx][hIdx].temperature;
      p["door"] = history[idx][hIdx].doorOpen;
    }
  }
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleGetAlerts() {
  StaticJsonDocument<1024> doc;
  JsonArray alerts = doc.createNestedArray("alerts");
  
  for (int i = 0; i < MAX_RIFTS; i++) {
    if (rifts[i].alertActive) {
      JsonObject a = alerts.createNestedObject();
      a["rift_id"] = rifts[i].id;
      a["rift_name"] = rifts[i].name;
      a["message"] = rifts[i].alertMessage;
    }
  }
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleGetConfig() {
  StaticJsonDocument<256> doc;
  doc["temp_max"] = thresholds.tempMax;
  doc["temp_critical"] = thresholds.tempCritical;
  doc["min_duration"] = thresholds.minDurationSeconds;
  doc["door_max"] = thresholds.doorOpenMaxSeconds;
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handlePostConfig() {
  StaticJsonDocument<256> doc;
  deserializeJson(doc, server.arg("plain"));
  
  if (doc.containsKey("temp_max")) thresholds.tempMax = doc["temp_max"];
  if (doc.containsKey("temp_critical")) thresholds.tempCritical = doc["temp_critical"];
  if (doc.containsKey("min_duration")) thresholds.minDurationSeconds = doc["min_duration"];
  if (doc.containsKey("door_max")) thresholds.doorOpenMaxSeconds = doc["door_max"];
  
  saveConfiguration();
  server.send(200, "application/json", "{\"status\":\"ok\"}");
}

void handleTestAlert() {
  sendTelegramAlert("Test - Sistema funcionando");
  server.send(200, "application/json", "{\"status\":\"ok\"}");
}

void addToHistory(int idx, float temp, bool door) {
  history[idx][historyIndex[idx]].timestamp = millis();
  history[idx][historyIndex[idx]].temperature = temp;
  history[idx][historyIndex[idx]].doorOpen = door;
  historyIndex[idx] = (historyIndex[idx] + 1) % MAX_HISTORY;
}

void checkAlerts() {
  static unsigned long highTempStart[MAX_RIFTS] = {0};
  
  for (int i = 0; i < MAX_RIFTS; i++) {
    if (!rifts[i].online || rifts[i].tempAvg == -999.0) continue;
    
    if (rifts[i].tempAvg > thresholds.tempCritical) {
      if (!rifts[i].alertActive) {
        triggerAlert(i, "CRITICO: Temp " + String(rifts[i].tempAvg, 1) + "C");
      }
    } else if (rifts[i].tempAvg > thresholds.tempMax) {
      if (!rifts[i].doorOpen) {
        if (highTempStart[i] == 0) highTempStart[i] = millis();
        else if ((millis() - highTempStart[i]) / 1000 > thresholds.minDurationSeconds) {
          if (!rifts[i].alertActive) {
            triggerAlert(i, "Temp alta: " + String(rifts[i].tempAvg, 1) + "C");
          }
        }
      }
    } else {
      highTempStart[i] = 0;
      if (rifts[i].alertActive) clearAlert(i);
    }
  }
}

void triggerAlert(int idx, String msg) {
  rifts[idx].alertActive = true;
  rifts[idx].alertMessage = msg;
  rifts[idx].alertStartTime = millis();
  
  String full = "ALERTA " + rifts[idx].name + "\n" + msg;
  sendTelegramAlert(full);
  totalAlertsSent++;
}

void clearAlert(int idx) {
  rifts[idx].alertActive = false;
  rifts[idx].alertMessage = "";
}

void sendTelegramAlert(String msg) {
  if (!internetAvailable || String(TELEGRAM_BOT_TOKEN) == "TU_BOT_TOKEN_AQUI") return;
  
  HTTPClient http;
  String url = "https://api.telegram.org/bot" + String(TELEGRAM_BOT_TOKEN) + "/sendMessage";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  
  StaticJsonDocument<512> doc;
  doc["chat_id"] = TELEGRAM_CHAT_ID;
  doc["text"] = msg;
  
  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
}

void syncToSupabase() {
  if (String(SUPABASE_URL) == "https://tu-proyecto.supabase.co") return;
  
  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/temperature_logs";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("apikey", SUPABASE_KEY);
  http.addHeader("Authorization", "Bearer " + String(SUPABASE_KEY));
  
  StaticJsonDocument<512> doc;
  JsonArray arr = doc.to<JsonArray>();
  
  for (int i = 0; i < MAX_RIFTS; i++) {
    if (rifts[i].online && rifts[i].tempAvg != -999.0) {
      JsonObject r = arr.createNestedObject();
      r["rift_id"] = rifts[i].id;
      r["temperature"] = rifts[i].tempAvg;
      r["door_open"] = rifts[i].doorOpen;
    }
  }
  
  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
}

void checkRiftStatus() {
  for (int i = 0; i < MAX_RIFTS; i++) {
    if (rifts[i].online && (millis() - rifts[i].lastUpdate > 120000)) {
      rifts[i].online = false;
    }
  }
  
  // Actualizar estado de alerta local
  localAlertActive = false;
  criticalAlertActive = false;
  for (int i = 0; i < MAX_RIFTS; i++) {
    if (rifts[i].alertActive) {
      localAlertActive = true;
      // Si la temperatura es crítica (>-10°C), activar sirena
      if (rifts[i].tempAvg > thresholds.tempCritical) {
        criticalAlertActive = true;
      }
    }
  }
  
  // Resetear acknowledge de sirena si pasaron 30 minutos
  if (sirenAcknowledged && (millis() - sirenAckTime > 1800000)) {
    sirenAcknowledged = false;
  }
}

// Manejar buzzer, LEDs y SIRENA de alerta local
void updateLocalAlerts() {
  if (localAlertActive) {
    // LED rojo encendido, verde apagado
    digitalWrite(LED_OK_PIN, LOW);
    digitalWrite(LED_ALERT_PIN, HIGH);
    
    // Buzzer intermitente (beep cada 2 segundos)
    if (millis() - lastBuzzerToggle > 2000) {
      buzzerState = !buzzerState;
      digitalWrite(BUZZER_PIN, buzzerState ? HIGH : LOW);
      lastBuzzerToggle = millis();
      
      // Apagar después de 200ms (beep corto)
      if (buzzerState) {
        delay(200);
        digitalWrite(BUZZER_PIN, LOW);
      }
    }
    
    // SIRENA POTENTE para alertas críticas (si no fue silenciada)
    if (criticalAlertActive && !sirenAcknowledged) {
      // Sirena intermitente: 3 segundos ON, 2 segundos OFF
      unsigned long sirenCycle = (millis() / 1000) % 5;
      digitalWrite(SIREN_PIN, sirenCycle < 3 ? HIGH : LOW);
    } else {
      digitalWrite(SIREN_PIN, LOW);
    }
  } else {
    // Todo OK: LED verde, sin buzzer ni sirena
    digitalWrite(LED_OK_PIN, HIGH);
    digitalWrite(LED_ALERT_PIN, LOW);
    digitalWrite(BUZZER_PIN, LOW);
    digitalWrite(SIREN_PIN, LOW);
    buzzerState = false;
    sirenAcknowledged = false; // Resetear para próxima alerta
  }
}

// Silenciar sirena y buzzer (pero mantener alerta visual)
void handleAckAlert() {
  sirenAcknowledged = true;
  sirenAckTime = millis();
  digitalWrite(BUZZER_PIN, LOW);
  digitalWrite(SIREN_PIN, LOW);
  Serial.println("[ALERT] Sirena silenciada por 30 minutos");
  server.send(200, "application/json", "{\"status\":\"silenced\",\"duration_min\":30}");
}

// App móvil para alertas locales
void handleMobileApp() {
  String html = getMobileAppHTML();
  server.send(200, "text/html", html);
}

String getMobileAppHTML() {
  return R"rawliteral(
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="theme-color" content="#1a1a2e">
    <title>ALERTA RIFT</title>
    <style>
        *{margin:0;padding:0;box-sizing:border-box}
        html,body{height:100%;overflow:hidden}
        body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#1a1a2e;color:#fff}
        
        #initScreen{position:fixed;inset:0;background:#1a1a2e;display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:1000}
        #initScreen h1{font-size:1.5em;margin-bottom:20px}
        #initScreen p{color:#888;margin-bottom:30px;text-align:center;padding:0 20px}
        #startBtn{background:linear-gradient(135deg,#00ff88,#00aa55);border:none;padding:20px 60px;border-radius:15px;color:#000;font-size:1.3em;font-weight:bold;cursor:pointer}
        
        #app{display:none;height:100%;flex-direction:column}
        #app.active{display:flex}
        
        .header{background:linear-gradient(90deg,#0f3460,#533483);padding:12px;text-align:center}
        .header h1{font-size:1.1em}
        
        .status{display:flex;justify-content:space-between;padding:8px 15px;background:rgba(0,0,0,0.4);font-size:0.8em}
        .dot{width:10px;height:10px;border-radius:50%;display:inline-block;margin-right:5px}
        .dot.ok{background:#0f0}
        .dot.err{background:#f00;animation:blink .3s infinite}
        @keyframes blink{50%{opacity:.2}}
        
        #alertFull{display:none;position:fixed;inset:0;background:#ff0000;z-index:100;flex-direction:column;align-items:center;justify-content:center;animation:flash .5s infinite}
        #alertFull.active{display:flex}
        @keyframes flash{0%,100%{background:#ff0000}50%{background:#cc0000}}
        #alertFull h1{font-size:3em;margin-bottom:20px}
        #alertFull .msg{font-size:1.5em;text-align:center;padding:20px}
        #alertFull button{margin-top:30px;padding:20px 50px;font-size:1.2em;background:#fff;color:#000;border:none;border-radius:10px;font-weight:bold}
        
        .main{flex:1;overflow-y:auto;padding:10px}
        .card{background:rgba(255,255,255,0.08);border-radius:10px;padding:12px;margin-bottom:10px;border:1px solid rgba(255,255,255,0.1)}
        .card.alert{border-color:#f00;background:rgba(255,0,0,0.15)}
        .card-head{display:flex;justify-content:space-between;font-size:0.9em;margin-bottom:8px}
        .badge{padding:2px 8px;border-radius:8px;font-size:0.7em;font-weight:bold}
        .badge.on{background:#0f0;color:#000}
        .badge.off{background:#666}
        .temp{font-size:2.8em;text-align:center;font-weight:bold;padding:10px 0}
        .temp.ok{color:#0f0}
        .temp.warn{color:#ff9800}
        .temp.crit{color:#f00}
        .info{display:flex;justify-content:space-between;font-size:0.8em;color:#888}
        .door{text-align:center;padding:6px;border-radius:5px;margin-top:8px;font-size:0.85em}
        .door.c{background:rgba(0,255,0,0.1);color:#0f0}
        .door.o{background:rgba(255,150,0,0.2);color:#ff9800}
        
        .foot{padding:8px;text-align:center;font-size:0.75em;color:#666;background:rgba(0,0,0,0.3)}
        
        #audioStatus{position:fixed;bottom:60px;left:50%;transform:translateX(-50%);background:rgba(0,0,0,0.8);padding:8px 20px;border-radius:20px;font-size:0.8em;display:none}
    </style>
</head>
<body>
    <div id="initScreen">
        <h1>🏔️ PARAMETICAN SILVER</h1>
        <p>Esta app monitorea los RIFTs y te alerta con sonido y vibracion.<br><br>
        <strong>IMPORTANTE:</strong> Debes tocar el boton para activar el audio (requisito del navegador).</p>
        <button id="startBtn" onclick="initApp()">🔔 ACTIVAR ALERTAS</button>
    </div>
    
    <div id="app">
        <div class="header"><h1>🏔️ MONITOR RIFT</h1></div>
        <div class="status">
            <span><span class="dot" id="dot"></span><span id="connTxt">...</span></span>
            <span id="time">--:--</span>
        </div>
        <div class="main" id="cards"></div>
        <div class="foot">Polling 5s | <span id="lastUp">--</span></div>
    </div>
    
    <div id="alertFull">
        <h1>⚠️ ALERTA</h1>
        <div class="msg" id="alertMsg"></div>
        <button onclick="ackAlert()">🔇 SILENCIAR</button>
    </div>
    
    <div id="audioStatus">🔊 Audio activo</div>
    
    <script>
    let audioCtx, oscillator, gainNode;
    let alertActive = false;
    let silenced = false;
    let wakeLock = null;
    
    function initApp() {
        document.getElementById('initScreen').style.display = 'none';
        document.getElementById('app').classList.add('active');
        
        // Crear contexto de audio (DEBE ser en respuesta a click del usuario)
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        
        // Mostrar confirmacion
        const status = document.getElementById('audioStatus');
        status.style.display = 'block';
        setTimeout(() => status.style.display = 'none', 2000);
        
        // Mantener pantalla encendida
        requestWakeLock();
        
        // Iniciar polling
        fetchData();
        setInterval(fetchData, 5000);
        
        // Vibrar para confirmar
        if(navigator.vibrate) navigator.vibrate(200);
    }
    
    async function requestWakeLock() {
        try {
            if('wakeLock' in navigator) {
                wakeLock = await navigator.wakeLock.request('screen');
            }
        } catch(e) {}
    }
    
    async function fetchData() {
        try {
            const r = await fetch('/api/status');
            const d = await r.json();
            
            document.getElementById('dot').className = 'dot ok';
            document.getElementById('connTxt').textContent = 'OK';
            document.getElementById('lastUp').textContent = new Date().toLocaleTimeString();
            if(d.current_time) document.getElementById('time').textContent = d.current_time.split(' ')[1];
            
            renderCards(d.rifts);
            checkAlerts(d.rifts);
        } catch(e) {
            document.getElementById('dot').className = 'dot err';
            document.getElementById('connTxt').textContent = 'SIN CONEXION';
        }
    }
    
    function renderCards(rifts) {
        const c = document.getElementById('cards');
        c.innerHTML = rifts.filter(r=>r.online).map(r => {
            const tc = r.temp_avg > -10 ? 'crit' : r.temp_avg > -18 ? 'warn' : 'ok';
            const cc = r.alert_active ? 'card alert' : 'card';
            return `<div class="${cc}">
                <div class="card-head"><span>${r.name}</span><span class="badge on">ON</span></div>
                <div class="temp ${tc}">${r.temp_avg.toFixed(1)}°</div>
                <div class="info"><span>${r.location}</span><span>${r.rssi}dBm</span></div>
                <div class="door ${r.door_open?'o':'c'}">${r.door_open?'🚪 ABIERTA':'🔒 Cerrada'}</div>
            </div>`;
        }).join('') || '<div style="text-align:center;color:#666;padding:40px">Sin RIFTs</div>';
    }
    
    function checkAlerts(rifts) {
        const hasAlert = rifts.some(r => r.alert_active);
        const alertDiv = document.getElementById('alertFull');
        
        if(hasAlert && !silenced) {
            if(!alertActive) {
                alertActive = true;
                alertDiv.classList.add('active');
                
                const msgs = rifts.filter(r=>r.alert_active).map(r=>`${r.name}: ${r.alert_message}`);
                document.getElementById('alertMsg').innerHTML = msgs.join('<br>');
                
                startAlarm();
                vibrateLoop();
            }
        } else if(!hasAlert) {
            alertActive = false;
            silenced = false;
            alertDiv.classList.remove('active');
            stopAlarm();
        }
    }
    
    function startAlarm() {
        if(!audioCtx) return;
        try {
            // Crear oscilador para sirena
            oscillator = audioCtx.createOscillator();
            gainNode = audioCtx.createGain();
            
            oscillator.connect(gainNode);
            gainNode.connect(audioCtx.destination);
            
            oscillator.type = 'square';
            oscillator.frequency.value = 800;
            gainNode.gain.value = 0.3;
            
            oscillator.start();
            
            // Modular frecuencia para efecto sirena
            sirenLoop();
        } catch(e) {}
    }
    
    function sirenLoop() {
        if(!oscillator || !alertActive || silenced) return;
        
        // Alternar entre 800Hz y 600Hz
        const freq = oscillator.frequency.value === 800 ? 600 : 800;
        oscillator.frequency.setValueAtTime(freq, audioCtx.currentTime);
        
        setTimeout(sirenLoop, 500);
    }
    
    function stopAlarm() {
        try {
            if(oscillator) {
                oscillator.stop();
                oscillator.disconnect();
                oscillator = null;
            }
        } catch(e) {}
    }
    
    function vibrateLoop() {
        if(!alertActive || silenced) return;
        if(navigator.vibrate) {
            navigator.vibrate([300,100,300,100,300]);
        }
        setTimeout(vibrateLoop, 2000);
    }
    
    async function ackAlert() {
        silenced = true;
        stopAlarm();
        document.getElementById('alertFull').classList.remove('active');
        
        try {
            await fetch('/api/ack-alert', {method:'POST'});
        } catch(e) {}
        
        if(navigator.vibrate) navigator.vibrate(100);
    }
    
    // Reconectar wakeLock si se pierde
    document.addEventListener('visibilitychange', () => {
        if(document.visibilityState === 'visible' && !wakeLock) {
            requestWakeLock();
        }
    });
    </script>
</body>
</html>
)rawliteral";
}
