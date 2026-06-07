package com.example.portalphotoframe

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import android.widget.ArrayAdapter
import android.widget.LinearLayout
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
    private lateinit var quietHoursSwitch: SwitchMaterial
    private lateinit var quietHoursTimes: LinearLayout
    private lateinit var quietHoursNote: TextView
    private lateinit var btnQuietStart: MaterialButton
    private lateinit var btnQuietEnd: MaterialButton
    private lateinit var serverUrl: TextView
    private lateinit var serverInfo: TextView
    private lateinit var imageCount: TextView
    private lateinit var btnDeleteAll: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this@SettingsActivity, R.color.portal_background)))
        }

        durationValue = findViewById(R.id.durationValue)
        durationSeekBar = findViewById(R.id.durationSeekBar)
        transitionSpinner = findViewById(R.id.transitionSpinner)
        shuffleSwitch = findViewById(R.id.shuffleSwitch)
        quietHoursSwitch = findViewById(R.id.quietHoursSwitch)
        quietHoursTimes = findViewById(R.id.quietHoursTimes)
        quietHoursNote = findViewById(R.id.quietHoursNote)
        btnQuietStart = findViewById(R.id.btnQuietStart)
        btnQuietEnd = findViewById(R.id.btnQuietEnd)
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
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            TRANSITION_OPTIONS
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
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

        quietHoursSwitch.isChecked = FrameSchedule.isEnabled(this)
        updateQuietHoursUi()
        quietHoursSwitch.setOnCheckedChangeListener { _, isChecked ->
            FrameSchedule.setEnabled(this, isChecked)
            updateQuietHoursUi()
            notifyScheduleChanged()
        }

        btnQuietStart.text = FrameSchedule.formatMinutes(FrameSchedule.getStartMinutes(this))
        btnQuietStart.setOnClickListener {
            showTimePicker(FrameSchedule.getStartMinutes(this)) { minutes ->
                FrameSchedule.setStartMinutes(this, minutes)
                btnQuietStart.text = FrameSchedule.formatMinutes(minutes)
                notifyScheduleChanged()
            }
        }

        btnQuietEnd.text = FrameSchedule.formatMinutes(FrameSchedule.getEndMinutes(this))
        btnQuietEnd.setOnClickListener {
            showTimePicker(FrameSchedule.getEndMinutes(this)) { minutes ->
                FrameSchedule.setEndMinutes(this, minutes)
                btnQuietEnd.text = FrameSchedule.formatMinutes(minutes)
                notifyScheduleChanged()
            }
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

    private fun updateQuietHoursUi() {
        val enabled = quietHoursSwitch.isChecked
        val visibility = if (enabled) View.VISIBLE else View.GONE
        quietHoursTimes.visibility = visibility
        quietHoursNote.visibility = visibility
    }

    private fun showTimePicker(currentMinutes: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onSelected(hour * 60 + minute) },
            currentMinutes / 60,
            currentMinutes % 60,
            false
        ).show()
    }

    private fun notifyScheduleChanged() {
        WebServerService.onScheduleChanged?.invoke()
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
