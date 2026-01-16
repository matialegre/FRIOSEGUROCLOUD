package com.parametican.alertarift

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sistema de logs centralizado para debugging
 * Guarda todos los eventos importantes de la app
 */
object AppLogger {
    
    private const val MAX_LOGS = 500
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val details: String? = null
    ) {
        fun getFormattedTime(): String = dateFormat.format(Date(timestamp))
        
        fun getIcon(): String = when(level) {
            LogLevel.INFO -> "ℹ️"
            LogLevel.SUCCESS -> "✅"
            LogLevel.WARNING -> "⚠️"
            LogLevel.ERROR -> "❌"
            LogLevel.NETWORK -> "🌐"
            LogLevel.ALERT -> "🚨"
            LogLevel.COMMAND -> "📤"
            LogLevel.RESPONSE -> "📥"
        }
    }
    
    enum class LogLevel {
        INFO, SUCCESS, WARNING, ERROR, NETWORK, ALERT, COMMAND, RESPONSE
    }
    
    // Listeners para actualizar UI en tiempo real
    private val listeners = mutableListOf<(LogEntry) -> Unit>()
    
    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }
    
    private fun addLog(level: LogLevel, tag: String, message: String, details: String? = null) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, details)
        logs.add(entry)
        
        // Limitar tamaño
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        
        // Notificar listeners
        listeners.forEach { it(entry) }
        
        // También loguear en Logcat
        when(level) {
            LogLevel.ERROR -> Log.e(tag, "$message ${details ?: ""}")
            LogLevel.WARNING -> Log.w(tag, "$message ${details ?: ""}")
            else -> Log.d(tag, "$message ${details ?: ""}")
        }
    }
    
    fun getLogs(): List<LogEntry> = logs.toList()
    
    fun getLogsFiltered(level: LogLevel? = null, tag: String? = null): List<LogEntry> {
        return logs.filter { entry ->
            (level == null || entry.level == level) &&
            (tag == null || entry.tag == tag)
        }
    }
    
    fun clear() {
        logs.clear()
    }
    
    // ============================================
    // MÉTODOS DE LOGGING POR CATEGORÍA
    // ============================================
    
    fun info(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.INFO, tag, message, details)
    }
    
    fun success(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.SUCCESS, tag, message, details)
    }
    
    fun warning(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.WARNING, tag, message, details)
    }
    
    fun error(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.ERROR, tag, message, details)
    }
    
    fun network(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.NETWORK, tag, message, details)
    }
    
    fun alert(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.ALERT, tag, message, details)
    }
    
    fun command(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.COMMAND, tag, message, details)
    }
    
    fun response(tag: String, message: String, details: String? = null) {
        addLog(LogLevel.RESPONSE, tag, message, details)
    }
    
    // ============================================
    // MÉTODOS ESPECÍFICOS PARA SUPABASE
    // ============================================
    
    fun supabaseRequest(endpoint: String, method: String, body: String? = null) {
        command("SUPABASE", "$method $endpoint", body)
    }
    
    fun supabaseResponse(endpoint: String, code: Int, response: String? = null) {
        if (code in 200..299) {
            response("SUPABASE", "✓ $endpoint -> $code", response?.take(200))
        } else {
            error("SUPABASE", "✗ $endpoint -> $code", response)
        }
    }
    
    fun supabaseError(endpoint: String, error: String) {
        error("SUPABASE", "✗ $endpoint FAILED", error)
    }
    
    // ============================================
    // MÉTODOS ESPECÍFICOS PARA ALERTAS
    // ============================================
    
    fun alertDetected(deviceId: String, temp: Float, message: String) {
        alert("ALERT", "🚨 ALERTA DETECTADA: $deviceId", "Temp: $temp°C - $message")
    }
    
    fun alertSilenced(deviceId: String, source: String) {
        success("ALERT", "🔕 ALERTA SILENCIADA: $deviceId", "Origen: $source")
    }
    
    fun alertResolved(deviceId: String) {
        success("ALERT", "✅ ALERTA RESUELTA: $deviceId", null)
    }
    
    fun silenceCommandSent(deviceId: String) {
        command("SILENCE", "📤 Enviando comando SILENCE", "Device: $deviceId")
    }
    
    fun silenceCommandConfirmed(deviceId: String) {
        success("SILENCE", "✓ Comando SILENCE confirmado", "Device: $deviceId")
    }
    
    fun silenceCommandFailed(deviceId: String, error: String) {
        error("SILENCE", "✗ Comando SILENCE falló", "Device: $deviceId - $error")
    }
    
    // ============================================
    // MÉTODOS ESPECÍFICOS PARA POLLING
    // ============================================
    
    fun pollingStarted() {
        info("POLLING", "▶️ Polling iniciado", "Intervalo: 5 segundos")
    }
    
    fun pollingResult(devicesCount: Int, hasAlert: Boolean) {
        val status = if (hasAlert) "⚠️ CON ALERTA" else "✅ Sin alertas"
        info("POLLING", "📊 $devicesCount dispositivo(s) - $status", null)
    }
    
    fun pollingError(error: String) {
        error("POLLING", "✗ Error en polling", error)
    }
}
