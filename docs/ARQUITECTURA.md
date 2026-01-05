# 🏗️ Arquitectura del Sistema - Campamento Parametican Silver

## Diagrama General

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CAMPAMENTO PARAMETICAN SILVER                         │
│                     Cerro Moro - 48°7'49.57"S, 66°39'10.42"W                │
└─────────────────────────────────────────────────────────────────────────────┘

                              RED WiFi LOCAL
    ┌──────────────────────────────────────────────────────────────────┐
    │                                                                  │
    │  ┌─────────────┐  ┌─────────────┐       ┌─────────────┐         │
    │  │  RIFT-01    │  │  RIFT-02    │  ...  │  RIFT-06    │         │
    │  │  ESP8266    │  │  ESP8266    │       │  ESP8266    │         │
    │  │  + DS18B20  │  │  (futuro)   │       │  (futuro)   │         │
    │  │  + Reed SW  │  │             │       │             │         │
    │  └──────┬──────┘  └──────┬──────┘       └──────┬──────┘         │
    │         │                │                     │                 │
    │         │    HTTP POST /api/data (cada 30s)    │                 │
    │         └────────────────┼─────────────────────┘                 │
    │                          │                                       │
    │                          ▼                                       │
    │                 ┌─────────────────┐                              │
    │                 │    RECEPTOR     │                              │
    │                 │     ESP32       │                              │
    │                 │  192.168.1.100  │                              │
    │                 │                 │                              │
    │                 │  ┌───────────┐  │                              │
    │                 │  │ Web Server│  │◄──── Navegador (Dashboard)   │
    │                 │  │ Puerto 80 │  │                              │
    │                 │  └───────────┘  │                              │
    │                 │                 │                              │
    │                 │  ┌───────────┐  │                              │
    │                 │  │ SQLite    │  │      Historial Local         │
    │                 │  │ (SPIFFS)  │  │                              │
    │                 │  └───────────┘  │                              │
    │                 │                 │                              │
    │                 │  ┌───────────┐  │                              │
    │                 │  │ Alertas   │  │                              │
    │                 │  │ Engine    │  │                              │
    │                 │  └───────────┘  │                              │
    │                 └────────┬────────┘                              │
    │                          │                                       │
    └──────────────────────────┼───────────────────────────────────────┘
                               │
                               │ (Cuando hay internet)
                               ▼
    ┌──────────────────────────────────────────────────────────────────┐
    │                         INTERNET                                  │
    │                                                                  │
    │   ┌─────────────────┐              ┌─────────────────┐          │
    │   │    SUPABASE     │              │    TELEGRAM     │          │
    │   │   (PostgreSQL)  │              │      BOT        │          │
    │   │                 │              │                 │          │
    │   │  - Historial    │              │  - Alertas      │          │
    │   │  - Estadísticas │              │  - Notificaciones│         │
    │   │  - Reportes     │              │                 │          │
    │   └─────────────────┘              └─────────────────┘          │
    │                                                                  │
    └──────────────────────────────────────────────────────────────────┘
```

## Flujo de Datos

### 1. Lectura de Sensores (EMISOR)
```
DS18B20 ──► ESP8266 ──► JSON ──► HTTP POST ──► RECEPTOR
                │
Reed Switch ────┘
```

### 2. Procesamiento (RECEPTOR)
```
HTTP POST ──► Validación ──► Almacenamiento ──► Verificación Alertas
                                    │                    │
                                    ▼                    ▼
                              Historial RAM        Telegram/Supabase
                              (circular buffer)    (si hay internet)
```

### 3. Visualización
```
Navegador ──► GET /api/status ──► JSON ──► Dashboard actualizado
              (cada 5 segundos)
