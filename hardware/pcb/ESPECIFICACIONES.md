# FrioSeguro PCB v1.0 - Especificaciones Técnicas

## Resumen del Proyecto

PCB modular para sistema de monitoreo de temperatura en reefers.
- **Versión Básica**: ESP32 + sensores + relay
- **Versión Premium**: Básica + SIM800L + batería 18650

---

## Dimensiones Sugeridas del PCB

- **Ancho**: 100mm
- **Alto**: 80mm
- **Capas**: 2 (Top + Bottom)
- **Espesor**: 1.6mm
- **Acabado**: HASL sin plomo

---

## Asignación de Pines ESP32 NodeMCU 38 pines

| GPIO | Función | Tipo | Notas |
|------|---------|------|-------|
| 0 | BOOT / Reset WiFi | INPUT | Botón integrado, mantener 3s para reset |
| 2 | LED OK (integrado) | OUTPUT | LED azul del módulo |
| 4 | Bus OneWire | I/O | DS18B20 x3, pull-up 4.7K |
| 5 | Puerta 1 | INPUT_PULLUP | Reed switch |
| 12 | Puerta 2 | INPUT_PULLUP | Reed switch |
| 13 | Puerta 3 | INPUT_PULLUP | Reed switch |
| 14 | Detección AC 220V | INPUT | Divisor resistivo |
| 15 | LED Alerta | OUTPUT | LED rojo externo |
| 16 | TX2 → SIM800L | OUTPUT | Solo Premium |
| 17 | RX2 ← SIM800L | INPUT | Solo Premium |
| 18 | Buzzer | OUTPUT | Buzzer activo 5V |
| 25 | Relay IN1 | OUTPUT | Módulo relay CH1 |
| 26 | Relay IN2 | OUTPUT | Módulo relay CH2 |
| 27 | DHT22 (opcional) | I/O | Sensor ambiente |
| 32 | Voltaje batería | ADC | Solo Premium, divisor |
| 33 | Señal Defrost | INPUT_PULLUP | Del reefer |
| 34 | Libre | ADC | Expansión futura |
| 35 | Libre | ADC | Expansión futura |

---

## Borneras de Conexión

### J1 - Sensores de Temperatura DS18B20 (3 pines, pitch 5.08mm)
```
┌─────┬─────┬─────┐
│ GND │DATA │ VCC │
│  ●  │  ●  │  ●  │
└─────┴─────┴─────┘
  1     2     3

Pin 1: GND (negro de los 3 sensores)
Pin 2: DATA (amarillo, GPIO4 con R 4.7K a 3.3V)
Pin 3: VCC 3.3V (rojo de los 3 sensores)

Los 3 sensores van en PARALELO en el mismo bus.
Cada DS18B20 tiene dirección única de fábrica.
```

### J2 - Sensores de Puerta (6 pines, pitch 5.08mm)
```
┌─────┬─────┬─────┬─────┬─────┬─────┐
│ GND │ P1  │ GND │ P2  │ GND │ P3  │
│  ●  │  ●  │  ●  │  ●  │  ●  │  ●  │
└─────┴─────┴─────┴─────┴─────┴─────┘
  1     2     3     4     5     6

Pin 1: GND
Pin 2: Puerta 1 (GPIO5)
Pin 3: GND
Pin 4: Puerta 2 (GPIO12)
Pin 5: GND
Pin 6: Puerta 3 (GPIO13)

Reed switch: un cable a GND, otro al pin GPIO.
Pull-up interno del ESP32 activado.
```

### J3 - Detección AC 220V (2 pines, pitch 5.08mm)
```
┌─────┬─────┐
│FASE │NEUT │
│  ●  │  ●  │
└─────┴─────┘
  1     2

⚠️ PELIGRO: 220V AC
Conectar cable de la fuente USB (fase y neutro).
El circuito detecta si hay energía AC.
```

