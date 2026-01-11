import { useState, useEffect } from 'react'
import { getDevicesWithReadings, getDeviceConfig, updateDeviceConfig, getActiveAlerts, acknowledgeAlert, resolveAlert } from './supabaseClient'
import './App.css'

function App() {
  const [devices, setDevices] = useState([])
  const [selectedDevice, setSelectedDevice] = useState(null)
  const [config, setConfig] = useState(null)
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [showConfig, setShowConfig] = useState(false)
  const [editConfig, setEditConfig] = useState({})
  const [lastUpdate, setLastUpdate] = useState(null)

  // Cargar datos
  const loadData = async () => {
    try {
      const devicesData = await getDevicesWithReadings()
      setDevices(devicesData)
      
      if (!selectedDevice && devicesData.length > 0) {
        setSelectedDevice(devicesData[0])
      } else if (selectedDevice) {
        const updated = devicesData.find(d => d.device_id === selectedDevice.device_id)
        if (updated) setSelectedDevice(updated)
      }
      
      const alertsData = await getActiveAlerts()
      setAlerts(alertsData)
      
      setLastUpdate(new Date())
      setLoading(false)
    } catch (e) {
      console.error('Error loading data:', e)
      setLoading(false)
    }
  }

  // Cargar configuración del dispositivo seleccionado
  const loadConfig = async () => {
    if (!selectedDevice) return
    const cfg = await getDeviceConfig(selectedDevice.device_id)
    setConfig(cfg)
    setEditConfig({
      temp_critical: cfg?.tempCritical || -10,
      alert_delay_sec: (cfg?.alertDelaySec || 300) / 60,
      defrost_cooldown_sec: (cfg?.defrostCooldownSec || 1800) / 60,
      door_open_max_sec: cfg?.doorOpenMaxSec || 120
    })
  }

  // Auto-refresh cada 5 segundos
  useEffect(() => {
    loadData()
    const interval = setInterval(loadData, 5000)
    return () => clearInterval(interval)
  }, [])

  // Cargar config cuando cambia el dispositivo
  useEffect(() => {
    if (selectedDevice) loadConfig()
  }, [selectedDevice?.device_id])

  // Guardar configuración
  const saveConfig = async () => {
    if (!selectedDevice) return
    
    const success = await updateDeviceConfig(selectedDevice.device_id, {
      temp_critical: parseFloat(editConfig.temp_critical),
      alert_delay_sec: parseInt(editConfig.alert_delay_sec) * 60,
      defrost_cooldown_sec: parseInt(editConfig.defrost_cooldown_sec) * 60,
      door_open_max_sec: parseInt(editConfig.door_open_max_sec)
    })
    
    if (success) {
      alert('✅ Configuración guardada')
      setShowConfig(false)
      loadConfig()
    } else {
      alert('❌ Error al guardar')
    }
  }

  // Formatear temperatura con color
  const getTempColor = (temp) => {
    if (temp === null || temp === undefined || temp < -55) return '#888'
    if (temp > 0) return '#ef4444'
    if (temp > -10) return '#f97316'
    if (temp > -25) return '#22c55e'
    return '#3b82f6'
  }

  // Formatear uptime
  const formatUptime = (seconds) => {
    if (!seconds) return '--:--:--'
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = seconds % 60
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }

  if (loading) {
    return (
      <div className="app loading">
        <div className="loading-content">
          <div className="loading-logo">❄️</div>
          <h1 className="loading-title">FrioSeguro</h1>
          <div className="spinner"></div>
          <p className="loading-text">Conectando a Supabase...</p>
          <div className="loading-brands">
            <div className="brand">
              <span className="brand-icon">🏔️</span>
              <span className="brand-name">Pan American Silver</span>
            </div>
            <div className="brand-separator">×</div>
            <div className="brand">
              <span className="brand-icon">⚡</span>
              <span className="brand-name">Pandemonium Tech</span>
            </div>
          </div>
        </div>
      </div>
    )
  }

  const reading = selectedDevice?.reading

  return (
    <div className="app">
      {/* Header */}
      <header className="header">
        <div className="header-left">
          <span className="logo">❄️</span>
          <div>
            <h1>FrioSeguro</h1>
            <p className="subtitle">☁️ Dashboard Cloud</p>
          </div>
        </div>
        <div className="header-right">
          <span className="update-time">
            {lastUpdate ? `Actualizado: ${lastUpdate.toLocaleTimeString()}` : ''}
          </span>
        </div>
      </header>

      {/* Selector de dispositivo */}
      <div className="device-selector">
        {devices.map(device => (
          <button
            key={device.device_id}
            className={`device-btn ${selectedDevice?.device_id === device.device_id ? 'active' : ''} ${device.is_online ? 'online' : 'offline'}`}
            onClick={() => setSelectedDevice(device)}
          >
            <span className="status-dot"></span>
            {device.name || device.device_id}
          </button>
        ))}
      </div>

      {/* Panel principal */}
      {selectedDevice && (
        <div className="main-panel">
          {/* Temperatura grande */}
          <div className="temp-card">
            <span className="temp-label">TEMPERATURA</span>
            <span 
              className="temp-value" 
              style={{ color: getTempColor(reading?.tempAvg) }}
            >
              {reading?.tempAvg !== null && reading?.tempAvg !== undefined && reading?.tempAvg > -55 
                ? `${reading.tempAvg.toFixed(1)}°C` 
                : '--.-°C'}
            </span>
            <div className="temp-sensors">
              <span>T1: {reading?.temp1?.toFixed(1) || '--'}°C</span>
              <span>T2: {reading?.temp2?.toFixed(1) || '--'}°C</span>
            </div>
          </div>

          {/* Estado */}
          <div className="status-grid">
            <div className={`status-card ${reading?.door1Open ? 'warning' : 'ok'}`}>
              <span className="status-icon">🚪</span>
              <span className="status-label">PUERTA</span>
              <span className="status-value">{reading?.door1Open ? 'ABIERTA' : 'Cerrada'}</span>
            </div>
            <div className={`status-card ${reading?.relayOn ? 'danger' : 'ok'}`}>
              <span className="status-icon">🔔</span>
              <span className="status-label">SIRENA</span>
              <span className="status-value">{reading?.relayOn ? 'ACTIVA' : 'Apagada'}</span>
            </div>
            <div className={`status-card ${reading?.alertActive ? 'danger' : 'ok'}`}>
              <span className="status-icon">⚠️</span>
              <span className="status-label">ALERTA</span>
              <span className="status-value">{reading?.alertActive ? 'ACTIVA' : 'Normal'}</span>
            </div>
            <div className={`status-card ${reading?.defrostMode ? 'info' : 'ok'}`}>
              <span className="status-icon">🧊</span>
              <span className="status-label">DEFROST</span>
              <span className="status-value">{reading?.defrostMode ? 'ACTIVO' : 'Inactivo'}</span>
            </div>
          </div>

          {/* Info de conexión */}
          <div className="connection-info">
            <div className="info-row">
              <span>📍 Ubicación:</span>
              <span>{selectedDevice.location || 'Sin ubicación'}</span>
            </div>
            <div className="info-row">
              <span>🆔 ID:</span>
              <span>{selectedDevice.device_id}</span>
            </div>
            <div className="info-row">
              <span>🌐 IP:</span>
              <span>{selectedDevice.ip_address || 'Sin IP'}</span>
            </div>
            <div className="info-row">
              <span>⏱️ Uptime:</span>
              <span>{formatUptime(reading?.uptimeSec)}</span>
            </div>
            <div className="info-row">
              <span>📶 Estado:</span>
              <span className={selectedDevice.is_online ? 'online-text' : 'offline-text'}>
                {selectedDevice.is_online ? '🟢 Online' : '🔴 Offline'}
              </span>
            </div>
          </div>

          {/* Configuración */}
          <div className="config-section">
            <h3>📋 Valores Configurados</h3>
            <div className="config-grid">
              <div className="config-item">
                <span className="config-icon">🌡️</span>
                <span>Temp. Crítica: {config?.tempCritical || '--'}°C</span>
              </div>
              <div className="config-item">
                <span className="config-icon">⏱️</span>
                <span>Tiempo espera: {config ? Math.round(config.alertDelaySec / 60) : '--'} min</span>
              </div>
              <div className="config-item">
                <span className="config-icon">🧊</span>
                <span>Post-defrost: {config ? Math.round(config.defrostCooldownSec / 60) : '--'} min</span>
              </div>
              <div className="config-item">
                <span className="config-icon">🚪</span>
                <span>Puerta máx: {config?.doorOpenMaxSec || '--'} seg</span>
              </div>
            </div>
            <button className="btn-config" onClick={() => setShowConfig(true)}>
              ⚙️ Editar Configuración
            </button>
          </div>
        </div>
      )}

      {/* Lista de todos los Reefers */}
      <div className="reefers-list">
        <h3>📦 Todos los Reefers</h3>
        <div className="reefers-grid">
          {devices.map(device => (
            <div 
              key={device.device_id} 
              className={`reefer-card ${device.is_online ? 'online' : 'offline'} ${selectedDevice?.device_id === device.device_id ? 'selected' : ''}`}
              onClick={() => setSelectedDevice(device)}
            >
              <div className="reefer-header">
                <span className={`reefer-status ${device.is_online ? 'online' : 'offline'}`}></span>
                <span className="reefer-name">{device.name || device.device_id}</span>
              </div>
              <div className="reefer-temp" style={{ color: getTempColor(device.reading?.tempAvg) }}>
                {device.reading?.tempAvg !== null && device.reading?.tempAvg !== undefined && device.reading?.tempAvg > -55
                  ? `${device.reading.tempAvg.toFixed(1)}°C`
                  : '--.-°C'}
              </div>
              <div className="reefer-location">{device.location || 'Sin ubicación'}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Alertas activas */}
      {alerts.length > 0 && (
        <div className="alerts-section">
          <h3>🚨 Alertas Activas ({alerts.length})</h3>
          <div className="alerts-list">
            {alerts.map(alert => (
              <div key={alert.id} className={`alert-card ${alert.severity}`}>
                <div className="alert-header">
                  <span className="alert-device">{alert.device_id}</span>
                  <span className="alert-type">{alert.alert_type}</span>
                </div>
                <p className="alert-message">{alert.message}</p>
                <div className="alert-actions">
                  {!alert.acknowledged && (
                    <button onClick={() => acknowledgeAlert(alert.id).then(loadData)}>
                      ✓ Reconocer
                    </button>
                  )}
                  <button onClick={() => resolveAlert(alert.id).then(loadData)}>
                    ✓ Resolver
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Modal de configuración */}
      {showConfig && (
        <div className="modal-overlay" onClick={() => setShowConfig(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>⚙️ Configuración - {selectedDevice?.name}</h2>
            
            <div className="form-group">
              <label>🌡️ Temperatura Crítica (°C)</label>
              <input
                type="number"
                step="0.1"
                value={editConfig.temp_critical}
                onChange={e => setEditConfig({...editConfig, temp_critical: e.target.value})}
              />
              <small>Si la temperatura supera este valor, se activa la alarma</small>
            </div>

            <div className="form-group">
              <label>⏱️ Tiempo de espera (minutos)</label>
              <input
                type="number"
                value={editConfig.alert_delay_sec}
                onChange={e => setEditConfig({...editConfig, alert_delay_sec: e.target.value})}
              />
              <small>Minutos que debe mantenerse la temperatura alta antes de alertar</small>
            </div>

            <div className="form-group">
              <label>🧊 Tiempo post-descongelación (minutos)</label>
              <input
                type="number"
                value={editConfig.defrost_cooldown_sec}
                onChange={e => setEditConfig({...editConfig, defrost_cooldown_sec: e.target.value})}
              />
              <small>Tiempo de espera después de descongelación</small>
            </div>

            <div className="form-group">
              <label>🚪 Tiempo máx. puerta abierta (segundos)</label>
              <input
                type="number"
                value={editConfig.door_open_max_sec}
                onChange={e => setEditConfig({...editConfig, door_open_max_sec: e.target.value})}
              />
              <small>Tiempo máximo que la puerta puede estar abierta</small>
            </div>

            <div className="modal-actions">
              <button className="btn-cancel" onClick={() => setShowConfig(false)}>Cancelar</button>
              <button className="btn-save" onClick={saveConfig}>💾 Guardar</button>
            </div>
          </div>
        </div>
      )}

      {/* Footer */}
      <footer className="footer">
        <div className="footer-brands">
          <span>🏔️ Pan American Silver</span>
          <span className="footer-separator">•</span>
          <span>⚡ Pandemonium Tech</span>
        </div>
        <p>FrioSeguro © 2026 - Sistema de Monitoreo de Reefers</p>
      </footer>
    </div>
  )
}

export default App
