package com.example.portalphotoframe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.FileInputStream

class WebServerService : Service() {

    companion object {
        const val PORT = 8080
        const val CHANNEL_ID = "web_server_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "WebServerService"

        var instance: WebServerService? = null
            private set

        // Slideshow control callbacks
        var onPlayPause: (() -> Unit)? = null
        var onNext: (() -> Unit)? = null
        var onPrev: (() -> Unit)? = null
        var onRefreshImages: (() -> Unit)? = null
        var isPlaying: Boolean = true
        var currentImageIndex: Int = 0
        var totalImages: Int = 0
    }

    private var server: FrameHttpServer? = null
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startServer()
    }

    override fun onDestroy() {
        server?.stop()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startServer() {
        try {
            server = FrameHttpServer(this, PORT)
            server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "Web server started on port $PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web server", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Portal Photo Frame Web Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Web server for Portal Photo Frame remote control"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        val ip = getDeviceIp()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Portal Photo Frame")
            .setContentText("Web UI: http://$ip:$PORT")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun getDeviceIp(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        return if (ip != 0) {
            "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
        } else {
            "localhost"
        }
    }

    // Inner HTTP server class
    private inner class FrameHttpServer(
        private val context: Context,
        port: Int
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            return try {
                when {
                    // API routes
                    uri == "/api/images" && method == Method.GET -> handleListImages()
                    uri == "/api/upload" && method == Method.POST -> handleUpload(session)
                    uri.startsWith("/api/images/") && method == Method.DELETE -> handleDeleteImage(uri)
                    uri.startsWith("/api/images/") && method == Method.GET -> handleGetImage(uri)
                    uri.startsWith("/api/thumbs/") && method == Method.GET -> handleGetThumb(uri)
                    uri == "/api/settings" && method == Method.GET -> handleGetSettings()
                    uri == "/api/settings" && method == Method.POST -> handleUpdateSettings(session)
                    uri.startsWith("/api/control/") && method == Method.POST -> handleControl(uri)
                    uri == "/api/status" && method == Method.GET -> handleGetStatus()

                    // Web UI
                    uri == "/" || uri == "/index.html" -> serveWebUI()

                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling request: $uri", e)
                jsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        private fun handleListImages(): Response {
            val files = ImageManager.getImageFiles(context)
            val images = files.map { file ->
                mapOf(
                    "name" to file.name,
                    "size" to file.length(),
                    "modified" to file.lastModified()
                )
            }
            return jsonResponse(Response.Status.OK, mapOf("images" to images))
        }

        private fun handleUpload(session: IHTTPSession): Response {
            val contentType = session.headers["content-type"] ?: ""

            if (!contentType.contains("multipart/form-data")) {
                return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Expected multipart/form-data"))
            }

            val files = HashMap<String, String>()
            session.parseBody(files)

            val uploadedNames = mutableListOf<String>()
            var skipped = 0

            // NanoHTTPD stores uploaded files as temp files
            for ((key, tempPath) in files) {
                if (key.startsWith("file")) {
                    val tempFile = java.io.File(tempPath)
                    if (tempFile.exists()) {
                        val stream = tempFile.inputStream()
                        val name = ImageManager.saveImageFromStream(context, stream)
                        if (name == "DUPLICATE") {
                            skipped++
                        } else if (name != null) {
                            uploadedNames.add(name)
                        }
                        tempFile.delete()
                    }
                }
            }

            onRefreshImages?.invoke()

            return jsonResponse(Response.Status.OK, mapOf(
                "uploaded" to uploadedNames.size,
                "skipped" to skipped,
                "files" to uploadedNames
            ))
        }

        private fun handleDeleteImage(uri: String): Response {
            val name = uri.removePrefix("/api/images/")
            val success = ImageManager.deleteImage(context, name)
            onRefreshImages?.invoke()
            return jsonResponse(
                if (success) Response.Status.OK else Response.Status.NOT_FOUND,
                mapOf("deleted" to success)
            )
        }

        private fun handleGetImage(uri: String): Response {
            val name = uri.removePrefix("/api/images/")
            val file = ImageManager.getImageFile(context, name)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

            val mimeType = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                else -> "image/jpeg"
            }
            val fis = FileInputStream(file)
            return newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length())
        }

        private fun handleGetThumb(uri: String): Response {
            val name = uri.removePrefix("/api/thumbs/")
            val file = ImageManager.getThumbnail(context, name)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

            val fis = FileInputStream(file)
            return newFixedLengthResponse(Response.Status.OK, "image/jpeg", fis, file.length())
        }

        private fun handleGetSettings(): Response {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val settings = mapOf(
                "duration" to prefs.getInt("slide_duration", 10),
                "shuffle" to prefs.getBoolean("shuffle", false),
                "transition" to prefs.getString("transition", "crossfade")
            )
            return jsonResponse(Response.Status.OK, settings)
        }

        private fun handleUpdateSettings(session: IHTTPSession): Response {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val body = ByteArray(contentLength)
            session.inputStream.read(body, 0, contentLength)
            val json = String(body)

            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(json, Map::class.java) as Map<String, Any>

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            data["duration"]?.let {
                val dur = (it as Number).toInt().coerceIn(3, 300)
                editor.putInt("slide_duration", dur)
            }
            data["shuffle"]?.let {
                editor.putBoolean("shuffle", it as Boolean)
            }
            data["transition"]?.let {
                editor.putString("transition", it as String)
            }

            editor.apply()
            onRefreshImages?.invoke()

            return jsonResponse(Response.Status.OK, mapOf("success" to true))
        }

        private fun handleControl(uri: String): Response {
            val action = uri.removePrefix("/api/control/")
            when (action) {
                "playpause" -> onPlayPause?.invoke()
                "next" -> onNext?.invoke()
                "prev" -> onPrev?.invoke()
                else -> return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Unknown action"))
            }
            return jsonResponse(Response.Status.OK, mapOf("action" to action, "playing" to isPlaying))
        }

        private fun handleGetStatus(): Response {
            val images = ImageManager.getImageFiles(context)
            return jsonResponse(Response.Status.OK, mapOf(
                "playing" to isPlaying,
                "currentIndex" to currentImageIndex,
                "totalImages" to images.size,
                "currentImage" to if (images.isNotEmpty() && currentImageIndex < images.size)
                    images[currentImageIndex].name else null
            ))
        }

        private fun serveWebUI(): Response {
            return try {
                val html = context.assets.open("web/index.html").bufferedReader().readText()
                newFixedLengthResponse(Response.Status.OK, "text/html", html)
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Web UI not found")
            }
        }

        private fun jsonResponse(status: Response.Status, data: Any): Response {
            val json = gson.toJson(data)
            return newFixedLengthResponse(status, "application/json", json)
        }
    }
}
