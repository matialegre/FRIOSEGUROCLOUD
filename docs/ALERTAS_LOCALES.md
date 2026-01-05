# 🔔 Sistema de Alertas Locales (Sin Internet)

## Problema Resuelto

Si no hay internet por 16 horas, el sistema igual avisa mediante:
1. **SIRENA POTENTE 12V** - Se escucha en todo el campamento
2. **Buzzer pequeño** en el receptor
3. **LEDs** (rojo = alerta, verde = OK)
4. **App móvil** con vibración y sonido (funciona en red local)

---

## ⚠️ REALIDAD sobre notificaciones web

### Lo que SÍ funciona (sin HTTPS, en red local):
- ✅ Vibración del celular (Android Chrome, NO iPhone)
- ✅ Sonido con Web Audio API (si el usuario activó la app)
- ✅ Pantalla roja/alertas visuales
- ✅ Polling constante cada 5 segundos
- ✅ Wake Lock (mantener pantalla encendida)

### Lo que NO funciona:
- ❌ **Push Notifications reales** (requieren HTTPS + servidor externo)
- ❌ **Alertas con pantalla bloqueada** (el navegador no puede)
- ❌ **iPhone Safari** - Muy limitado, no vibra, audio restringido
- ❌ **Notificaciones aunque cierres la app**

### Conclusión:
**La app web es un COMPLEMENTO, no la solución principal.**
La solución principal es la **SIRENA FÍSICA de 12V** que se escucha aunque no haya nadie mirando el celular.

---

## 1. Hardware del Receptor

### Conexiones Completas

```
                         ESP32 DevKit
                         ┌─────────────┐
                         │             │
    LED Verde ───[220Ω]──┤ GPIO27      │
                         │             │
    LED Rojo ────[220Ω]──┤ GPIO26      │
                         │             │
    Buzzer ──────────────┤ GPIO25      │ (ver circuito abajo)
                         │             │
    SIRENA 12V ──────────┤ GPIO32      │ (ver circuito abajo)
                         │             │
                         │ GND ────────┼──── GND común
                         │             │
                         │ VIN ────────┼──── +5V (para buzzer)
                         │             │
                         └─────────────┘
```

### Circuito del Buzzer 5V (GPIO25)

```
        +5V (VIN del ESP32)
         │
    ┌────┴────┐
    │ BUZZER  │
    │  5V     │
    └────┬────┘
         │ Colector (C)
    ┌────┴────┐
    │ 2N2222  │
    │  NPN    │
    └────┬────┘
         │ Emisor (E)
         │
        GND
         
GPIO25 ──[1kΩ]── Base (B)
```

### Circuito de la SIRENA 12V (GPIO32) - ¡IMPORTANTE!

Esta es la alerta principal. Usar un **módulo relay** o transistor de potencia:

```
    +12V (Fuente externa)
         │
    ┌────┴────┐
    │ SIRENA  │  ← Sirena de alarma 12V (tipo auto/casa)
    │  12V    │    Se escucha a 100+ metros
    └────┬────┘
         │
         │ COM (común del relay)
    ┌────┴────┐
    │  RELAY  │  ← Módulo relay 5V (con optoacoplador)
    │   5V    │
    └─┬──┬──┬─┘
      │  │  │
     VCC IN GND
      │  │  │
     5V  │  GND
         │
      GPIO32
```

**Componentes para sirena:**
- 1x Módulo Relay 5V (con optoacoplador, tipo SRD-05VDC)
- 1x Sirena 12V (tipo alarma de auto, 110+ dB)
- 1x Fuente 12V 1A (para la sirena)

### Lista de componentes completa

| Componente | Cantidad | Precio Est. |
|------------|----------|-------------|
| Transistor 2N2222 | 1 | $0.20 |
| Resistencia 1kΩ | 1 | $0.05 |
| Resistencia 220Ω | 2 | $0.10 |
| Buzzer activo 5V | 1 | $1.50 |
| LED Rojo 5mm | 1 | $0.10 |
| LED Verde 5mm | 1 | $0.10 |
| Módulo Relay 5V | 1 | $2.00 |
| **Sirena 12V 110dB** | 1 | $8-15 |
| Fuente 12V 1A | 1 | $5.00 |
| **TOTAL** | | **~$20** |

### Alternativa: Buzzer de 3.3V (sin transistor)

```
GPIO25 ────── Buzzer (+)
GND ───────── Buzzer (-)
```

---

## 2. App Móvil para Celulares

