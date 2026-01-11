# Arquitectura Multi-Dispositivo FrioSeguro

## Resumen Ejecutivo

Sistema de monitoreo industrial para **7 reefers** usando una única infraestructura Supabase, diferenciados por `device_id`.

---

## 1. Inventario de Dispositivos

| device_id | Nombre | Ubicación | Propósito |
|-----------|--------|-----------|-----------|
| `REEFER_01_SCZ` | Reefer Principal | Santa Cruz - Campamento | Producción |
| `REEFER_02_SCZ` | Reefer Carnes | Santa Cruz - Campamento | Producción |
| `REEFER_03_SCZ` | Reefer Lácteos | Santa Cruz - Campamento | Producción |
| `REEFER_04_SCZ` | Reefer Verduras | Santa Cruz - Campamento | Producción |
| `REEFER_05_SCZ` | Reefer Bebidas | Santa Cruz - Campamento | Producción |
| `REEFER_06_SCZ` | Reefer Backup | Santa Cruz - Campamento | Producción |
| `REEFER_DEV_BHI` | Reefer Desarrollo | Bahía Blanca | Testing/Dev |

### Convención de Nombres

```
REEFER_[NUMERO]_[UBICACION]

Donde:
- NUMERO: 01-99 para producción, DEV para desarrollo
- UBICACION: 
  - SCZ = Santa Cruz (producción)
  - BHI = Bahía Blanca (desarrollo)
```

---

## 2. Modelo de Datos

### Tabla Principal: `devices`

```sql
device_id VARCHAR(50) UNIQUE NOT NULL  -- Identificador único
name VARCHAR(100)                       -- Nombre descriptivo
location VARCHAR(200)                   -- Ubicación física
is_online BOOLEAN                       -- Estado de conexión
ip_address VARCHAR(50)                  -- IP actual del dispositivo
```

### Tabla de Lecturas: `readings`

Todas las lecturas se identifican por `device_id`:

```sql
device_id VARCHAR(50) NOT NULL  -- FK a devices
temp1, temp2, temp_avg          -- Temperaturas
door1_open, door2_open          -- Estado puertas
relay_on, alert_active          -- Estado sistema
defrost_mode                    -- Modo descongelación
created_at TIMESTAMPTZ          -- Timestamp
```

### Tabla de Alertas: `alerts`

```sql
device_id VARCHAR(50) NOT NULL  -- FK a devices
alert_type VARCHAR(50)          -- 'temperature', 'door', 'power', 'offline'
severity VARCHAR(20)            -- 'info', 'warning', 'critical', 'emergency'
resolved BOOLEAN                -- Estado de resolución
```

---

## 3. Flujo de Estados del Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│                    ESTADOS DEL DISPOSITIVO                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐    WiFi OK     ┌──────────┐    Datos OK    ┌────────────┐
│  │ OFFLINE  │ ─────────────► │  ONLINE  │ ─────────────► │  REPORTING │
│  └──────────┘                └──────────┘                └────────────┘
│       ▲                           │                            │
│       │                           │                            │
│       └───────────────────────────┴────────────────────────────┘
│                         Sin conexión > 5 min
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│                    ESTADOS DE ALERTA                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐   Temp > Max   ┌──────────┐   Temp > Crit  ┌────────────┐
│  │  NORMAL  │ ─────────────► │ WARNING  │ ─────────────► │  CRITICAL  │
│  └──────────┘                └──────────┘                └────────────┘
│       ▲                           │                            │
│       │         Temp OK           │         Temp OK            │
│       └───────────────────────────┴────────────────────────────┘
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│                    ESTADOS DE DESCONGELACIÓN                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐   Señal Relé   ┌──────────┐   Fin Señal    ┌────────────┐
│  │  NORMAL  │ ─────────────► │ DEFROST  │ ─────────────► │  COOLDOWN  │
│  └──────────┘                └──────────┘                └────────────┘
│       ▲                                                        │
│       │                    Cooldown 30 min                     │
│       └────────────────────────────────────────────────────────┘
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Arquitectura de Componentes

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              DISPOSITIVOS                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │ REEFER_01   │  │ REEFER_02   │  │ REEFER_06   │  │ REEFER_DEV  │    │
│  │ Santa Cruz  │  │ Santa Cruz  │  │ Santa Cruz  │  │ Bahía Blanca│    │
│  │ ESP32       │  │ ESP32       │  │ ESP32       │  │ ESP32       │    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘    │
│         │                │                │                │            │
│         └────────────────┴────────────────┴────────────────┘            │
│                                   │                                      │
│                                   ▼                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                              SUPABASE                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    PostgreSQL Database                           │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │    │
│  │  │ devices  │ │ readings │ │  alerts  │ │ commands │           │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                   │                                      │
│                                   ▼                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                              CLIENTES                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │ App Android │  │ Web Netlify │  │ Desktop App │  │ App Local   │    │
│  │   (Cloud)   │  │ Dashboard   │  │ (Viewer)    │  │ (WiFi Dir)  │    │
│  │             │  │             │  │             │  │             │    │
│  │ Filtra por  │  │ Filtra por  │  │ Filtra por  │  │ Conexión    │    │
│  │ device_id   │  │ device_id   │  │ device_id   │  │ directa ESP │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Configuración por Dispositivo

