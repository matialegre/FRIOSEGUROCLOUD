// Cliente Supabase usando fetch directo (sin SDK)
import { AppLogger } from './AppLogger'

const SUPABASE_URL = 'https://xhdeacnwdzvkivfjzard.supabase.co'
const SUPABASE_KEY = 'sb_publishable_JhTUv1X2LHMBVILUaysJ3g_Ho11zu-Q'

// Helper para hacer requests a Supabase REST API
async function supabaseGet(endpoint) {
  AppLogger.supabaseRequest(endpoint, 'GET')
  const response = await fetch(`${SUPABASE_URL}/rest/v1/${endpoint}`, {
    headers: {
      'apikey': SUPABASE_KEY,
      'Authorization': `Bearer ${SUPABASE_KEY}`,
      'Content-Type': 'application/json'
    }
  })
  if (!response.ok) {
    const error = await response.json()
    AppLogger.supabaseResponse(endpoint, response.status, JSON.stringify(error))
    throw error
  }
  const data = await response.json()
  AppLogger.supabaseResponse(endpoint, response.status, JSON.stringify(data).substring(0, 100))
  return data
}

async function supabasePatch(endpoint, body) {
  AppLogger.supabaseRequest(endpoint, 'PATCH', JSON.stringify(body))
  const response = await fetch(`${SUPABASE_URL}/rest/v1/${endpoint}`, {
    method: 'PATCH',
    headers: {
      'apikey': SUPABASE_KEY,
      'Authorization': `Bearer ${SUPABASE_KEY}`,
      'Content-Type': 'application/json',
      'Prefer': 'return=representation'
    },
    body: JSON.stringify(body)
  })
  if (!response.ok) {
    const error = await response.json()
    AppLogger.supabaseResponse(endpoint, response.status, JSON.stringify(error))
    throw error
  }
  const data = await response.json()
  AppLogger.supabaseResponse(endpoint, response.status, 'OK')
  return data
}

// Obtener todos los dispositivos con su última lectura
export async function getDevicesWithReadings() {
  try {
    // Obtener dispositivos
    const devices = await supabaseGet('devices?select=*&order=device_id.asc')

    // Para cada dispositivo, obtener la última lectura
    const devicesWithReadings = await Promise.all(
      devices.map(async (device) => {
        try {
          const readings = await supabaseGet(`readings?device_id=eq.${device.device_id}&order=created_at.desc&limit=1`)
          const reading = readings?.[0]
          
          return {
            ...device,
            reading: reading ? {
              tempAvg: reading.temp_avg,
              temp1: reading.temp1,
              temp2: reading.temp2,
              humidity: reading.humidity,
              door1Open: reading.door1_open,
              relayOn: reading.relay_on,
              alertActive: reading.alert_active,
              alertAcknowledged: reading.alert_acknowledged || false,
              tempOverCritical: reading.temp_over_critical || false,
              highTempElapsedSec: reading.high_temp_elapsed_sec || 0,
              defrostMode: reading.defrost_mode,
              uptimeSec: reading.uptime_sec,
              systemState: reading.system_state || 'NORMAL',
              cooldownRemainingSec: reading.cooldown_remaining_sec || 0,
              configLoadRemainingSec: reading.config_load_remaining_sec || 0,
              createdAt: reading.created_at
            } : null
          }
        } catch (e) {
          return { ...device, reading: null }
        }
      })
    )

    return devicesWithReadings
  } catch (error) {
    console.error('Error fetching devices:', error)
    return []
  }
}

// Obtener configuración de un dispositivo
export async function getDeviceConfig(deviceId) {
  try {
    const data = await supabaseGet(`devices?device_id=eq.${deviceId}&select=temp_critical,alert_delay_sec,defrost_cooldown_sec,door_open_max_sec&limit=1`)
    const device = data?.[0]
    if (!device) return null

    return {
      tempCritical: device.temp_critical,
      alertDelaySec: device.alert_delay_sec,
      defrostCooldownSec: device.defrost_cooldown_sec,
      doorOpenMaxSec: device.door_open_max_sec
    }
  } catch (error) {
    console.error('Error fetching config:', error)
    return null
  }
}