### J4 - Señal Defrost del Reefer (2 pines, pitch 5.08mm)
```
┌─────┬─────┐
│ GND │ SIG │
│  ●  │  ●  │
└─────┴─────┘
  1     2

Pin 1: GND
Pin 2: Señal (GPIO33)

Conectar al contacto del relé de defrost del reefer.
Configurable como NO o NC desde el software.
```

### J5 - Módulo Relay 2CH (4 pines, pitch 2.54mm)
```
┌─────┬─────┬─────┬─────┐
│ VCC │ GND │ IN1 │ IN2 │
│  ●  │  ●  │  ●  │  ●  │
└─────┴─────┴─────┴─────┘
  1     2     3     4

Pin 1: VCC (5V)
Pin 2: GND
Pin 3: IN1 (GPIO25) - Relay 1
Pin 4: IN2 (GPIO26) - Relay 2
```

### J6 - Alimentación USB 5V (2 pines, pitch 5.08mm)
```
┌─────┬─────┐
│ +5V │ GND │
│  ●  │  ●  │
└─────┴─────┘
  1     2

Entrada principal de alimentación.
Fuente USB 5V 2A mínimo recomendado.
```

---

## Zona Premium (Opcional)

### J7 - Módulo SIM800L (6 pines, pitch 2.54mm)
```
┌─────┬─────┬─────┬─────┬─────┬─────┐
│ VCC │ GND │ TXD │ RXD │ RST │RING │
│  ●  │  ●  │  ●  │  ●  │  ●  │  ●  │
└─────┴─────┴─────┴─────┴─────┴─────┘
  1     2     3     4     5     6

Pin 1: VCC (4.0V desde Step-Down)
Pin 2: GND
Pin 3: TXD → GPIO17 (RX2 del ESP32)
Pin 4: RXD ← GPIO16 (TX2 del ESP32)
Pin 5: RST (opcional)
Pin 6: RING (opcional)

⚠️ SIM800L requiere 4.0V estables y picos de 2A
```

### J8 - Shield Cargador 18650 (4 pines, pitch 2.54mm)
```
┌─────┬─────┬─────┬─────┐
│ VIN │ GND │VBAT+│VBAT-│
│  ●  │  ●  │  ●  │  ●  │
└─────┴─────┴─────┴─────┘
  1     2     3     4

Pin 1: VIN (5V desde USB)
Pin 2: GND
Pin 3: VBAT+ (3.7-4.2V salida)
Pin 4: VBAT- (GND batería)
```

### JP1 - Jumper Selección Alimentación (3 pines)
```
┌───┬───┬───┐
│USB│COM│BAT│
│ ● │ ● │ ● │
└───┴───┴───┘
  1   2   3

Posición 1-2: Alimentación USB directa
Posición 2-3: Alimentación desde batería
```

### JP2 - Jumper Habilitación SIM800L (2 pines)
```
┌───┬───┐
│EN │GND│
│ ● │ ● │
└───┴───┘

Cerrado: SIM800L alimentado
Abierto: SIM800L apagado (ahorro energía)
```

---

## Circuitos de Protección

### Divisor Resistivo Detección AC 220V
```
FASE 220V ──┬── R1 (1MΩ 1/2W) ──┬── R2 (10KΩ) ──┬── GPIO14
            │                    │               │
            │                    │    D1 Zener   │
            │                    │    3.3V       │
            │                    │      │        │
NEUTRO ─────┴────────────────────┴──────┴────────┴── GND

Vout = 220V × 10K / (1M + 10K) ≈ 2.2V (seguro para GPIO)
El zener 3.3V protege contra sobretensiones.
```

### Pull-up Bus OneWire
```
3.3V ────── R3 (4.7KΩ) ──┬── GPIO4
                         │
                         ├── DS18B20 #1 (DATA)
                         ├── DS18B20 #2 (DATA)
                         └── DS18B20 #3 (DATA)
```

### LEDs Indicadores
```
GPIO2 ──── R4 (330Ω) ──── LED Verde ──── GND
GPIO15 ─── R5 (330Ω) ──── LED Rojo ───── GND
```

