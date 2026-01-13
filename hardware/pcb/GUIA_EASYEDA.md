# Guía para crear el PCB en EasyEDA

## ¿Por qué EasyEDA en vez de KiCad?

- **Online y gratis**: https://easyeda.com
- **Visual**: Arrastras componentes, no escribís código
- **Librería enorme**: Tiene casi todos los componentes
- **Exporta directo a JLCPCB**: Pedís el PCB con 2 clicks
- **Más fácil** que KiCad para proyectos simples

---

## Paso 1: Crear cuenta y proyecto

1. Ir a https://easyeda.com
2. Crear cuenta (gratis)
3. File > New > Project
4. Nombre: "FrioSeguro_v1"

---

## Paso 2: Crear el esquemático

### Componentes a buscar en la librería (tecla P o Place > Component):

| Buscar | Componente | Cantidad |
|--------|------------|----------|
| `ESP32 DevKit` | ESP32-DevKitC-32D | 1 |
| `Screw Terminal 3P` | Bornera 3 pines 5.08mm | 1 (sensores temp) |
| `Screw Terminal 6P` | Bornera 6 pines 5.08mm | 1 (puertas) |
| `Screw Terminal 2P` | Bornera 2 pines 5.08mm | 4 (AC, defrost, USB, relay) |
| `Header 4P` | Pin header 4 pines | 1 (relay module) |
| `4.7K` | Resistor 4.7KΩ | 1 |
| `1M` | Resistor 1MΩ | 1 |
| `10K` | Resistor 10KΩ | 1 |
| `330R` | Resistor 330Ω | 2 |
| `Zener 3.3V` | Diodo zener | 1 |
| `100uF` | Capacitor electrolítico | 1 |
| `100nF` | Capacitor cerámico | 2 |
| `LED 3mm` | LED verde | 1 |
| `LED 3mm` | LED rojo | 1 |
| `Buzzer` | Buzzer activo | 1 |

### Para versión Premium agregar:
| Buscar | Componente | Cantidad |
|--------|------------|----------|
| `SIM800L` | Módulo GSM | 1 |
| `Header 6P` | Para SIM800L | 1 |
| `Header 4P` | Para batería | 1 |
| `Header 3P` | Jumper alimentación | 1 |
| `Header 2P` | Jumper SIM | 1 |
| `1000uF` | Capacitor SIM800L | 1 |

---

## Paso 3: Conexiones del esquemático

### Bus OneWire (sensores temperatura):
```
3.3V ─── R 4.7K ───┬─── GPIO4
                   │
                   └─── Bornera J1 pin 2 (DATA)

Bornera J1:
  Pin 1: GND
  Pin 2: DATA (GPIO4)
  Pin 3: 3.3V
```

### Sensores de puerta:
```
Bornera J2:
  Pin 1: GND
  Pin 2: GPIO5  (Puerta 1)
  Pin 3: GND
  Pin 4: GPIO12 (Puerta 2)
  Pin 5: GND
  Pin 6: GPIO13 (Puerta 3)
```

### Detección AC 220V:
```
J3 Pin 1 (FASE) ─── R 1MΩ ───┬─── R 10KΩ ───┬─── GPIO14
                              │              │
                              │         Zener 3.3V
                              │              │
J3 Pin 2 (NEUTRO) ────────────┴──────────────┴─── GND
```

### LEDs:
```
GPIO2  ─── R 330Ω ─── LED Verde ─── GND
GPIO15 ─── R 330Ω ─── LED Rojo ──── GND
```

### Buzzer:
```
GPIO18 ─── Buzzer (+) 
GND ────── Buzzer (-)
```

### Relay module:
```
Bornera J5:
  Pin 1: 5V
  Pin 2: GND
  Pin 3: GPIO25 (IN1)
  Pin 4: GPIO26 (IN2)
```

### Señal Defrost:
```
Bornera J4:
  Pin 1: GND
  Pin 2: GPIO33
```

### Alimentación:
```
Bornera J6:
  Pin 1: 5V (entrada)
  Pin 2: GND

5V ─── C 100uF ─── GND (filtro entrada)
3.3V ── C 100nF ── GND (desacoplo ESP32)
```

---

## Paso 4: Convertir a PCB

1. En EasyEDA: Design > Convert to PCB
2. Definir tamaño: 100mm x 80mm
3. 2 capas (Top + Bottom)

### Tips de ruteo:
- Pistas de señal: 0.25mm
- Pistas de potencia (5V, GND): 0.5mm o más
- Mantener GND como plano en Bottom layer
- Separar zona AC 220V del resto (¡peligro!)

---

## Paso 5: Exportar y fabricar

1. Fabrication > PCB Fabrication File (Gerber)
2. Descargar ZIP
3. Subir a JLCPCB: https://jlcpcb.com
4. Opciones recomendadas:
   - Layers: 2
   - PCB Thickness: 1.6mm
   - Surface Finish: HASL (lead free)
   - Quantity: 5 (mínimo)
   - Color: Verde (más barato) o Negro (más pro)

**Costo aproximado**: $2-5 USD + envío (~$15-20 USD)

---

## Alternativa: Pedir PCB con montaje (PCBA)

JLCPCB también puede soldar los componentes SMD por vos:
1. Al subir Gerber, activar "SMT Assembly"
2. Subir BOM y Pick & Place files
3. Ellos sueldan resistencias, capacitores, etc.
4. Solo tenés que soldar los módulos grandes (ESP32, borneras)

---

## Recursos útiles

- **Tutorial EasyEDA**: https://docs.easyeda.com/en/Tutorial/
- **JLCPCB**: https://jlcpcb.com
- **PCBWay**: https://pcbway.com (alternativa)
- **AllPCB**: https://allpcb.com (alternativa)
