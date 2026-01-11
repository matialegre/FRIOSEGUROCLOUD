@echo off
chcp 65001 >nul
echo ============================================
echo   DIAGNOSTICO AVANZADO - ESP32 REEFER
echo ============================================
echo.
echo Este script prueba MULTIPLES metodos para encontrar el ESP32
echo Puede tardar unos minutos...
echo.

python discover_esp32_diagnostico.py

echo.
pause
