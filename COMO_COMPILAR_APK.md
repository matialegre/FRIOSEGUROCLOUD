# 📱 Cómo Compilar el APK de Android

No tenés Java instalado, así que hay varias opciones:

---

## Opción 1: GitHub Actions (RECOMENDADA - Gratis)

1. Subí la carpeta `android-app` a un repositorio de GitHub
2. Creá el archivo `.github/workflows/build.yml` con este contenido:

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Build APK
      run: |
        cd android-app
        chmod +x gradlew
        ./gradlew assembleDebug
    
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: android-app/app/build/outputs/apk/debug/app-debug.apk
```

3. Hacé push y esperá que compile
4. Descargá el APK desde la pestaña "Actions" → "Artifacts"

---

## Opción 2: Compilar en el Celular con AIDE

1. Instalá **AIDE** desde Play Store (gratis)
2. Copiá la carpeta `android-app` al celular
3. Abrí AIDE y seleccioná el proyecto
4. Tocá "Build" → "Build APK"
5. Instalá directamente

---

## Opción 3: Servicio Online - Codemagic

1. Andá a https://codemagic.io
2. Creá cuenta gratis
3. Conectá tu repo de GitHub
4. Compilá y descargá el APK

---

## Opción 4: Instalar Java y Android SDK

Si querés compilar localmente:

### Windows:
```powershell
# Instalar Chocolatey (si no lo tenés)
Set-ExecutionPolicy Bypass -Scope Process -Force
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Instalar Java y Android SDK
choco install openjdk17 -y
choco install android-sdk -y

# Reiniciar terminal y compilar
cd android-app
.\gradlew.bat assembleDebug
```

---

## Opción 5: Pedirle a alguien

Si conocés a alguien con Android Studio instalado, pasale la carpeta `android-app` y que ejecute:
- Build → Build Bundle(s) / APK(s) → Build APK(s)

---

## Una vez que tengas el APK

1. Copialo al celular
2. Habilitá "Orígenes desconocidos" en Configuración → Seguridad
3. Abrí el APK e instalá
4. Abrí la app, ingresá la IP del ESP32 (ej: 192.168.1.100)
5. Tocá "INICIAR MONITOREO"
6. ¡Listo! La app monitoreará 24/7

---

## Estructura del proyecto Android

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/parametican/alertarift/
│   │   │   ├── MainActivity.kt       # Pantalla principal
│   │   │   ├── MonitorService.kt     # Servicio 24/7
│   │   │   ├── AlertActivity.kt      # Pantalla de alerta
│   │   │   └── BootReceiver.kt       # Auto-inicio
│   │   ├── res/layout/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradlew.bat
```
