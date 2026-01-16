package com.parametican.alertarift

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente Supabase para la app cloud
 * Maneja todas las operaciones con la base de datos remota
 */
object SupabaseClient {
    
    private const val SUPABASE_URL = "https://xhdeacnwdzvkivfjzard.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_JhTUv1X2LHMBVILUaysJ3g_Ho11zu-Q"
    
    data class DeviceStatus(
        val deviceId: String,
        val name: String,
        val location: String,
        val isOnline: Boolean,
        val lastSeenAt: String?,
        val wifiRssi: Int?,
        val tempAvg: Float?,
        val temp1: Float?,
        val temp2: Float?,
        val humidity: Float?,
        val door1Open: Boolean,
        val doorOpen: Boolean,  // Alias para compatibilidad
        val acPower: Boolean,
        val alertActive: Boolean,
        val alertAcknowledged: Boolean,  // Si la alerta fue silenciada
        val tempOverCritical: Boolean,   // Si temp > temp_critical
        val highTempElapsedSec: Int,     // Segundos que lleva sobre el crítico
        val defrostMode: Boolean,
        val cooldownMode: Boolean,
        val cooldownRemainingSec: Int,
        val relayOn: Boolean,
        val uptimeSec: Long?,
        val simulationMode: Boolean,
        val readingCreatedAt: String?  // Timestamp de la última lectura del ESP
    )
    
    data class Alert(
        val id: Long,
        val deviceId: String,
        val alertType: String,
        val severity: String,
        val message: String?,
        val temperature: Float?,
        val acknowledged: Boolean,
        val resolved: Boolean,
        val createdAt: String
    )
    
    data class DeviceConfig(
        val deviceId: String,
        val name: String,
        val location: String,
        val tempMax: Float,
        val tempCritical: Float,
        val alertDelaySec: Int,
        val doorOpenMaxSec: Int,
        val defrostCooldownSec: Int,
        val telegramEnabled: Boolean,
        val supabaseEnabled: Boolean
    )
    
