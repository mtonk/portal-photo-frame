package com.example.portalphotoframe

import android.content.Context
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object FrameSchedule {

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "quiet_hours_enabled"
    private const val KEY_START_MINUTES = "quiet_start_minutes"
    private const val KEY_END_MINUTES = "quiet_end_minutes"

    const val DEFAULT_START_MINUTES = 23 * 60   // 11:00 PM
    const val DEFAULT_END_MINUTES = 7 * 60      // 7:00 AM

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getStartMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_START_MINUTES, DEFAULT_START_MINUTES)
    }

    fun getEndMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_END_MINUTES, DEFAULT_END_MINUTES)
    }

    fun setStartMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_START_MINUTES, minutes.coerceIn(0, 23 * 60 + 59)).apply()
    }

    fun setEndMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_END_MINUTES, minutes.coerceIn(0, 23 * 60 + 59)).apply()
    }

    /** True during configured quiet hours when scheduling is enabled. */
    fun isQuietHours(context: Context, now: LocalTime = LocalTime.now()): Boolean {
        if (!isEnabled(context)) return false
        return isQuietHours(now, getStartMinutes(context), getEndMinutes(context))
    }

    /** Keep screen awake and allow wake-resume outside quiet hours. */
    fun shouldKeepAlive(context: Context, now: LocalTime = LocalTime.now()): Boolean {
        return isEnabled(context) && !isQuietHours(context, now)
    }

    fun formatMinutes(minutes: Int): String {
        val hour = minutes / 60
        val minute = minutes % 60
        return LocalTime.of(hour, minute).format(timeFormatter)
    }

    internal fun isQuietHours(now: LocalTime, startMinutes: Int, endMinutes: Int): Boolean {
        val start = minutesToLocalTime(startMinutes)
        val end = minutesToLocalTime(endMinutes)
        return if (start > end) {
            !now.isBefore(start) || now.isBefore(end)
        } else {
            !now.isBefore(start) && now.isBefore(end)
        }
    }

    private fun minutesToLocalTime(minutes: Int): LocalTime {
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
