package com.techilyfly.tfplans.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.techilyfly.tfplans.TFPlansApplication
import com.techilyfly.tfplans.data.NotesRepository
import com.techilyfly.tfplans.data.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val notesRepository: NotesRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val themeMode: StateFlow<String> = preferencesRepository.themeMode
    val fontSize: StateFlow<String> = preferencesRepository.fontSize
    val defaultView: StateFlow<String> = preferencesRepository.defaultView
    val cloudBackup: StateFlow<Boolean> = preferencesRepository.cloudBackup
    val lastSyncedTime: StateFlow<Long> = preferencesRepository.lastSyncedTime

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun getUserEmail(): String {
        return firebaseAuth.currentUser?.email ?: ""
    }

    fun setThemeMode(mode: String) {
        preferencesRepository.setThemeMode(mode)
        viewModelScope.launch {
            try {
                notesRepository.backupSettingsToCloud()
            } catch (_: Exception) {}
        }
    }

    fun setFontSize(size: String) {
        preferencesRepository.setFontSize(size)
        viewModelScope.launch {
            try {
                notesRepository.backupSettingsToCloud()
            } catch (_: Exception) {}
        }
    }

    fun setDefaultView(view: String) {
        preferencesRepository.setDefaultView(view)
        viewModelScope.launch {
            try {
                notesRepository.backupSettingsToCloud()
            } catch (_: Exception) {}
        }
    }

    fun setCloudBackup(enabled: Boolean) {
        preferencesRepository.setCloudBackup(enabled)
    }

    fun logout() {
        notesRepository.stopRealtimeSync()
        viewModelScope.launch {
            notesRepository.clearAllLocalData()
            preferencesRepository.clearPreferences()
            firebaseAuth.signOut()
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                notesRepository.stopRealtimeSync()
                notesRepository.deleteAllCloudData()
                notesRepository.clearAllLocalData()
                preferencesRepository.clearPreferences()
                firebaseAuth.currentUser?.delete()
            } catch (_: Exception) {
                notesRepository.clearAllLocalData()
                preferencesRepository.clearPreferences()
                firebaseAuth.signOut()
            }
            onComplete()
        }
    }

    companion object {
        fun provideFactory(application: TFPlansApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        application.container.userPreferencesRepository,
                        application.container.notesRepository,
                        application.container.firebaseAuth
                    ) as T
                }
            }
    }
}
