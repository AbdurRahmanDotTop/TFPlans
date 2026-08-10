package com.techilyfly.tfplans.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.techilyfly.tfplans.TFPlansApplication
import com.techilyfly.tfplans.data.NotesRepository
import com.techilyfly.tfplans.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import kotlinx.coroutines.tasks.await

class ProfileViewModel(
    private val repository: NotesRepository,
    val auth: FirebaseAuth,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()
    
    private val _isEmailVerified = MutableStateFlow(auth.currentUser?.isEmailVerified ?: false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            _isEmailVerified.value = firebaseAuth.currentUser?.isEmailVerified ?: false
        }
    }

    val cloudBackup: StateFlow<Boolean> = preferencesRepository.cloudBackup
    val lastSyncedTime: StateFlow<Long> = preferencesRepository.lastSyncedTime

    val driveFolderRootId: StateFlow<String?> = preferencesRepository.driveFolderRootId
    val driveFolderImagesId: StateFlow<String?> = preferencesRepository.driveFolderImagesId
    val driveFolderRecordingsId: StateFlow<String?> = preferencesRepository.driveFolderRecordingsId

    val notesCount: StateFlow<Int> = repository.getNotesCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun syncNow(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.syncAllNotesWithCloud()
            } catch (_: Exception) {}
            onComplete()
        }
    }

    fun checkDrivePermission(context: Context): Boolean {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        return account != null && com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(
            account, 
            com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE)
        )
    }

    fun initializeDrive(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.initializeDriveFolders()
                onResult(true, "Google Drive folders initialized successfully")
            } catch (e: com.techilyfly.tfplans.data.DrivePermissionDeniedException) {
                onResult(false, "Google Drive permission not granted")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to initialize Drive")
            }
        }
    }

    fun reloadUser() {
        val user = auth.currentUser
        if (user != null) {
            viewModelScope.launch {
                try {
                    user.reload().await()
                    val updatedUser = auth.currentUser
                    _currentUser.value = updatedUser
                    _isEmailVerified.value = updatedUser?.isEmailVerified ?: false
                } catch (e: Exception) {
                    // Ignore network or token errors during silent reload
                }
            }
        }
    }

    fun updateProfile(name: String, photoUrl: String, onResult: (Boolean, String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "User not found")
            return
        }

        viewModelScope.launch {
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .apply {
                        if (photoUrl.isNotBlank()) {
                            setPhotoUri(Uri.parse(photoUrl))
                        }
                    }
                    .build()

                user.updateProfile(profileUpdates).await()
                
                // Force reload the user to get updated information
                user.reload().await()
                _currentUser.value = auth.currentUser
                
                onResult(true, "Profile updated successfully")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to update profile")
            }
        }
    }

    fun sendEmailVerification(onResult: (Boolean, String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "User not found")
            return
        }

        viewModelScope.launch {
            try {
                user.sendEmailVerification().await()
                onResult(true, "Verification email sent to ${user.email}")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to send verification email")
            }
        }
    }

    fun reauthenticate(password: String, onResult: (Boolean, String) -> Unit) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            onResult(false, "User not found")
            return
        }

        viewModelScope.launch {
            try {
                val credential = EmailAuthProvider.getCredential(user.email!!, password)
                user.reauthenticate(credential).await()
                onResult(true, "Re-authenticated successfully")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to re-authenticate")
            }
        }
    }

    fun updatePassword(newPassword: String, onResult: (Boolean, String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "User not found")
            return
        }

        viewModelScope.launch {
            try {
                user.updatePassword(newPassword).await()
                onResult(true, "Password updated successfully")
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                onResult(false, "REAUTH_REQUIRED")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to update password")
            }
        }
    }

    fun logout(context: Context, force: Boolean = false, onResult: (Boolean, String?, Boolean) -> Unit) {
        viewModelScope.launch {
            if (!com.techilyfly.tfplans.ui.auth.isOnline(context)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "An active internet connection is required to sign out safely.", false)
                }
                return@launch
            }
            
            if (!force && repository.hasUnsyncedData()) {
                val syncSuccess = repository.syncAllNotesWithCloud()
                if (!syncSuccess) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false, "Failed to sync pending data. Please try again.", true)
                    }
                    return@launch
                }
            }
            
            repository.stopRealtimeSync()
            repository.clearAllLocalData()
            preferencesRepository.clearPreferences()
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {}
            
            try {
                @Suppress("DEPRECATION")
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                @Suppress("DEPRECATION")
                val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                googleSignInClient.signOut()
            } catch (e: Exception) {}
            
            auth.signOut()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(true, null, false)
            }
        }
    }

    fun deleteAccount(context: Context, force: Boolean = false, onResult: (Boolean, String, Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "User not found", false)
            return
        }
        
        viewModelScope.launch {
            if (!com.techilyfly.tfplans.ui.auth.isOnline(context)) {
                onResult(false, "An active internet connection is required to delete your account safely.", false)
                return@launch
            }
            
            if (!force && repository.hasUnsyncedData()) {
                val syncSuccess = repository.syncAllNotesWithCloud()
                if (!syncSuccess) {
                    onResult(false, "Failed to sync pending data. Please try again.", true)
                    return@launch
                }
            }
            
            repository.stopRealtimeSync()
            try {
                // We should technically delete cloud data first, but if delete() fails we don't want to lose data yet.
                // However, doing this securely requires a Cloud Function. We'll attempt client-side delete.
                repository.deleteAllCloudData() // This might fail since they are unauthenticated now, but we try.
                user.delete().await()
                
                // If we reach here, user is deleted from Auth successfully
                repository.clearAllLocalData()
                preferencesRepository.clearPreferences()
                
                try {
                    val credentialManager = CredentialManager.create(context)
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {}
                
                auth.signOut()
                onResult(true, "Account deleted successfully", false)
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                repository.startRealtimeSync() // Restore sync if failed
                onResult(false, "REAUTH_REQUIRED", false)
            } catch (e: Exception) {
                // If delete fails, restore sync and report error
                repository.startRealtimeSync()
                onResult(false, e.message ?: "Failed to delete account", false)
            }
        }
    }

    companion object {
        fun provideFactory(app: TFPlansApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                    return ProfileViewModel(
                        repository = app.container.notesRepository,
                        auth = FirebaseAuth.getInstance(),
                        preferencesRepository = app.container.userPreferencesRepository
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