    /**
     * Obtener todos los dispositivos con su estado actual
     */
    fun getDevicesStatus(): List<DeviceStatus> {
        val devices = mutableListOf<DeviceStatus>()
        
        try {
            // Obtener dispositivos
            val devicesJson = apiGet("/rest/v1/devices?select=*&order=device_id")
            val devicesArray = JSONArray(devicesJson)
            
            for (i in 0 until devicesArray.length()) {
                val device = devicesArray.getJSONObject(i)
                val deviceId = device.getString("device_id")
                
                // Obtener última lectura de este dispositivo
                val readingsJson = apiGet("/rest/v1/readings?device_id=eq.$deviceId&order=created_at.desc&limit=1")
                val readingsArray = JSONArray(readingsJson)
                
                val reading = if (readingsArray.length() > 0) readingsArray.getJSONObject(0) else null
                
                val door1OpenVal = reading?.optBoolean("door1_open", false) ?: false
                devices.add(DeviceStatus(
                    deviceId = deviceId,
                    name = device.optString("name", deviceId),
                    location = device.optString("location", ""),
                    isOnline = device.optBoolean("is_online", false),
                    lastSeenAt = device.optString("last_seen_at", null),
                    wifiRssi = if (device.has("wifi_rssi") && !device.isNull("wifi_rssi")) device.getInt("wifi_rssi") else null,
                    tempAvg = reading?.optDouble("temp_avg", -999.0)?.toFloat(),
                    temp1 = reading?.optDouble("temp1", -999.0)?.toFloat(),
                    temp2 = reading?.optDouble("temp2", -999.0)?.toFloat(),
                    humidity = reading?.optDouble("humidity", 0.0)?.toFloat(),
                    door1Open = door1OpenVal,
                    doorOpen = door1OpenVal,
                    acPower = reading?.optBoolean("ac_power", true) ?: true,
                    alertActive = reading?.optBoolean("alert_active", false) ?: false,
                    alertAcknowledged = reading?.optBoolean("alert_acknowledged", false) ?: false,
                    tempOverCritical = reading?.optBoolean("temp_over_critical", false) ?: false,
                    highTempElapsedSec = reading?.optInt("high_temp_elapsed_sec", 0) ?: 0,
                    defrostMode = reading?.optBoolean("defrost_mode", false) ?: false,
                    cooldownMode = reading?.optBoolean("cooldown_mode", false) ?: false,
                    cooldownRemainingSec = reading?.optInt("cooldown_remaining_sec", 0) ?: 0,
                    relayOn = reading?.optBoolean("relay_on", false) ?: false,
                    uptimeSec = reading?.optLong("uptime_sec", 0),
                    simulationMode = reading?.optBoolean("simulation_mode", false) ?: false,
                    readingCreatedAt = reading?.optString("created_at", null)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return devices
    }
    
    /**
     * Obtener estado de un dispositivo específico
     */
    fun getDeviceStatus(deviceId: String): DeviceStatus? {
        try {
            val deviceJson = apiGet("/rest/v1/devices?device_id=eq.$deviceId&limit=1")
            val deviceArray = JSONArray(deviceJson)
            if (deviceArray.length() == 0) return null
            
            val device = deviceArray.getJSONObject(0)
            
            val readingsJson = apiGet("/rest/v1/readings?device_id=eq.$deviceId&order=created_at.desc&limit=1")
            val readingsArray = JSONArray(readingsJson)
            val reading = if (readingsArray.length() > 0) readingsArray.getJSONObject(0) else null
            
            val door1OpenVal = reading?.optBoolean("door1_open", false) ?: false
            return DeviceStatus(
                deviceId = deviceId,
                name = device.optString("name", deviceId),
                location = device.optString("location", ""),
                isOnline = device.optBoolean("is_online", false),
                lastSeenAt = device.optString("last_seen_at", null),
                wifiRssi = if (device.has("wifi_rssi") && !device.isNull("wifi_rssi")) device.getInt("wifi_rssi") else null,
                tempAvg = reading?.optDouble("temp_avg", -999.0)?.toFloat(),
                temp1 = reading?.optDouble("temp1", -999.0)?.toFloat(),
                temp2 = reading?.optDouble("temp2", -999.0)?.toFloat(),
                humidity = reading?.optDouble("humidity", 0.0)?.toFloat(),
                door1Open = door1OpenVal,
                doorOpen = door1OpenVal,
                acPower = reading?.optBoolean("ac_power", true) ?: true,
                alertActive = reading?.optBoolean("alert_active", false) ?: false,
                alertAcknowledged = reading?.optBoolean("alert_acknowledged", false) ?: false,
                tempOverCritical = reading?.optBoolean("temp_over_critical", false) ?: false,
                highTempElapsedSec = reading?.optInt("high_temp_elapsed_sec", 0) ?: 0,
                defrostMode = reading?.optBoolean("defrost_mode", false) ?: false,
                cooldownMode = reading?.optBoolean("cooldown_mode", false) ?: false,
                cooldownRemainingSec = reading?.optInt("cooldown_remaining_sec", 0) ?: 0,
                relayOn = reading?.optBoolean("relay_on", false) ?: false,
                uptimeSec = reading?.optLong("uptime_sec", 0),
                simulationMode = reading?.optBoolean("simulation_mode", false) ?: false,
                readingCreatedAt = reading?.optString("created_at", null)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Obtener alertas activas
     */
    fun getActiveAlerts(): List<Alert> {
        val alerts = mutableListOf<Alert>()
        
        try {
            val json = apiGet("/rest/v1/alerts?resolved=eq.false&order=created_at.desc&limit=50")
            val array = JSONArray(json)
            
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                alerts.add(Alert(
                    id = obj.getLong("id"),
                    deviceId = obj.getString("device_id"),
                    alertType = obj.getString("alert_type"),
                    severity = obj.getString("severity"),
                    message = obj.optString("message", null),
                    temperature = obj.optDouble("temperature", -999.0).toFloat(),
                    acknowledged = obj.optBoolean("acknowledged", false),
                    resolved = obj.optBoolean("resolved", false),
                    createdAt = obj.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return alerts
    }
    
    /**
     * Reconocer una alerta
     */
    fun acknowledgeAlert(alertId: Long): Boolean {
        return try {
            AppLogger.info("SUPABASE", "Reconociendo alerta ID: $alertId")
            val body = JSONObject().apply {
                put("acknowledged", true)
                put("acknowledged_at", "now()")
                put("acknowledged_by", "app_cloud")
            }
            apiPatch("/rest/v1/alerts?id=eq.$alertId", body.toString())
            AppLogger.success("SUPABASE", "✓ Alerta $alertId reconocida")
            true
        } catch (e: Exception) {
            AppLogger.error("SUPABASE", "✗ Error reconociendo alerta $alertId", e.message)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Marcar alert_acknowledged en la última lectura del dispositivo
     */
    fun acknowledgeDeviceAlert(deviceId: String): Boolean {
        return try {
            AppLogger.info("SUPABASE", "Marcando alert_acknowledged para $deviceId")
            val body = JSONObject().apply {
                put("alert_acknowledged", true)
            }
            apiPatch("/rest/v1/readings?device_id=eq.$deviceId&order=created_at.desc&limit=1", body.toString())
            AppLogger.success("SUPABASE", "✓ alert_acknowledged=true en readings de $deviceId")
            true
        } catch (e: Exception) {
            AppLogger.error("SUPABASE", "✗ Error marcando acknowledge en readings", e.message)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Enviar comando SILENCE al ESP32 via tabla commands
     * El ESP32 lo lee cada 5 segundos y silencia su sirena/relay
     */
    fun sendSilenceCommand(deviceId: String): Boolean {
        AppLogger.silenceCommandSent(deviceId)
        return try {
            // 1. Enviar comando SILENCE a la tabla commands
            val cmdBody = JSONObject().apply {
                put("device_id", deviceId)
                put("command", "SILENCE")
                put("status", "pending")
                put("source", "app_cloud")
            }
            AppLogger.command("SILENCE", "POST /commands", cmdBody.toString())
            apiPost("/rest/v1/commands", cmdBody.toString())
            AppLogger.success("SILENCE", "✓ Comando SILENCE insertado en tabla commands")
            
            // 2. También marcar flag en devices para redundancia
            val deviceBody = JSONObject().apply {
                put("alert_acknowledged_remote", true)
            }
            AppLogger.command("SILENCE", "PATCH /devices", "alert_acknowledged_remote=true")
            apiPatch("/rest/v1/devices?device_id=eq.$deviceId", deviceBody.toString())
            AppLogger.success("SILENCE", "✓ Flag alert_acknowledged_remote=true en devices")
            
            // 3. Marcar en readings también
            acknowledgeDeviceAlert(deviceId)
            
            AppLogger.silenceCommandConfirmed(deviceId)
            true
        } catch (e: Exception) {
            AppLogger.silenceCommandFailed(deviceId, e.message ?: "Unknown error")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Resolver una alerta
     */
    fun resolveAlert(alertId: Long, notes: String = ""): Boolean {
        return try {
            val body = JSONObject().apply {
                put("resolved", true)
                put("resolved_at", "now()")
                put("resolution_notes", notes)
            }
            apiPatch("/rest/v1/alerts?id=eq.$alertId", body.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Enviar comando a un dispositivo
     */
    fun sendCommand(deviceId: String, command: String, parameters: JSONObject? = null): Boolean {
        return try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("command", command)
                put("parameters", parameters ?: JSONObject())
                put("source", "app_cloud")
                put("created_by", "android_app")
            }
            apiPost("/rest/v1/commands", body.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Obtener configuración de un dispositivo
     */
    fun getDeviceConfig(deviceId: String): DeviceConfig? {
        return try {
            val json = apiGet("/rest/v1/devices?device_id=eq.$deviceId&limit=1")
            val array = JSONArray(json)
            if (array.length() == 0) return null
            
            val device = array.getJSONObject(0)
            DeviceConfig(
                deviceId = device.getString("device_id"),
                name = device.optString("name", deviceId),
                location = device.optString("location", ""),
                tempMax = device.optDouble("temp_max", -15.0).toFloat(),
                tempCritical = device.optDouble("temp_critical", -10.0).toFloat(),
                alertDelaySec = device.optInt("alert_delay_sec", 300),
                doorOpenMaxSec = device.optInt("door_open_max_sec", 120),
                defrostCooldownSec = device.optInt("defrost_cooldown_sec", 1800),
                telegramEnabled = device.optBoolean("telegram_enabled", false),
                supabaseEnabled = device.optBoolean("supabase_enabled", true)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Actualizar configuración de un dispositivo
     */
    fun updateDeviceConfig(deviceId: String, config: JSONObject): Boolean {
        return try {
            apiPatch("/rest/v1/devices?device_id=eq.$deviceId", config.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Verificar conexión a Supabase
     */
    fun checkConnection(): Boolean {
        return try {
            val response = apiGet("/rest/v1/devices?limit=1")
            response.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    // ========================================
    // Métodos HTTP internos
    // ========================================
    
    private fun apiGet(endpoint: String): String {
        AppLogger.supabaseRequest(endpoint, "GET")
        val url = URL("$SUPABASE_URL$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("apikey", SUPABASE_KEY)
        connection.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            AppLogger.supabaseResponse(endpoint, responseCode, response.take(100))
            return response
        } else {
            AppLogger.supabaseResponse(endpoint, responseCode, "Error")
            throw Exception("HTTP Error: $responseCode")
        }
    }
    
    private fun apiPost(endpoint: String, body: String): String {
        AppLogger.supabaseRequest(endpoint, "POST", body)
        val url = URL("$SUPABASE_URL$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_KEY)
        connection.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "return=representation")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(body)
        writer.flush()
        writer.close()
        
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            AppLogger.supabaseResponse(endpoint, responseCode, response.take(100))
            return response
        } else {
            AppLogger.supabaseResponse(endpoint, responseCode, "Error")
            throw Exception("HTTP Error: $responseCode")
        }
    }
    
    private fun apiPatch(endpoint: String, body: String): String {
        AppLogger.supabaseRequest(endpoint, "PATCH", body)
        val url = URL("$SUPABASE_URL$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PATCH"
        connection.setRequestProperty("apikey", SUPABASE_KEY)
        connection.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "return=representation")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(body)
        writer.flush()
        writer.close()
        
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            AppLogger.supabaseResponse(endpoint, responseCode, response.take(100))
            return response
        } else {
            AppLogger.supabaseResponse(endpoint, responseCode, "Error")
            throw Exception("HTTP Error: $responseCode")
        }
    }
}
