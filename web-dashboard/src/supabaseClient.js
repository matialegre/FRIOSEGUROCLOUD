// Cliente Supabase usando fetch directo (sin SDK)
const SUPABASE_URL = 'https://xhdeacnwdzvkivfjzard.supabase.co'
const SUPABASE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhoZGVhY253ZHp2a2l2Zmp6YXJkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzYwMTY0NzAsImV4cCI6MjA1MTU5MjQ3MH0.sG5Ki3Vfnp0xPE6UcqbsurPJrWlXOqXkGpCqrVtFz3M'

// Helper para hacer requests a Supabase REST API
async function supabaseGet(endpoint) {
  const response = await fetch(`${SUPABASE_URL}/rest/v1/${endpoint}`, {
    headers: {
      'apikey': SUPABASE_KEY,
      'Authorization': `Bearer ${SUPABASE_KEY}`,
      'Content-Type': 'application/json'
    }
  })
  if (!response.ok) {
    const error = await response.json()
    throw error
  }
  return response.json()
}

async function supabasePatch(endpoint, body) {
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
    throw error
  }
  return response.json()
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
