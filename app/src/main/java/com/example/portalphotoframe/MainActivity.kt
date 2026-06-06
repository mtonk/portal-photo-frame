package com.example.portalphotoframe

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var imageViewNext: ImageView
    private lateinit var emptyState: LinearLayout
    private lateinit var pauseIndicator: TextView
    private lateinit var imageCounter: TextView
    private lateinit var controlsOverlay: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var serverUrlText: TextView
    private lateinit var overlayIpText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var imageFiles = listOf<File>()
    private var currentIndex = 0
    private var isPlaying = true
    private var controlsVisible = false
    private var shuffledIndices = listOf<Int>()

    private val hideControlsRunnable = Runnable { hideControls() }

    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && imageFiles.isNotEmpty()) {
                nextImage()
                schedulNext()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        hideSystemUI()

        imageView = findViewById(R.id.imageView)
        imageViewNext = findViewById(R.id.imageViewNext)
        emptyState = findViewById(R.id.emptyState)
        pauseIndicator = findViewById(R.id.pauseIndicator)
        imageCounter = findViewById(R.id.imageCounter)
        controlsOverlay = findViewById(R.id.controlsOverlay)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        serverUrlText = findViewById(R.id.serverUrlText)
        overlayIpText = findViewById(R.id.overlayIpText)

        // Touch to show/hide controls
        val rootView = findViewById<FrameLayout>(android.R.id.content)
        rootView.setOnClickListener { toggleControls() }
        imageView.setOnClickListener { toggleControls() }
        imageViewNext.setOnClickListener { toggleControls() }

        // Control buttons
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { prevImage() }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { nextImage() }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Register web server callbacks
        WebServerService.onPlayPause = { runOnUiThread { togglePlayPause() } }
        WebServerService.onNext = { runOnUiThread { nextImage() } }
        WebServerService.onPrev = { runOnUiThread { prevImage() } }
        WebServerService.onRefreshImages = { runOnUiThread { loadImages() } }

        // Start web server service
        val serviceIntent = Intent(this, WebServerService::class.java)
        startForegroundService(serviceIntent)

        // Handle incoming shared images
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        loadImages()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(slideshowRunnable)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    uri?.let {
                        val name = ImageManager.saveImageFromUri(this, it)
                        if (name != null) {
                            Toast.makeText(this, "Image added", Toast.LENGTH_SHORT).show()
                            loadImages()
                        }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    var count = 0
                    uris?.forEach { uri ->
                        if (ImageManager.saveImageFromUri(this, uri) != null) count++
                    }
                    if (count > 0) {
                        Toast.makeText(this, "$count images added", Toast.LENGTH_SHORT).show()
                        loadImages()
                    }
                }
            }
        }
    }

    private fun loadImages() {
        imageFiles = ImageManager.getImageFiles(this)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val shuffle = prefs.getBoolean("shuffle", false)

        if (shuffle) {
            shuffledIndices = imageFiles.indices.shuffled()
        } else {
            shuffledIndices = imageFiles.indices.toList()
        }

        if (imageFiles.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            imageView.setImageDrawable(null)
            imageViewNext.setImageDrawable(null)
            imageCounter.visibility = View.GONE
            pauseIndicator.visibility = View.GONE

            // Show server URL on empty state
            val ip = getDeviceIp()
            serverUrlText.text = "Upload at http://$ip:${WebServerService.PORT}"
        } else {
            emptyState.visibility = View.GONE
            if (currentIndex >= imageFiles.size) currentIndex = 0
            showCurrentImage()
            if (isPlaying) {
                schedulNext()
            }
        }

        // Update web server status
        WebServerService.totalImages = imageFiles.size
    }

    private fun showCurrentImage() {
        if (imageFiles.isEmpty()) return

        val mappedIndex = if (shuffledIndices.isNotEmpty()) {
            shuffledIndices[currentIndex % shuffledIndices.size]
        } else {
            currentIndex
        }

        val file = imageFiles[mappedIndex]
        try {
            val bitmap = decodeSampledBitmap(file)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.alpha = 1f
                imageViewNext.alpha = 0f
            }
        } catch (e: Exception) {
            // Skip corrupted images
        }

        imageCounter.visibility = View.VISIBLE
        imageCounter.text = "${currentIndex + 1} / ${imageFiles.size}"
        WebServerService.currentImageIndex = currentIndex
    }

    private fun transitionToNext(file: File) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val transition = prefs.getString("transition", "crossfade") ?: "crossfade"

        try {
            val bitmap = decodeSampledBitmap(file) ?: return

            when (transition) {
                "crossfade" -> {
                    imageViewNext.setImageBitmap(bitmap)
                    imageViewNext.alpha = 0f

                    val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 800; fillAfter = true }
                    val fadeOut = AlphaAnimation(1f, 0f).apply {
                        duration = 800; fillAfter = true
                        setAnimationListener(object : Animation.AnimationListener {
                            override fun onAnimationStart(a: Animation?) {}
                            override fun onAnimationRepeat(a: Animation?) {}
                            override fun onAnimationEnd(a: Animation?) {
                                imageView.setImageBitmap(bitmap)
                                imageView.alpha = 1f
                                imageViewNext.alpha = 0f
                                imageViewNext.clearAnimation()
                                imageView.clearAnimation()
                            }
                        })
                    }

                    imageViewNext.startAnimation(fadeIn)
                    imageView.startAnimation(fadeOut)
                }
                "slide" -> {
                    imageViewNext.setImageBitmap(bitmap)
                    imageViewNext.alpha = 1f

                    val slideIn = TranslateAnimation(
                        Animation.RELATIVE_TO_PARENT, 1f,
                        Animation.RELATIVE_TO_PARENT, 0f,
                        Animation.RELATIVE_TO_PARENT, 0f,
                        Animation.RELATIVE_TO_PARENT, 0f
                    ).apply { duration = 500; fillAfter = true }

                    val slideOut = TranslateAnimation(
                        Animation.RELATIVE_TO_PARENT, 0f,
                        Animation.RELATIVE_TO_PARENT, -1f,
                        Animation.RELATIVE_TO_PARENT, 0f,
                        Animation.RELATIVE_TO_PARENT, 0f
                    ).apply {
                        duration = 500; fillAfter = true
                        setAnimationListener(object : Animation.AnimationListener {
                            override fun onAnimationStart(a: Animation?) {}
                            override fun onAnimationRepeat(a: Animation?) {}
                            override fun onAnimationEnd(a: Animation?) {
                                imageView.setImageBitmap(bitmap)
                                imageView.clearAnimation()
                                imageViewNext.clearAnimation()
                                imageViewNext.alpha = 0f
                            }
                        })
                    }

                    imageViewNext.startAnimation(slideIn)
                    imageView.startAnimation(slideOut)
                }
                else -> {
                    imageView.setImageBitmap(bitmap)
                }
            }
        } catch (e: Exception) {
            // Skip corrupted images
        }

        imageCounter.visibility = View.VISIBLE
        imageCounter.text = "${currentIndex + 1} / ${imageFiles.size}"
        WebServerService.currentImageIndex = currentIndex
    }

    private fun nextImage() {
        if (imageFiles.isEmpty()) return
        currentIndex = (currentIndex + 1) % imageFiles.size

        val mappedIndex = if (shuffledIndices.isNotEmpty()) {
            shuffledIndices[currentIndex % shuffledIndices.size]
        } else {
            currentIndex
        }

        transitionToNext(imageFiles[mappedIndex])
    }

    private fun prevImage() {
        if (imageFiles.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else imageFiles.size - 1

        val mappedIndex = if (shuffledIndices.isNotEmpty()) {
            shuffledIndices[currentIndex % shuffledIndices.size]
        } else {
            currentIndex
        }

        showCurrentImage()
    }

    private fun togglePlayPause() {
        isPlaying = !isPlaying
        WebServerService.isPlaying = isPlaying

        if (isPlaying) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            pauseIndicator.visibility = View.GONE
            schedulNext()
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            pauseIndicator.visibility = View.VISIBLE
            handler.removeCallbacks(slideshowRunnable)
        }
    }

    private fun schedulNext() {
        handler.removeCallbacks(slideshowRunnable)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val duration = prefs.getInt("slide_duration", 10)
        handler.postDelayed(slideshowRunnable, duration * 1000L)
    }

    private fun toggleControls() {
        if (controlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        val ip = getDeviceIp()
        overlayIpText.text = "http://$ip:${WebServerService.PORT}"
        controlsOverlay.visibility = View.VISIBLE
        controlsOverlay.alpha = 0f
        controlsOverlay.animate().alpha(1f).setDuration(200).start()
        controlsVisible = true
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 5000)
    }

    private fun hideControls() {
        controlsOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            controlsOverlay.visibility = View.GONE
        }.start()
        controlsVisible = false
    }

    private fun decodeSampledBitmap(file: File): Bitmap? {
        // Use screen size as the target, capped at GL max texture size
        val display = windowManager.defaultDisplay
        val screenW = display.width
        val screenH = display.height
        val maxDim = maxOf(screenW, screenH).coerceAtMost(2048)

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        // Subsample during decode
        var sampleSize = 1
        var w = opts.outWidth; var h = opts.outHeight
        while (w / 2 >= maxDim && h / 2 >= maxDim) {
            sampleSize *= 2; w /= 2; h /= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        // If still larger than texture limit, scale down
        if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            val newW = (bitmap.width * ratio).toInt()
            val newH = (bitmap.height * ratio).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun getDeviceIp(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        return if (ip != 0) {
            "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
        } else {
            "Not connected to WiFi"
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}
