# 📖 MANUAL - Sistema de Monitoreo Reefer
## Campamento Parametican Silver

---

## 🚀 INICIO RÁPIDO

### 1. Encender el ESP32
- Conectar el ESP32 a la alimentación
- Esperar ~30 segundos hasta que conecte al WiFi
- El relé hará un pulso de 1 segundo al conectar (confirmación)

### 2. Abrir la App
- Instalar `VersionLocal2.apk` en el celular
- Abrir **"Alerta REEFER"**
- Ingresar la IP del dispositivo: `reefer.local` o `192.168.0.47`
- Presionar **INICIAR MONITOREO**

---

## 📱 PANTALLA PRINCIPAL

| Elemento | Descripción |
|----------|-------------|
| **Temperatura Grande** | Temperatura actual del reefer en tiempo real |
| **Sensor DS18B20** | Lectura directa del sensor (debe coincidir) |
| **Puerta** | Siempre "Inhabilitado" (sensor no conectado) |
| **Sirena** | "Apagada" normal / "PRENDIDA" cuando hay alarma |
| **Lista de Reefers** | Verde = Online, Rojo = Offline |

---

## ⚙️ CONFIGURACIÓN

### Acceder a Configuración
1. Tocar el botón **⚙️** (esquina superior derecha)
2. Seleccionar **"Configuración"**

### Temperatura Crítica
- **Único parámetro configurable**
- Si la temperatura supera este valor → **SE ACTIVA LA ALARMA**
- Valor por defecto: **-10°C**
- Ejemplo: Si pones -15°C, la alarma salta cuando supere -15°C

---

## 🚨 CUANDO SALTA LA ALARMA

### ¿Qué pasa?
1. La app muestra banner rojo con mensaje de alerta
2. Suena alarma en el celular
3. El relé se activa (sirena/luz externa)
4. Se envía notificación a Telegram (si hay internet)

### ¿Cómo silenciar?
1. Tocar **"SILENCIAR ALARMA"** en la app
2. La alarma se detiene en el celular Y en el ESP32
3. La sirena externa se apaga

### ¿Cuándo se reactiva?
- Si la temperatura sigue alta, la alarma NO vuelve a sonar
- Solo vuelve a alertar si la temperatura baja y vuelve a subir

---

## 🔌 CONEXIONES DEL ESP32

| PIN | Función |
|-----|---------|
| GPIO 4 | Sensor DS18B20 (temperatura) |
| GPIO 26 | Relé (sirena/luz) |
| GPIO 2 | LED integrado |

### Sensor DS18B20
- Cable ROJO → 3.3V
- Cable NEGRO → GND
- Cable AMARILLO → GPIO 4
- Resistencia 4.7kΩ entre ROJO y AMARILLO

---

## 🔧 SOLUCIÓN DE PROBLEMAS

### La app muestra "--.-°C"
- El ESP32 no está enviando datos
- Verificar que el ESP32 esté encendido y conectado al WiFi
- Verificar la IP en la app

### Temperatura muestra -127°C
- El sensor DS18B20 no está conectado correctamente
- Verificar cables y resistencia 4.7kΩ

### No conecta al WiFi
- Mantener presionado el botón BOOT del ESP32 por 5 segundos
- Se creará un AP llamado "Reefer-Setup"
- Conectarse a ese AP y configurar el WiFi

### La alarma no suena
- Verificar que la temperatura supere el límite crítico
- Verificar volumen del celular
- Verificar conexión del relé

---

## 📞 SOPORTE

**PANDEMONIUM TECH** × **PAN AMERICAN SILVER**

---

*Versión 2.0 - Enero 2026*