```

## Componentes del Sistema

### EMISOR (ESP8266)

| Componente | Función |
|------------|---------|
| WiFi Client | Conexión a red local |
| OneWire Bus | Comunicación con DS18B20 |
| DallasTemperature | Lectura de temperaturas |
| HTTP Client | Envío de datos al receptor |
| GPIO Input | Lectura de Reed Switch |

**Ciclo de operación:**
1. Leer temperaturas (2 sensores)
2. Leer estado de puerta
3. Crear JSON con datos
4. Enviar HTTP POST al receptor
5. Esperar 30 segundos
6. Repetir

### RECEPTOR (ESP32)

| Componente | Función |
|------------|---------|
| WiFi AP/STA | Conexión con IP fija |
| Web Server | Servir dashboard y API |
| SPIFFS | Almacenar HTML/CSS/JS |
| Preferences | Configuración persistente |
| HTTP Client | Sync con Supabase/Telegram |

**Servicios:**
- **API REST**: Endpoints para datos y configuración
- **Dashboard**: Interfaz web completa
- **Alertas**: Motor de detección y notificación
- **Historial**: Buffer circular en RAM

## API REST

### Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/` | Dashboard HTML |
| GET | `/api/status` | Estado de todos los RIFTs |
| POST | `/api/data` | Recibir datos de emisor |
| GET | `/api/history?rift=1&period=day` | Historial |
| GET | `/api/alerts` | Alertas activas |
| GET | `/api/config` | Configuración actual |
| POST | `/api/config` | Actualizar configuración |
| POST | `/api/test-alert` | Enviar alerta de prueba |

### Formato de Datos

**Datos del emisor (POST /api/data):**
```json
{
  "rift_id": 1,
  "rift_name": "RIFT-01",
  "location": "Deposito Principal",
  "temp1": -25.5,
  "temp2": -24.8,
  "temp_avg": -25.15,
  "door_open": false,
  "door_open_since": 0,
  "sensor_count": 2,
  "rssi": -65,
  "uptime": 3600,
  "timestamp": 1234567890
}
```

**Estado del sistema (GET /api/status):**
```json
{
  "rifts": [
    {
      "id": 1,
      "name": "RIFT-01",
      "location": "Deposito Principal",
      "temp_avg": -25.15,
      "door_open": false,
      "online": true,
      "alert_active": false
    }
  ],
  "internet": true,
  "uptime": 7200,
  "current_time": "2024-01-15 14:30:00"
}
```

## Sistema de Alertas

### Tipos de Alerta

| Tipo | Condición | Acción |
|------|-----------|--------|
| **Crítica** | Temp > -10°C | Notificación inmediata |
| **Temperatura** | Temp > -18°C por 5+ min | Notificación con delay |
| **Puerta** | Abierta > 3 min | Notificación |
| **Offline** | Sin datos > 2 min | Marcar como offline |

### Anti-Falsos Positivos

```
┌─────────────────────────────────────────────────────────────────┐
│                    LÓGICA ANTI-FALSOS POSITIVOS                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Temperatura sube > umbral                                      │
│         │                                                       │
│         ▼                                                       │
│  ¿Puerta abierta? ──── SÍ ──► Esperar cierre + 2 min           │
│         │                            │                          │
│         NO                           ▼                          │
│         │                     ¿Temp normalizada?                │
│         ▼                            │                          │
│  Iniciar timer                  SÍ ──┴── NO                     │
│  (5 minutos)                    │        │                      │
│         │                       ▼        ▼                      │
│         ▼                    Ignorar   Alertar                  │
│  ¿Temp sigue alta?                                              │
│         │                                                       │
│    SÍ ──┴── NO                                                  │
│    │        │                                                   │
│    ▼        ▼                                                   │
│ Alertar  Resetear                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Escalabilidad

### Agregar más RIFTs

1. **Hardware**: Armar otro emisor ESP8266 + sensores
2. **Configuración**: Cambiar `RIFT_ID` (2-6) en el código del emisor
3. **Despliegue**: Subir código y conectar a la red

El receptor ya está preparado para 6 RIFTs simultáneos.

### Futuras expansiones

- **Sensor de corriente**: Monitorear compresor (CT clamp + ADS1115)
- **Sensor de humedad**: DHT22 o SHT31
- **Cámara**: ESP32-CAM para inspección visual
- **GPS**: Para tracking de vehículos
- **LoRa**: Para RIFTs muy alejados (>100m)

## Consideraciones de Diseño

### ¿Por qué ESP8266 para emisor?
- Más económico
- Suficiente para la tarea
- Menor consumo
- Amplia disponibilidad

### ¿Por qué ESP32 para receptor?
- Más memoria RAM (para historial)
- Más potencia de procesamiento
- Mejor manejo de conexiones simultáneas
- SPIFFS más robusto

### ¿Por qué HTTP en lugar de MQTT?
- Simplicidad
- No requiere broker externo
- Funciona 100% offline
- Fácil de debuggear

### ¿Por qué Supabase?
- Tier gratuito generoso
- PostgreSQL completo
- API REST automática
- Dashboard incluido
- Fácil de escalar
