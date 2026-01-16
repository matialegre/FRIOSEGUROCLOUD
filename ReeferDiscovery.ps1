# ============================================
# REEFER DISCOVERY TOOL - Santa Cruz
# Busca el ESP32 Reefer en la red local
# ============================================

param(
    [string]$DeviceId = "REEFER_01_SCZ"
)

$Host.UI.RawUI.WindowTitle = "Reefer Discovery Tool"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   REEFER DISCOVERY TOOL - Santa Cruz" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Buscando dispositivo: $DeviceId" -ForegroundColor Yellow
Write-Host ""

$SupabaseUrl = "https://sxjmqxwdqdicxcoascks.supabase.co"
$SupabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN4am1xeHdkcWRpY3hjb2FzY2tzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcxNTMwNzcsImV4cCI6MjA2MjcyOTA3N30.lPvnJFxVWjU8CrVPSsNPPHxnBz2xRc8VLvXNfCPKbQU"

$foundIp = $null
$results = @{
    mDNS = @{ Status = "Pendiente"; IP = $null; Time = 0 }
    Supabase = @{ Status = "Pendiente"; IP = $null; Time = 0 }
    Scan = @{ Status = "Pendiente"; IP = $null; Time = 0 }
}

# Función para probar si una IP es un Reefer
function Test-ReeferIP {
    param([string]$IP)
    try {
        $response = Invoke-WebRequest -Uri "http://$IP/api/status" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq 200 -and $response.Content -match "sensor" -and $response.Content -match "system") {
            return $true
        }
    } catch { }
    return $false
}

# ============================================
# MÉTODO 1: mDNS (reefer.local)
# ============================================
Write-Host "[1/3] Probando mDNS (reefer.local)..." -ForegroundColor White
$sw = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $response = Invoke-WebRequest -Uri "http://reefer.local/api/status" -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        $results.mDNS.Status = "ENCONTRADO"
        $results.mDNS.IP = "reefer.local"
        $foundIp = "reefer.local"
        Write-Host "   ✅ mDNS: ENCONTRADO en reefer.local" -ForegroundColor Green
    }
} catch {
    $results.mDNS.Status = "No disponible"
    Write-Host "   ❌ mDNS: No disponible" -ForegroundColor Red
}
$sw.Stop()
$results.mDNS.Time = $sw.ElapsedMilliseconds

# ============================================
# MÉTODO 2: Consultar Supabase
# ============================================
Write-Host "[2/3] Consultando Supabase por IP de $DeviceId..." -ForegroundColor White
$sw = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $headers = @{
        "apikey" = $SupabaseKey
        "Authorization" = "Bearer $SupabaseKey"
    }
    $url = "$SupabaseUrl/rest/v1/device_status?device_id=eq.$DeviceId&select=local_ip,device_id,name"
    $response = Invoke-RestMethod -Uri $url -Headers $headers -TimeoutSec 5 -ErrorAction Stop
    
    if ($response -and $response.Count -gt 0 -and $response[0].local_ip) {
        $supabaseIp = $response[0].local_ip
        Write-Host "   📡 Supabase dice IP: $supabaseIp" -ForegroundColor Yellow
        
        # Verificar que la IP responde
        if (Test-ReeferIP -IP $supabaseIp) {
            $results.Supabase.Status = "ENCONTRADO"
            $results.Supabase.IP = $supabaseIp
            if (-not $foundIp) { $foundIp = $supabaseIp }
            Write-Host "   ✅ Supabase: VERIFICADO en $supabaseIp" -ForegroundColor Green
        } else {
            $results.Supabase.Status = "IP no responde"
            $results.Supabase.IP = "$supabaseIp (no responde)"
            Write-Host "   ⚠️ Supabase: IP $supabaseIp no responde" -ForegroundColor Yellow
        }
    } else {
        $results.Supabase.Status = "Dispositivo no encontrado"
        Write-Host "   ❌ Supabase: Dispositivo $DeviceId no encontrado" -ForegroundColor Red
    }
} catch {
    $results.Supabase.Status = "Error: $($_.Exception.Message)"
    Write-Host "   ❌ Supabase: Error - $($_.Exception.Message)" -ForegroundColor Red
}
$sw.Stop()
$results.Supabase.Time = $sw.ElapsedMilliseconds

# ============================================
# MÉTODO 3: Escaneo de red
# ============================================
Write-Host "[3/3] Escaneando red local..." -ForegroundColor White
$sw = [System.Diagnostics.Stopwatch]::StartNew()

# IPs específicas de Santa Cruz + comunes
$ipsToScan = @(
    # Santa Cruz específicas
    "192.168.225.200", "192.168.226.180",
    # Rangos Santa Cruz (192.168.224-227.x)
    "192.168.224.1", "192.168.224.100", "192.168.224.200",
    "192.168.225.1", "192.168.225.100", "192.168.225.150",
    "192.168.226.1", "192.168.226.100", "192.168.226.200",
    "192.168.227.1", "192.168.227.100", "192.168.227.200",
    # IPs comunes
    "192.168.1.100", "192.168.1.101", "192.168.0.100", "192.168.0.101",
    "192.168.4.1", "10.0.0.100"
)