// Actualizar configuración de un dispositivo
export async function updateDeviceConfig(deviceId, config) {
  try {
    await supabasePatch(`devices?device_id=eq.${deviceId}`, {
      temp_critical: config.temp_critical,
      alert_delay_sec: config.alert_delay_sec,
      defrost_cooldown_sec: config.defrost_cooldown_sec,
      door_open_max_sec: config.door_open_max_sec
    })
    return true
  } catch (error) {
    console.error('Error updating config:', error)
    return false
  }
}

// Obtener alertas activas
export async function getActiveAlerts() {
  try {
    const data = await supabaseGet('alerts?select=*&resolved=eq.false&order=created_at.desc')
    return data || []
  } catch (error) {
    console.error('Error fetching alerts:', error)
    return []
  }
}

// Reconocer alerta
export async function acknowledgeAlert(alertId) {
  try {
    await supabasePatch(`alerts?id=eq.${alertId}`, { 
      acknowledged: true, 
      acknowledged_at: new Date().toISOString() 
    })
    return true
  } catch (error) {
    console.error('Error acknowledging alert:', error)
    return false
  }
}

// Resolver alerta
export async function resolveAlert(alertId) {
  try {
    await supabasePatch(`alerts?id=eq.${alertId}`, { 
      resolved: true, 
      resolved_at: new Date().toISOString() 
    })
    return true
  } catch (error) {
    console.error('Error resolving alert:', error)
    return false
  }
}

// Helper para POST
async function supabasePost(endpoint, body) {
  AppLogger.supabaseRequest(endpoint, 'POST', JSON.stringify(body))
  const response = await fetch(`${SUPABASE_URL}/rest/v1/${endpoint}`, {
    method: 'POST',
    headers: {
      'apikey': SUPABASE_KEY,
      'Authorization': `Bearer ${SUPABASE_KEY}`,
      'Content-Type': 'application/json',
      'Prefer': 'return=representation'
    },
    body: JSON.stringify(body)
  })
  if (!response.ok) {
    const error = await response.json()
    AppLogger.supabaseResponse(endpoint, response.status, JSON.stringify(error))
    throw error
  }
  const data = await response.json()
  AppLogger.supabaseResponse(endpoint, response.status, 'OK')
  return data
}

// Enviar comando SILENCE al ESP32 para silenciar sirena/relay
export async function sendSilenceCommand(deviceId) {
  try {
    // 1. Enviar comando SILENCE a la tabla commands
    await supabasePost('commands', {
      device_id: deviceId,
      command: 'SILENCE',
      status: 'pending',
      source: 'web_dashboard'
    })
    
    // 2. Marcar flag en devices para redundancia
    await supabasePatch(`devices?device_id=eq.${deviceId}`, {
      alert_acknowledged_remote: true
    })
    
    // 3. Marcar alert_acknowledged en la última lectura
    await supabasePatch(`readings?device_id=eq.${deviceId}&order=created_at.desc&limit=1`, {
      alert_acknowledged: true
    })
    
    console.log('✓ Comando SILENCE enviado a', deviceId)
    return true
  } catch (error) {
    console.error('Error sending silence command:', error)
    return false
  }
}

// Silenciar alerta completa (ESP32 + apps + base de datos)
export async function silenceDeviceAlert(deviceId) {
  try {
    // Enviar comando al ESP32
    await sendSilenceCommand(deviceId)
    
    // Marcar alertas activas del dispositivo como acknowledged
    const alerts = await getActiveAlerts()
    const deviceAlerts = alerts.filter(a => a.device_id === deviceId)
    
    for (const alert of deviceAlerts) {
      await acknowledgeAlert(alert.id)
    }
    
    return true
  } catch (error) {
    console.error('Error silencing device alert:', error)
    return false
  }
}
