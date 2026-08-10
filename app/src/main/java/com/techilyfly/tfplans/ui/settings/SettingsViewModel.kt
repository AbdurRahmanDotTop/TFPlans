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
import kotlinx.coroutines.tasks.await

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

    fun logout(context: android.content.Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (notesRepository.hasUnsyncedData()) {
                if (!com.techilyfly.tfplans.ui.auth.isOnline(context)) {
                    onResult(false, "You have unsynced data. Please connect to the internet to sync your data before signing out to prevent data loss.")
                    return@launch
                }
                
                val syncSuccess = notesRepository.syncAllNotesWithCloud()
                if (!syncSuccess) {
                    onResult(false, "Failed to sync pending data. Please try again.")
                    return@launch
                }
            }
            
            notesRepository.stopRealtimeSync()
            notesRepository.clearAllLocalData()
            preferencesRepository.clearPreferences()
            try {
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
            } catch (e: Exception) {}
            firebaseAuth.signOut()
            onResult(true, null)
        }
    }

    fun deleteAccount(context: android.content.Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val user = firebaseAuth.currentUser
            if (user == null) {
                onResult(false, "User not found")
                return@launch
            }
            
            if (notesRepository.hasUnsyncedData()) {
                if (!com.techilyfly.tfplans.ui.auth.isOnline(context)) {
                    onResult(false, "You have unsynced data. Please connect to the internet to sync your data before deleting your account to prevent data loss.")
                    return@launch
                }
                val syncSuccess = notesRepository.syncAllNotesWithCloud()
                if (!syncSuccess) {
                    onResult(false, "Failed to sync pending data. Please try again.")
                    return@launch
                }
            } else {
                if (!com.techilyfly.tfplans.ui.auth.isOnline(context)) {
                    onResult(false, "An active internet connection is required to delete your account.")
                    return@launch
                }
            }
            
            try {
                notesRepository.stopRealtimeSync()
                // Must attempt cloud data deletion while user is still authenticated
                notesRepository.deleteAllCloudData()
                
                user.delete().await()
                
                notesRepository.clearAllLocalData()
                preferencesRepository.clearPreferences()
                try {
                    val credentialManager = androidx.credentials.CredentialManager.create(context)
                    credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                } catch (e: Exception) {}
                firebaseAuth.signOut()
                onResult(true, "Account deleted successfully")
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                notesRepository.startRealtimeSync() // Restore sync if failed
                onResult(false, "REAUTH_REQUIRED")
            } catch (e: Exception) {
                notesRepository.startRealtimeSync() // Restore sync if failed
                onResult(false, e.message ?: "Failed to delete account")
            }
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
