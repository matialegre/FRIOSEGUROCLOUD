# 📱 App Android - Alerta RIFT

App nativa Android para recibir alertas de temperatura de los RIFTs del Campamento Parametican Silver.

## Características

- ✅ **Funciona en segundo plano** (Foreground Service)
- ✅ **Suena aunque el celular esté bloqueado**
- ✅ **Vibración continua** hasta silenciar
- ✅ **Pantalla completa roja** de alerta
- ✅ **Ignora modo "No Molestar"**
- ✅ **Inicia automáticamente** al encender el celular
- ✅ **No necesita internet** - Solo WiFi local
- ✅ **Polling cada 5 segundos**

## Requisitos

- Android 7.0+ (API 24)
- Conexión al WiFi del campamento

## Cómo compilar

### Opción 1: Android Studio (si lo tenés)
1. Abrir la carpeta `android-app` como proyecto
2. Build > Build APK
3. El APK estará en `app/build/outputs/apk/debug/`

### Opción 2: Línea de comandos (sin IDE)

Necesitás tener instalado:
- Java JDK 17
- Android SDK (se puede instalar solo el command-line tools)

```bash
# En Windows (PowerShell)
cd android-app
.\gradlew.bat assembleDebug

# En Linux/Mac
cd android-app
./gradlew assembleDebug
```

El APK estará en: `app/build/outputs/apk/debug/app-debug.apk`

### Opción 3: AIDE (compilar en el celular)
1. Instalar AIDE desde Play Store
2. Importar el proyecto
3. Compilar directamente en el celular

### Opción 4: Servicio online
- Usar https://appetize.io o similar para compilar online
- O pedirle a alguien que tenga Android Studio que te compile el APK

## Instalación del APK

1. Copiar el APK al celular
2. Habilitar "Orígenes desconocidos" en Configuración > Seguridad
3. Abrir el APK e instalar
4. Dar todos los permisos que pida

## Uso

1. Abrir la app
2. Ingresar la IP del receptor (ej: 192.168.1.100)
3. Tocar "INICIAR MONITOREO"
4. La app queda en segundo plano monitoreando
5. Si hay alerta: pantalla roja + sirena + vibración

## Permisos necesarios

- **Internet**: Para conectar al ESP32
- **Vibración**: Para alertas
- **Notificaciones**: Para mostrar estado
- **Iniciar al bootear**: Para arrancar automáticamente
- **Pantalla de bloqueo**: Para mostrar alertas

## Estructura del proyecto

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/parametican/alertarift/
│   │   │   ├── MainActivity.kt      # Pantalla principal
│   │   │   ├── MonitorService.kt    # Servicio de monitoreo
│   │   │   ├── AlertActivity.kt     # Pantalla de alerta
│   │   │   └── BootReceiver.kt      # Iniciar al bootear
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   └── activity_alert.xml
│   │   │   └── values/
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Troubleshooting

### La app no suena
- Verificar que no esté en modo silencio
- Dar permiso de "Ignorar optimización de batería"
- Verificar permisos de notificación

### No conecta al ESP32
- Verificar que el celular esté en el WiFi correcto
- Verificar la IP del receptor
- Probar acceder a `http://IP/api/status` desde el navegador

### Se cierra sola
- Desactivar optimización de batería para esta app
- En Xiaomi/Huawei: Agregar a "Apps protegidas"
