package com.example.portalphotoframe

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    companion object {
        // Duration options in seconds
        val DURATION_OPTIONS = intArrayOf(3, 5, 7, 10, 15, 20, 30, 45, 60, 90, 120, 300)
        val TRANSITION_OPTIONS = arrayOf("crossfade", "slide", "none")
    }

    private lateinit var durationValue: TextView
    private lateinit var durationSeekBar: SeekBar
    private lateinit var transitionSpinner: Spinner
    private lateinit var shuffleSwitch: SwitchMaterial
    private lateinit var serverUrl: TextView
    private lateinit var serverInfo: TextView
    private lateinit var imageCount: TextView
    private lateinit var btnDeleteAll: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        durationValue = findViewById(R.id.durationValue)
        durationSeekBar = findViewById(R.id.durationSeekBar)
        transitionSpinner = findViewById(R.id.transitionSpinner)
        shuffleSwitch = findViewById(R.id.shuffleSwitch)
        serverUrl = findViewById(R.id.serverUrl)
        serverInfo = findViewById(R.id.serverInfo)
        imageCount = findViewById(R.id.imageCount)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Duration
        val currentDuration = prefs.getInt("slide_duration", 10)
        val currentIndex = DURATION_OPTIONS.indexOf(currentDuration).coerceAtLeast(0)
        durationSeekBar.max = DURATION_OPTIONS.size - 1
        durationSeekBar.progress = currentIndex
        updateDurationLabel(currentIndex)

        durationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateDurationLabel(progress)
                prefs.edit().putInt("slide_duration", DURATION_OPTIONS[progress]).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Transition
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, TRANSITION_OPTIONS)
        transitionSpinner.adapter = adapter
        val currentTransition = prefs.getString("transition", "crossfade") ?: "crossfade"
        transitionSpinner.setSelection(TRANSITION_OPTIONS.indexOf(currentTransition).coerceAtLeast(0))
        transitionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("transition", TRANSITION_OPTIONS[position]).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Shuffle
        shuffleSwitch.isChecked = prefs.getBoolean("shuffle", false)
        shuffleSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("shuffle", isChecked).apply()
        }

        // Server info
        val webService = WebServerService.instance
        if (webService != null) {
            val ip = webService.getDeviceIp()
            serverInfo.text = "Web server is running. Access from any device on the same network:"
            serverUrl.text = "http://$ip:${WebServerService.PORT}"
        } else {
            serverInfo.text = "Web server is not running."
            serverUrl.text = ""
        }

        // Image count
        val images = ImageManager.getImageFiles(this)
        imageCount.text = "${images.size} images stored"

        // Delete all
        btnDeleteAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete All Images")
                .setMessage("Are you sure you want to delete all ${images.size} images?")
                .setPositiveButton("Delete") { _, _ ->
                    val count = ImageManager.deleteAllImages(this)
                    imageCount.text = "0 images stored (deleted $count)"
                    WebServerService.onRefreshImages?.invoke()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateDurationLabel(index: Int) {
        val seconds = DURATION_OPTIONS[index]
        durationValue.text = if (seconds >= 60) {
            "${seconds / 60}m ${seconds % 60}s"
        } else {
            "${seconds}s"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
