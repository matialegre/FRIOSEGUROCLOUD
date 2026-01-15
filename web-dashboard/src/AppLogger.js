// Sistema de logs centralizado para el dashboard web
const MAX_LOGS = 500
let logs = []
let listeners = []

export const LogLevel = {
  INFO: 'INFO',
  SUCCESS: 'SUCCESS',
  WARNING: 'WARNING',
  ERROR: 'ERROR',
  NETWORK: 'NETWORK',
  ALERT: 'ALERT',
  COMMAND: 'COMMAND',
  RESPONSE: 'RESPONSE'
}

const getIcon = (level) => {
  switch(level) {
    case LogLevel.INFO: return 'ℹ️'
    case LogLevel.SUCCESS: return '✅'
    case LogLevel.WARNING: return '⚠️'
    case LogLevel.ERROR: return '❌'
    case LogLevel.NETWORK: return '🌐'
    case LogLevel.ALERT: return '🚨'
    case LogLevel.COMMAND: return '📤'
    case LogLevel.RESPONSE: return '📥'
    default: return '•'
  }
}

const getColor = (level) => {
  switch(level) {
    case LogLevel.SUCCESS: return '#22c55e'
    case LogLevel.WARNING: return '#f59e0b'
    case LogLevel.ERROR: return '#ef4444'
    case LogLevel.ALERT: return '#ef4444'
    case LogLevel.NETWORK: return '#3b82f6'
    case LogLevel.COMMAND: return '#8b5cf6'
    case LogLevel.RESPONSE: return '#06b6d4'
    default: return '#ffffff'
  }
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('es-AR', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit',
    fractionalSecondDigits: 3
  })
}

const addLog = (level, tag, message, details = null) => {
  const entry = {
    id: Date.now() + Math.random(),
    timestamp: Date.now(),
    level,
    tag,
    message,
    details,
    icon: getIcon(level),
    color: getColor(level),
    formattedTime: formatTime(Date.now())
  }
  
  logs.push(entry)
  
  // Limitar tamaño
  while (logs.length > MAX_LOGS) {
    logs.shift()
  }
  
  // Notificar listeners
  listeners.forEach(listener => listener(entry))
  
  // También loguear en consola
  const consoleMethod = level === LogLevel.ERROR ? 'error' : 
                        level === LogLevel.WARNING ? 'warn' : 'log'
  console[consoleMethod](`[${tag}] ${message}`, details || '')
  
  return entry
}

// API pública
export const AppLogger = {
  getLogs: () => [...logs],
  
  clear: () => {
    logs = []
    listeners.forEach(listener => listener(null))
  },
  
  addListener: (listener) => {
    listeners.push(listener)
    return () => {
      listeners = listeners.filter(l => l !== listener)
    }
  },
  
  // Métodos de logging
  info: (tag, message, details) => addLog(LogLevel.INFO, tag, message, details),
  success: (tag, message, details) => addLog(LogLevel.SUCCESS, tag, message, details),
  warning: (tag, message, details) => addLog(LogLevel.WARNING, tag, message, details),
  error: (tag, message, details) => addLog(LogLevel.ERROR, tag, message, details),
  network: (tag, message, details) => addLog(LogLevel.NETWORK, tag, message, details),
  alert: (tag, message, details) => addLog(LogLevel.ALERT, tag, message, details),
  command: (tag, message, details) => addLog(LogLevel.COMMAND, tag, message, details),
  response: (tag, message, details) => addLog(LogLevel.RESPONSE, tag, message, details),
  
  // Métodos específicos para Supabase
  supabaseRequest: (endpoint, method, body) => {
    addLog(LogLevel.COMMAND, 'SUPABASE', `${method} ${endpoint}`, body)
  },
  
  supabaseResponse: (endpoint, status, response) => {
    if (status >= 200 && status < 300) {
      addLog(LogLevel.RESPONSE, 'SUPABASE', `✓ ${endpoint} -> ${status}`, response?.substring(0, 200))
    } else {
      addLog(LogLevel.ERROR, 'SUPABASE', `✗ ${endpoint} -> ${status}`, response)
    }
  },
  
  supabaseError: (endpoint, error) => {
    addLog(LogLevel.ERROR, 'SUPABASE', `✗ ${endpoint} FAILED`, error)
  },
  
  // Métodos específicos para alertas
  alertDetected: (deviceId, temp, message) => {
    addLog(LogLevel.ALERT, 'ALERT', `🚨 ALERTA DETECTADA: ${deviceId}`, `Temp: ${temp}°C - ${message}`)
  },
  
  alertSilenced: (deviceId, source) => {
    addLog(LogLevel.SUCCESS, 'ALERT', `🔕 ALERTA SILENCIADA: ${deviceId}`, `Origen: ${source}`)
  },
  
  silenceCommandSent: (deviceId) => {
    addLog(LogLevel.COMMAND, 'SILENCE', '📤 Enviando comando SILENCE', `Device: ${deviceId}`)
  },
  
  silenceCommandConfirmed: (deviceId) => {
    addLog(LogLevel.SUCCESS, 'SILENCE', '✓ Comando SILENCE confirmado', `Device: ${deviceId}`)
  },
  
  silenceCommandFailed: (deviceId, error) => {
    addLog(LogLevel.ERROR, 'SILENCE', '✗ Comando SILENCE falló', `Device: ${deviceId} - ${error}`)
  },
  
  // Polling
  pollingResult: (devicesCount, hasAlert) => {
    const status = hasAlert ? '⚠️ CON ALERTA' : '✅ Sin alertas'
    addLog(LogLevel.INFO, 'POLLING', `📊 ${devicesCount} dispositivo(s) - ${status}`)
  }
}

export default AppLogger
