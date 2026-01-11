package com.parametican.reeferserver

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Servicio Android que actúa como servidor HTTP para monitoreo de reefers
 * El celular se convierte en un servidor web completo
 */
class ReeferServerService : Service() {

    companion object {
        private const val TAG = "ReeferServer"
        private const val CHANNEL_ID = "reefer_server_channel"
        private const val NOTIFICATION_ID = 1
        private const val PORT = 8080  // Puerto donde escucha el servidor
        
        var isRunning = false
        private var serverSocket: ServerSocket? = null
        private var serverThread: Thread? = null
    }

    private val binder = LocalBinder()
    private val server = AndroidReeferServer()

    inner class LocalBinder : Binder() {
        fun getService(): ReeferServerService = this@ReeferServerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Servicio creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Servidor HTTP iniciado en puerto $PORT"))
        
        if (!isRunning) {
            startServer()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reefer Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servidor HTTP para monitoreo de reefers"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🌐 Reefer Server Activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startServer() {
        if (isRunning) return
        
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(PORT)
                isRunning = true
                Log.d(TAG, "Servidor iniciado en puerto $PORT")
                
                val executor = Executors.newFixedThreadPool(10)
                
                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        Log.d(TAG, "Cliente conectado: ${clientSocket.remoteSocketAddress}")
                        
                        // Manejar cada cliente en un thread separado
                        executor.execute {
                            handleClient(clientSocket)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Error aceptando conexión", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error iniciando servidor", e)
                isRunning = false
            }
        }
        
        serverThread?.start()
        
        // Actualizar notificación con la IP
        val ip = getLocalIpAddress()
        updateNotification("Servidor en http://$ip:$PORT")
    }

    private fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando servidor", e)
        }
        serverThread?.interrupt()
        Log.d(TAG, "Servidor detenido")
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            // Leer request
            val request = readRequest(input)
            
            // Procesar request y generar response
            val response = server.handleRequest(request)
            
            // Enviar response
            output.write(response.toByteArray())
            output.flush()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error manejando cliente", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error cerrando socket", e)
            }
        }
    }

    private fun readRequest(input: java.io.InputStream): String {
        val buffer = ByteArray(4096)
        val bytesRead = input.read(buffer)
        return String(buffer, 0, bytesRead)
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo IP", e)
        }
        return "0.0.0.0"
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    fun getServerUrl(): String {
        val ip = getLocalIpAddress()
        return "http://$ip:$PORT"
    }

    fun getServerStatus(): String {
        return if (isRunning) {
            "Servidor activo en ${getServerUrl()}"
        } else {
            "Servidor detenido"
        }
    }
}