### Firmware (config.h)

Cada ESP32 debe tener su propio `device_id` configurado:

```cpp
// Santa Cruz - Producción
#define DEVICE_ID "REEFER_01_SCZ"
#define DEVICE_NAME "Reefer Principal"
#define LOCATION_DETAIL "Cerro Moro, Santa Cruz"

// Bahía Blanca - Desarrollo
#define DEVICE_ID "REEFER_DEV_BHI"
#define DEVICE_NAME "Reefer Desarrollo"
#define LOCATION_DETAIL "Bahía Blanca, Buenos Aires"
```

### Apps (Android/Web)

Las apps deben:
1. Cargar lista de dispositivos desde `devices` table
2. Permitir seleccionar dispositivo activo
3. Filtrar todas las consultas por `device_id`

---

## 6. Queries Importantes

### Obtener todos los dispositivos
```sql
SELECT * FROM devices ORDER BY device_id;
```

### Lecturas de un dispositivo específico
```sql
SELECT * FROM readings 
WHERE device_id = 'REEFER_01_SCZ' 
ORDER BY created_at DESC 
LIMIT 100;
```

### Alertas activas de todos los dispositivos
```sql
SELECT d.name, a.* 
FROM alerts a
JOIN devices d ON d.device_id = a.device_id
WHERE a.resolved = FALSE
ORDER BY a.created_at DESC;
```

### Estado actual de todos los dispositivos
```sql
SELECT * FROM v_devices_status;
```

---

## 7. Estrategia de Escalado

### Corto Plazo (7 dispositivos)
- Una sola instancia Supabase
- Índices por `device_id` ya creados
- Sin particionamiento

### Mediano Plazo (20+ dispositivos)
- Considerar particionamiento de `readings` por mes
- Agregar caching en apps
- Implementar agregaciones diarias automáticas

### Largo Plazo (100+ dispositivos)
- Particionamiento por `device_id` + fecha
- Réplicas de lectura
- CDN para assets estáticos

---

## 8. Checklist de Implementación

### Firmware
- [ ] Actualizar `config.h` con nuevo formato de `device_id`
- [ ] Verificar que cada ESP32 tenga ID único
- [ ] Probar envío de datos con nuevo ID

### Supabase
- [ ] Insertar los 7 dispositivos en tabla `devices`
- [ ] Verificar que no haya datos cruzados

### Apps
- [ ] App Cloud: agregar selector de dispositivo
- [ ] App Local: mantener conexión directa
- [ ] Web Netlify: agregar filtro por dispositivo
- [ ] Desktop Viewer: ya tiene filtro por dispositivo

### Testing
- [ ] Verificar datos de REEFER_DEV_BHI (Bahía Blanca)
- [ ] Verificar datos de REEFER_01_SCZ (Santa Cruz)
- [ ] Confirmar que no se mezclan

---

## 9. Contactos y Responsables

| Rol | Responsable | Dispositivos |
|-----|-------------|--------------|
| Desarrollo | Bahía Blanca | REEFER_DEV_BHI |
| Operaciones | Santa Cruz | REEFER_01-06_SCZ |

---

*Documento generado: 2026-01-11*
*Versión: 1.0*
