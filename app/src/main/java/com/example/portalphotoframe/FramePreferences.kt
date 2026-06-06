package com.example.portalphotoframe

import android.content.Context

object FramePreferences {
    private const val PREFS = "settings"
    private const val KEY_RESUME_ON_WAKE = "resume_on_wake"

    fun setResumeOnWake(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESUME_ON_WAKE, enabled)
            .apply()
    }

    fun shouldResumeOnWake(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESUME_ON_WAKE, true)
    }
}
