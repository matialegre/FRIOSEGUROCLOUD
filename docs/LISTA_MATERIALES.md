# 🛒 Lista de Materiales - Sistema Parametican Silver

## Por cada EMISOR (RIFT)

| Cant | Componente | Especificación | Precio Est. (USD) |
|------|------------|----------------|-------------------|
| 1 | ESP8266 NodeMCU | ESP-12E, CH340/CP2102 | $4-6 |
| 2 | DS18B20 Waterproof | Cable 1m, rango -55°C a +125°C | $3-4 c/u |
| 1 | Reed Switch | Normalmente abierto, con cable | $1-2 |
| 1 | Resistencia 4.7kΩ | 1/4W | $0.10 |
| 1 | Caja estanca | IP65 mínimo, 100x68x50mm | $5-8 |
| 1 | Fuente 5V 1A | Con cable micro USB | $3-5 |
| - | Cables dupont | Hembra-hembra, varios | $2 |
| - | Prensacables | PG7 o PG9 | $1 |

**Subtotal por emisor: ~$25-35 USD**

## RECEPTOR (Central)

| Cant | Componente | Especificación | Precio Est. (USD) |
|------|------------|----------------|-------------------|
| 1 | ESP32 DevKit | 38 pines, con antena | $6-10 |
| 1 | Caja | Opcional, para montaje | $3-5 |
| 1 | Fuente 5V 2A | Con cable micro USB | $4-6 |

**Subtotal receptor: ~$15-20 USD**

## Total Sistema Inicial (1 RIFT + Receptor)

| Concepto | Precio Est. |
|----------|-------------|
| 1x Emisor completo | $30 |
| 1x Receptor | $18 |
| Envío estimado | $10-20 |
| **TOTAL** | **~$60-70 USD** |

## Expansión (5 RIFTs adicionales)

| Concepto | Precio Est. |
|----------|-------------|
| 5x Emisores completos | $150 |
| **TOTAL expansión** | **~$150 USD** |

## Proveedores Recomendados (Argentina)

### Electrónica
- **Nubbeo** - nubbeo.com.ar
- **Electrocomponentes** - electrocomponentes.com
- **Patagoniatec** - patagoniatec.com.ar
- **Mercado Libre** - Buscar vendedores con buena reputación

### Sensores DS18B20
- Buscar específicamente "DS18B20 waterproof" o "sonda temperatura sumergible"
- Verificar que sea el modelo genuino (hay clones de menor calidad)
- Preferir cables de 1m o más para instalación flexible

## Herramientas Necesarias

| Herramienta | Uso |
|-------------|-----|
| Soldador + estaño | Conexiones permanentes (opcional) |
| Multímetro | Verificar conexiones |
| Destornilladores | Montaje de cajas |
| Pistola de silicona | Sellado de cables |
| Cable USB | Programación |

## Notas Importantes

### Sobre los DS18B20
- **Genuinos vs Clones**: Los clones pueden tener menor precisión
- **Waterproof**: Esencial para ambientes de frío/humedad
- **Rango**: -55°C a +125°C (sobrado para -40°C a +20°C)
- **Precisión**: ±0.5°C (suficiente para la aplicación)

### Sobre las Cajas
- Usar cajas IP65 o superior
- Prever entrada de cables con prensacables
- Considerar ventilación si hay calor del ESP

### Sobre la Alimentación
- Preferir fuentes de calidad (no genéricas baratas)
- Considerar UPS pequeño para cortes de luz
- El ESP8266 consume ~80mA, el ESP32 ~150mA

## Opcionales / Futuras Expansiones

| Componente | Uso | Precio Est. |
|------------|-----|-------------|
| Sensor CT (SCT-013) | Medir corriente compresor | $8-12 |
| ADS1115 | ADC para sensor CT | $3-5 |
| DHT22 | Humedad + temperatura | $4-6 |
| Display OLED 0.96" | Mostrar datos localmente | $5-8 |
| Buzzer | Alarma sonora local | $1 |
| Batería 18650 + holder | Backup de energía | $8-12 |
| Módulo LoRa SX1278 | Para RIFTs muy alejados | $10-15 |
