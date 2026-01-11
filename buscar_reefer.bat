@echo off
chcp 65001 >nul
echo ============================================
echo   BUSCAR ESP32 REEFER EN LA RED
echo ============================================
echo.

python discover_esp32.py -t 5

echo.
pause
