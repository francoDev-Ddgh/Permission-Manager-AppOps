package com.francodev.appops.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getWorkMode(): WorkMode? =
        prefs.getString(KEY_WORK_MODE, null)?.let { name ->
            WorkMode.entries.firstOrNull { it.name == name }
        }

    fun setWorkMode(mode: WorkMode) {
        prefs.edit().putString(KEY_WORK_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_WORK_MODE = "work_mode"
    }
}