### Acceso

Los celulares conectados a la red WiFi del campamento acceden a:

```
http://192.168.1.100/app
```

### Características

- **Polling cada 10 segundos** - Consulta el estado constantemente
- **Vibración** - Cuando hay alerta nueva, vibra el celular
- **Sonido** - Reproduce un beep de alerta
- **Pantalla roja** - Banner visible de alerta
- **Botón silenciar** - Para no molestar después de ver la alerta
- **Funciona sin HTTPS** - Es red local, no necesita certificado
- **No necesita instalación** - Es una página web normal

### Instrucciones para los usuarios

1. Conectar celular al WiFi del campamento
2. Abrir navegador (Chrome, Safari, etc.)
3. Ir a `http://192.168.1.100/app`
4. **Agregar a pantalla de inicio** (opcional pero recomendado):
   - Chrome Android: Menú → "Agregar a pantalla de inicio"
   - Safari iOS: Compartir → "Agregar a inicio"
5. Dejar la app abierta en segundo plano
6. El celular vibrará y sonará si hay alerta

### Limitaciones

- El celular debe estar conectado al WiFi local
- La app debe estar abierta (aunque sea en segundo plano)
- Algunos celulares matan apps en segundo plano (configurar excepciones)

---

## 3. Comportamiento de Alertas

### LED Verde (GPIO27)
- **Encendido fijo**: Todo OK, temperaturas normales
- **Apagado**: Hay una alerta activa

### LED Rojo (GPIO26)
- **Apagado**: Todo OK
- **Encendido fijo**: Alerta activa

### Buzzer (GPIO25)
- **Silencio**: Todo OK
- **Beep cada 2 segundos**: Alerta activa
- **Se puede silenciar**: Desde la app móvil o dashboard

### App Móvil
- **Banner rojo parpadeante**: Alerta activa
- **Vibración**: Al detectar nueva alerta
- **Sonido**: Beep al detectar alerta
- **Repite cada 30 segundos**: Si no se silencia

---

## 4. Flujo de Alerta

```
Temperatura sube > umbral
        │
        ▼
┌───────────────────┐
│ Sistema detecta   │
│ alerta            │
└────────┬──────────┘
         │
         ├──────────────────────────────────────┐
         │                                      │
         ▼                                      ▼
┌─────────────────┐                   ┌─────────────────┐
│ RECEPTOR        │                   │ CELULARES       │
│ - LED rojo ON   │                   │ (polling /app)  │
│ - LED verde OFF │                   │                 │
│ - Buzzer beep   │                   │ - Vibración     │
└─────────────────┘                   │ - Sonido        │
                                      │ - Banner rojo   │
                                      └─────────────────┘
         │                                      │
         │                                      │
         ▼                                      ▼
┌─────────────────┐                   ┌─────────────────┐
│ Alguien ve/oye  │                   │ Usuario ve en   │
│ la alerta       │                   │ su celular      │
└────────┬────────┘                   └────────┬────────┘
         │                                      │
         └──────────────┬───────────────────────┘
                        │
                        ▼
              ┌─────────────────┐
              │ Ir a revisar    │
              │ el RIFT         │
              └─────────────────┘
```

---

## 5. Configuración Recomendada

### Para el campamento

1. **Receptor en lugar central** - Donde se escuche el buzzer
2. **5 celulares mínimo** con la app:
   - Encargado de cocina
   - Jefe de turno
   - Mantenimiento
   - Seguridad
   - Backup

### Umbrales sugeridos

| Parámetro | Valor | Razón |
|-----------|-------|-------|
| Temp. Máxima | -18°C | Límite seguro para alimentos |
| Temp. Crítica | -10°C | Alerta inmediata |
| Delay alerta | 300s (5 min) | Evita falsos positivos por puerta |
| Máx. puerta abierta | 180s (3 min) | Tiempo razonable de carga/descarga |

---

## 6. Testing

### Probar buzzer
```cpp
// En el setup() agregar temporalmente:
digitalWrite(BUZZER_PIN, HIGH);
delay(1000);
digitalWrite(BUZZER_PIN, LOW);
```

### Probar desde la app
1. Ir a `http://192.168.1.100/app`
2. Usar el botón "Probar Alerta" en el dashboard principal
3. Verificar que vibra y suena

### Simular alerta
Desde el dashboard principal (`http://192.168.1.100`):
- Configurar umbral muy bajo temporalmente
- O usar el simulador web del proyecto