$scanFound = $false
$testedCount = 0

# Escanear en paralelo usando jobs
$jobs = @()
foreach ($ip in $ipsToScan) {
    $jobs += Start-Job -ScriptBlock {
        param($testIp)
        try {
            $response = Invoke-WebRequest -Uri "http://$testIp/api/status" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($response.StatusCode -eq 200 -and $response.Content -match "sensor") {
                return $testIp
            }
        } catch { }
        return $null
    } -ArgumentList $ip
}

Write-Host "   Escaneando $($ipsToScan.Count) IPs en paralelo..." -ForegroundColor Gray

# Esperar resultados (máximo 10 segundos)
$timeout = 10
$completed = Wait-Job -Job $jobs -Timeout $timeout

foreach ($job in $jobs) {
    $result = Receive-Job -Job $job -ErrorAction SilentlyContinue
    if ($result) {
        $results.Scan.Status = "ENCONTRADO"
        $results.Scan.IP = $result
        if (-not $foundIp) { $foundIp = $result }
        $scanFound = $true
        Write-Host "   ✅ Scan: ENCONTRADO en $result" -ForegroundColor Green
        break
    }
}

# Limpiar jobs
$jobs | Remove-Job -Force -ErrorAction SilentlyContinue

if (-not $scanFound) {
    $results.Scan.Status = "No encontrado"
    Write-Host "   ❌ Scan: No encontrado en IPs conocidas" -ForegroundColor Red
}

$sw.Stop()
$results.Scan.Time = $sw.ElapsedMilliseconds

# ============================================
# RESUMEN
# ============================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   RESUMEN DE RESULTADOS" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Método        | Estado              | IP                  | Tiempo" -ForegroundColor White
Write-Host "------------- | ------------------- | ------------------- | ------" -ForegroundColor Gray

$color = if ($results.mDNS.Status -eq "ENCONTRADO") { "Green" } else { "Red" }
Write-Host ("mDNS          | {0,-19} | {1,-19} | {2}ms" -f $results.mDNS.Status, $results.mDNS.IP, $results.mDNS.Time) -ForegroundColor $color

$color = if ($results.Supabase.Status -eq "ENCONTRADO") { "Green" } elseif ($results.Supabase.Status -match "no responde") { "Yellow" } else { "Red" }
Write-Host ("Supabase      | {0,-19} | {1,-19} | {2}ms" -f $results.Supabase.Status, $results.Supabase.IP, $results.Supabase.Time) -ForegroundColor $color

$color = if ($results.Scan.Status -eq "ENCONTRADO") { "Green" } else { "Red" }
Write-Host ("Scan Red      | {0,-19} | {1,-19} | {2}ms" -f $results.Scan.Status, $results.Scan.IP, $results.Scan.Time) -ForegroundColor $color

Write-Host ""
if ($foundIp) {
    Write-Host "🎉 REEFER ENCONTRADO EN: $foundIp" -ForegroundColor Green
    Write-Host ""
    Write-Host "Probando conexión completa..." -ForegroundColor Yellow
    try {
        $status = Invoke-RestMethod -Uri "http://$foundIp/api/status" -TimeoutSec 5
        Write-Host ""
        Write-Host "📊 ESTADO DEL REEFER:" -ForegroundColor Cyan
        Write-Host "   Temperatura: $($status.sensor.temp_avg)°C" -ForegroundColor White
        Write-Host "   Temp1: $($status.sensor.temp1)°C | Temp2: $($status.sensor.temp2)°C" -ForegroundColor Gray
        Write-Host "   Puerta: $(if ($status.sensor.door_open) { 'ABIERTA' } else { 'Cerrada' })" -ForegroundColor $(if ($status.sensor.door_open) { 'Yellow' } else { 'Green' })
        Write-Host "   Alerta: $(if ($status.system.alert_active) { 'ACTIVA' } else { 'Inactiva' })" -ForegroundColor $(if ($status.system.alert_active) { 'Red' } else { 'Green' })
        Write-Host "   Relé: $(if ($status.system.relay_on) { 'ENCENDIDO' } else { 'Apagado' })" -ForegroundColor White
        Write-Host "   Uptime: $([math]::Round($status.system.uptime_sec / 3600, 1)) horas" -ForegroundColor Gray
    } catch {
        Write-Host "   Error obteniendo estado: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "❌ NO SE ENCONTRÓ EL REEFER" -ForegroundColor Red
    Write-Host ""
    Write-Host "Posibles causas:" -ForegroundColor Yellow
    Write-Host "   - El ESP32 no está encendido" -ForegroundColor Gray
    Write-Host "   - El ESP32 no está conectado a la misma red" -ForegroundColor Gray
    Write-Host "   - La IP del ESP32 está fuera de los rangos escaneados" -ForegroundColor Gray
    Write-Host "   - Firewall bloqueando conexiones" -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Presiona cualquier tecla para salir..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