---

## Lista de Materiales (BOM)

### Componentes Básicos (todas las placas)

| Qty | Referencia | Valor | Footprint | Notas |
|-----|------------|-------|-----------|-------|
| 1 | U1 | ESP32-NodeMCU | 38 pines | Módulo completo |
| 1 | R1 | 1MΩ | 0805 o THT | Divisor AC, 1/2W |
| 1 | R2 | 10KΩ | 0805 o THT | Divisor AC |
| 1 | R3 | 4.7KΩ | 0805 o THT | Pull-up OneWire |
| 2 | R4, R5 | 330Ω | 0805 o THT | LEDs |
| 1 | D1 | Zener 3.3V | SOD-123 o THT | Protección AC |
| 1 | C1 | 100µF/16V | Electrolítico | Filtro entrada |
| 2 | C2, C3 | 100nF | 0805 | Desacoplo |
| 1 | LED1 | Verde 3mm | THT | Indicador OK |
| 1 | LED2 | Rojo 3mm | THT | Indicador Alerta |
| 1 | BZ1 | Buzzer 5V | THT | Activo |
| 1 | J1 | Bornera 3P | 5.08mm | Sensores temp |
| 1 | J2 | Bornera 6P | 5.08mm | Puertas |
| 1 | J3 | Bornera 2P | 5.08mm | AC 220V |
| 1 | J4 | Bornera 2P | 5.08mm | Defrost |
| 1 | J5 | Header 4P | 2.54mm | Relay |
| 1 | J6 | Bornera 2P | 5.08mm | USB 5V |

### Componentes Premium (adicionales)

| Qty | Referencia | Valor | Footprint | Notas |
|-----|------------|-------|-----------|-------|
| 1 | U2 | SIM800L | Módulo | Con antena |
| 1 | U3 | MP1584 | Módulo | Step-Down 4.0V |
| 1 | U4 | Shield 18650 | Módulo | Cargador |
| 1 | C4 | 1000µF/10V | Electrolítico | SIM800L |
| 1 | C5 | 100nF | 0805 | Step-Down |
| 1 | R6 | 100KΩ | 0805 | Divisor batería |
| 1 | R7 | 100KΩ | 0805 | Divisor batería |
| 1 | J7 | Header 6P | 2.54mm | SIM800L |
| 1 | J8 | Header 4P | 2.54mm | Batería |
| 1 | JP1 | Header 3P | 2.54mm | Jumper alim |
| 1 | JP2 | Header 2P | 2.54mm | Jumper SIM |
| 2 | - | Jumper | 2.54mm | - |

---

## Notas de Fabricación

1. **Fabricantes recomendados**: JLCPCB, PCBWay, AllPCB
2. **Cantidad mínima**: 5 unidades
3. **Costo aproximado**: $5-15 USD por 5 placas (sin envío)
4. **Tiempo de fabricación**: 3-7 días
5. **Archivos necesarios**: Gerber ZIP

---

## Carcasa 3D

### Dimensiones internas requeridas:
- **Versión Básica**: 110 x 90 x 40 mm
- **Versión Premium**: 130 x 100 x 60 mm (espacio para batería)

### Búsqueda en Thingiverse:
- "ESP32 project box"
- "Electronics enclosure 100x80"
- "Waterproof junction box"

### Modelos sugeridos:
1. https://www.thingiverse.com/thing:4820236 - ESP32 Case modular
2. https://www.thingiverse.com/thing:3436669 - Project box parametric
3. https://www.thingiverse.com/thing:2627220 - Waterproof enclosure

---

## Próximos Pasos

1. ✅ Especificaciones definidas
2. ⬜ Crear esquemático en KiCad (símbolos)
3. ⬜ Diseñar PCB layout
4. ⬜ Generar Gerbers
5. ⬜ Enviar a fabricar
6. ⬜ Diseñar/adaptar carcasa 3D
