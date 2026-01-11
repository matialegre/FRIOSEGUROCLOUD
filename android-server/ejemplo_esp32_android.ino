/*
 * Ejemplo de código ESP32 para enviar datos a Android Server
 * 
 * Este código muestra cómo modificar tu ESP32 para que envíe datos
 * a un celular Android que esté corriendo el servidor HTTP
 */

#include <WiFi.h>
#include <HTTPClient.h>

// ============================================
// CONFIGURACIÓN - MODIFICAR ESTOS VALORES
// ============================================
const char* WIFI_SSID = "TU_WIFI";
const char* WIFI_PASSWORD = "TU_PASSWORD";

// IP DEL CELULAR ANDROID (obtenela desde la app en el celular)
const char* ANDROID_SERVER_IP = "192.168.1.50";  // ← CAMBIAR ESTO
const int ANDROID_SERVER_PORT = 8080;

// Identificación de este Reefer
const char* REEFER_ID = "REEFER-01";
const char* REEFER_NAME = "Reefer Principal";

// ============================================
// INTERVALO DE ENVÍO
// ============================================
const unsigned long SEND_INTERVAL = 30000; // 30 segundos
unsigned long lastSendTime = 0;

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("========================================");
  Serial.println("  ESP32 → Android Server");
  Serial.println("========================================");
  Serial.println();
  
  // Conectar WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a WiFi");
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.println("✅ WiFi conectado!");
    Serial.print("📍 IP ESP32: ");
    Serial.println(WiFi.localIP());
    Serial.print("📡 Enviando datos a Android: ");
    Serial.print(ANDROID_SERVER_IP);
    Serial.print(":");
    Serial.println(ANDROID_SERVER_PORT);
    Serial.println();
  } else {
    Serial.println();
    Serial.println("❌ Error conectando WiFi");
  }
}

void loop() {
  // Enviar datos periódicamente
  if (millis() - lastSendTime >= SEND_INTERVAL) {
    if (WiFi.status() == WL_CONNECTED) {
      sendDataToAndroid();
      lastSendTime = millis();
    } else {
      Serial.println("⚠️ Sin WiFi, reconectando...");
      WiFi.reconnect();
    }
  }
  
  delay(1000);
}

void sendDataToAndroid() {
  HTTPClient http;
  
  // URL del servidor Android
  String url = "http://" + String(ANDROID_SERVER_IP) + ":" + String(ANDROID_SERVER_PORT) + "/api/data";
  
  Serial.println("📤 Enviando datos a Android...");
  Serial.print("   URL: ");
  Serial.println(url);
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  
  // Crear JSON con los datos
  // NOTA: En tu código real, lee los sensores aquí
  String json = "{";
  json += "\"reefer_id\":\"" + String(REEFER_ID) + "\",";
  json += "\"name\":\"" + String(REEFER_NAME) + "\",";
  json += "\"temp\":" + String(-22.5) + ",";  // ← Leer sensor real
  json += "\"door_open\":false";              // ← Leer sensor real
  json += "}";
  
  Serial.print("   JSON: ");
  Serial.println(json);
  
  int httpCode = http.POST(json);
  
  if (httpCode > 0) {
    if (httpCode == HTTP_CODE_OK) {
      String response = http.getString();
      Serial.println("   ✅ Datos enviados correctamente");
      Serial.print("   📥 Respuesta: ");
      Serial.println(response);
    } else {
      Serial.print("   ⚠️ Código HTTP: ");
      Serial.println(httpCode);
    }
  } else {
    Serial.print("   ❌ Error: ");
    Serial.println(http.errorToString(httpCode));
    Serial.println("   💡 Verificá que:");
    Serial.println("      - El celular Android esté en la misma red WiFi");
    Serial.println("      - El servidor en Android esté activo");
    Serial.println("      - La IP del celular sea correcta");
  }
  
  http.end();
  Serial.println();
}

/*
 * INSTRUCCIONES:
 * 
 * 1. Cambiá ANDROID_SERVER_IP con la IP del celular
 *    (la mostrás la app cuando iniciás el servidor)
 * 
 * 2. Compilá y subí este código al ESP32
 * 
 * 3. El ESP32 enviará datos cada 30 segundos al celular Android
 * 
 * 4. Podés ver los datos en el navegador:
 *    http://[IP_CELULAR]:8080
 * 
 * 5. Para integrar con tu código actual, simplemente:
 *    - Reemplazá la función sendDataToAndroid()
 *    - Usá tus variables reales de sensores
 */
