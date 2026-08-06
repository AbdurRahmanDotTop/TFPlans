package com.techilyfly.tfplans.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tfplans_user_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _fontSize = MutableStateFlow(getSavedFontSize())
    val fontSize: StateFlow<String> = _fontSize.asStateFlow()

    private val _defaultView = MutableStateFlow(getSavedDefaultView())
    val defaultView: StateFlow<String> = _defaultView.asStateFlow()

    private val _cloudBackup = MutableStateFlow(getSavedCloudBackup())
    val cloudBackup: StateFlow<Boolean> = _cloudBackup.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow(getSavedLastSyncedTime())
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime.asStateFlow()

    private fun getSavedThemeMode(): String = prefs.getString("theme_mode", "system") ?: "system"
    private fun getSavedFontSize(): String = prefs.getString("font_size", "medium") ?: "medium"
    private fun getSavedDefaultView(): String = prefs.getString("default_view", "grid") ?: "grid"
    private fun getSavedCloudBackup(): Boolean {
        val isLoggedIn = try {
            FirebaseAuth.getInstance().currentUser != null
        } catch (_: Exception) {
            false
        }
        return prefs.getBoolean("cloud_backup", isLoggedIn)
    }
    private fun getSavedLastSyncedTime(): Long = prefs.getLong("last_synced_time", 0L)

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun setFontSize(size: String) {
        prefs.edit().putString("font_size", size).apply()
        _fontSize.value = size
    }

    fun setDefaultView(view: String) {
        prefs.edit().putString("default_view", view).apply()
        _defaultView.value = view
    }

    fun setCloudBackup(enabled: Boolean) {
        prefs.edit().putBoolean("cloud_backup", enabled).apply()
        _cloudBackup.value = enabled
    }

    fun updateLastSyncedTime(time: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_synced_time", time).apply()
        _lastSyncedTime.value = time
    }

    suspend fun clearPreferences() {
        prefs.edit().clear().apply()
        _themeMode.value = "system"
        _fontSize.value = "medium"
        _defaultView.value = "grid"
        _cloudBackup.value = false
        _lastSyncedTime.value = 0L
    }
}
