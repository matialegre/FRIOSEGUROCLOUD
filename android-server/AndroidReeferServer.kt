package com.parametican.reeferserver

import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Servidor HTTP embebido en Android
 * Maneja requests HTTP y genera respuestas
 */
class AndroidReeferServer {

    private val TAG = "ReeferServer"
    
    // Base de datos simple en memoria (en producción usar Room/SQLite)
    private val reefers = mutableMapOf<String, ReeferData>()
    private val history = mutableListOf<HistoryPoint>()
    
    data class ReeferData(
        var id: String,
        var name: String,
        var temp: Float = -999f,
        var doorOpen: Boolean = false,
        var alertActive: Boolean = false,
        var lastUpdate: Long = System.currentTimeMillis()
    )
    
    data class HistoryPoint(
        val timestamp: Long,
        val reeferId: String,
        val temp: Float,
        val doorOpen: Boolean
    )

    fun handleRequest(request: String): String {
        try {
            val lines = request.lines()
            if (lines.isEmpty()) {
                return createResponse(400, "text/plain", "Bad Request")
            }
            
            val requestLine = lines[0]
            val parts = requestLine.split(" ")
            
            if (parts.size < 3) {
                return createResponse(400, "text/plain", "Bad Request")
            }
            
            val method = parts[0]
            val path = parts[1]
            
            Log.d(TAG, "$method $path")
            
            return when {
                method == "GET" && path == "/" -> handleRoot()
                method == "GET" && path == "/api/status" -> handleApiStatus()
                method == "POST" && path == "/api/data" -> {
                    val body = extractBody(request)
                    handleApiData(body)
                }
                method == "GET" && path.startsWith("/api/history") -> handleApiHistory()
                else -> createResponse(404, "text/plain", "Not Found: $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error manejando request", e)
            return createResponse(500, "text/plain", "Internal Server Error: ${e.message}")
        }
    }

    private fun handleRoot(): String {
        val html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reefer Monitor - Android Server</title>
    <style>
        body { font-family: Arial, sans-serif; background: #1a1a2e; color: #fff; padding: 20px; }
        .container { max-width: 800px; margin: 0 auto; }
        h1 { color: #60a5fa; }
        .card { background: #16213e; padding: 20px; margin: 20px 0; border-radius: 10px; }
        .temp { font-size: 3em; color: #22d3ee; font-weight: bold; }
        .alert { color: #ef4444; font-weight: bold; }
        .ok { color: #22c55e; }
        button { padding: 10px 20px; background: #3b82f6; color: white; border: none; border-radius: 5px; cursor: pointer; }
        button:hover { background: #2563eb; }
    </style>
</head>
<body>
    <div class="container">
        <h1>📱 Reefer Monitor - Servidor Android</h1>
        <p>Este servidor está corriendo en un celular Android</p>
        
        <div class="card">
            <h2>📊 Estado de Reefers</h2>
            <div id="reefers">Cargando...</div>
        </div>
        
        <div class="card">
            <button onclick="location.reload()">🔄 Actualizar</button>
            <button onclick="fetch('/api/status').then(r=>r.json()).then(d=>alert(JSON.stringify(d)))">📊 Ver JSON</button>
        </div>
    </div>
    
    <script>
        async function loadStatus() {
            const res = await fetch('/api/status');
            const data = await res.json();
            
            let html = '<table style="width:100%;border-collapse:collapse;">';
            html += '<tr><th>ID</th><th>Nombre</th><th>Temp</th><th>Estado</th><th>Última Actualización</th></tr>';
            
            data.reefers.forEach(r => {
                const tempColor = r.temp > -10 ? '#ef4444' : r.temp > -18 ? '#fbbf24' : '#22c55e';
                const status = r.alert_active ? '<span class="alert">⚠️ ALERTA</span>' : '<span class="ok">✅ OK</span>';
                html += `<tr>
                    <td>${r.id}</td>
                    <td>${r.name}</td>
                    <td style="color:${tempColor}">${r.temp.toFixed(1)}°C</td>
                    <td>${status}</td>
                    <td>${new Date(r.last_update).toLocaleString()}</td>
                </tr>`;
            });
            
            html += '</table>';
            document.getElementById('reefers').innerHTML = html;
        }
        
        loadStatus();
        setInterval(loadStatus, 5000);
    </script>
</body>
</html>
        """.trimIndent()
        
        return createResponse(200, "text/html; charset=utf-8", html)
    }

    private fun handleApiStatus(): String {
        val json = JSONObject().apply {
            put("server_type", "Android")
            put("server_version", "1.0")
            put("uptime_ms", System.currentTimeMillis())
            
            val reefersArray = org.json.JSONArray()
            reefers.values.forEach { r ->
                reefersArray.put(JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("temp", r.temp)
                    put("door_open", r.doorOpen)
                    put("alert_active", r.alertActive)
                    put("last_update", r.lastUpdate)
                })
            }
            put("reefers", reefersArray)
            put("total_reefers", reefers.size)
            put("total_history_points", history.size)
        }
        
        return createResponse(200, "application/json", json.toString())
    }

    private fun handleApiData(body: String): String {
        try {
            val json = JSONObject(body)
            val reeferId = json.optString("reefer_id", "UNKNOWN")
            val temp = json.optDouble("temp", -999.0).toFloat()
            val doorOpen = json.optBoolean("door_open", false)
            
            // Actualizar o crear reefer
            val reefer = reefers.getOrPut(reeferId) {
                ReeferData(
                    id = reeferId,
                    name = json.optString("name", "Reefer $reeferId")
                )
            }
            
            reefer.temp = temp
            reefer.doorOpen = doorOpen
            reefer.alertActive = temp > -10.0
            reefer.lastUpdate = System.currentTimeMillis()
            
            // Guardar en historial
            history.add(HistoryPoint(
                timestamp = System.currentTimeMillis(),
                reeferId = reeferId,
                temp = temp,
                doorOpen = doorOpen
            ))
            
            // Mantener solo últimos 1000 puntos
            if (history.size > 1000) {
                history.removeAt(0)
            }
            
            Log.d(TAG, "Datos recibidos: Reefer $reeferId, Temp: $temp°C")
            
            val response = JSONObject().apply {
                put("status", "ok")
                put("message", "Datos recibidos")
                put("reefer_id", reeferId)
            }
            
            return createResponse(200, "application/json", response.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando datos", e)
            return createResponse(400, "application/json", 
                JSONObject().apply {
                    put("status", "error")
                    put("message", e.message)
                }.toString()
            )
        }
    }

    private fun handleApiHistory(): String {
        val json = JSONObject().apply {
            val historyArray = org.json.JSONArray()
            history.takeLast(100).forEach { point ->
                historyArray.put(JSONObject().apply {
                    put("timestamp", point.timestamp)
                    put("reefer_id", point.reeferId)
                    put("temp", point.temp)
                    put("door_open", point.doorOpen)
                })
            }
            put("history", historyArray)
        }
        
        return createResponse(200, "application/json", json.toString())
    }

    private fun extractBody(request: String): String {
        val parts = request.split("\r\n\r\n")
        return if (parts.size > 1) parts[1] else ""
    }

    private fun createResponse(statusCode: Int, contentType: String, body: String): String {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }
        
        return """
HTTP/1.1 $statusCode $statusText
Content-Type: $contentType
Content-Length: ${body.toByteArray().size}
Access-Control-Allow-Origin: *
Connection: close

$body
        """.trimIndent()
    }
}
