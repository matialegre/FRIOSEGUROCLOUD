import { createClient } from '@supabase/supabase-js'

const supabaseUrl = 'https://xhdeacnwdzvkivfjzard.supabase.co'
const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhoZGVhY253ZHp2a2l2Zmp6YXJkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzYwMTY0NzAsImV4cCI6MjA1MTU5MjQ3MH0.sG5Ki3Vfnp0xPE6UcqbsurPJrWlXOqXkGpCqrVtFz3M'

export const supabase = createClient(supabaseUrl, supabaseKey)

// Obtener todos los dispositivos con su última lectura
export async function getDevicesWithReadings() {
  try {
    // Obtener dispositivos
    const { data: devices, error: devicesError } = await supabase
      .from('devices')
      .select('*')
      .order('device_id')

    if (devicesError) throw devicesError

    // Para cada dispositivo, obtener la última lectura
    const devicesWithReadings = await Promise.all(
      devices.map(async (device) => {
        const { data: readings } = await supabase
          .from('readings')
          .select('*')
          .eq('device_id', device.device_id)
          .order('created_at', { ascending: false })
          .limit(1)

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
            // Nuevos campos para estados del sistema
            systemState: reading.system_state || 'NORMAL',
            cooldownRemainingSec: reading.cooldown_remaining_sec || 0,
            configLoadRemainingSec: reading.config_load_remaining_sec || 0,
            createdAt: reading.created_at
          } : null
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
    const { data, error } = await supabase
      .from('devices')
      .select('temp_critical, alert_delay_sec, defrost_cooldown_sec, door_open_max_sec')
      .eq('device_id', deviceId)
      .single()

    if (error) throw error

    return {
      tempCritical: data.temp_critical,
      alertDelaySec: data.alert_delay_sec,
      defrostCooldownSec: data.defrost_cooldown_sec,
      doorOpenMaxSec: data.door_open_max_sec
    }
  } catch (error) {
    console.error('Error fetching config:', error)
    return null
  }
}

// Actualizar configuración de un dispositivo
export async function updateDeviceConfig(deviceId, config) {
  try {
    const { error } = await supabase
      .from('devices')
      .update({
        temp_critical: config.temp_critical,
        alert_delay_sec: config.alert_delay_sec,
        defrost_cooldown_sec: config.defrost_cooldown_sec,
        door_open_max_sec: config.door_open_max_sec
      })
      .eq('device_id', deviceId)

    if (error) throw error
    return true
  } catch (error) {
    console.error('Error updating config:', error)
    return false
  }
}

// Obtener alertas activas
export async function getActiveAlerts() {
  try {
    const { data, error } = await supabase
      .from('alerts')
      .select('*')
      .eq('resolved', false)
      .order('created_at', { ascending: false })

    if (error) throw error
    return data || []
  } catch (error) {
    console.error('Error fetching alerts:', error)
    return []
  }
}

// Reconocer alerta
export async function acknowledgeAlert(alertId) {
  try {
    const { error } = await supabase
      .from('alerts')
      .update({ acknowledged: true, acknowledged_at: new Date().toISOString() })
      .eq('id', alertId)

    if (error) throw error
    return true
  } catch (error) {
    console.error('Error acknowledging alert:', error)
    return false
  }
}

// Resolver alerta
export async function resolveAlert(alertId) {
  try {
    const { error } = await supabase
      .from('alerts')
      .update({ resolved: true, resolved_at: new Date().toISOString() })
      .eq('id', alertId)

    if (error) throw error
    return true
  } catch (error) {
    console.error('Error resolving alert:', error)
    return false
  }
}
