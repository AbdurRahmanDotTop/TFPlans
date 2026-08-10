package com.techilyfly.tfplans.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Patterns
import com.google.android.gms.common.api.ApiException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.techilyfly.tfplans.TFPlansApplication
import com.techilyfly.tfplans.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

import com.techilyfly.tfplans.data.UserPreferencesRepository

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class AuthViewModel(
    private val auth: FirebaseAuth,
    private val repository: NotesRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    init {
        if (auth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            viewModelScope.launch {
                if (preferencesRepository.cloudBackup.value) {
                    try {
                        repository.syncAllNotesWithCloud()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Initial
        }
    }

    fun setAuthError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    fun handleGoogleIdToken(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(authCredential).await()
                handleSuccessfulLogin()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val userFriendlyMsg = if (msg.contains("network") || msg.contains("timeout") || msg.contains("Unable to resolve host")) {
                    "A network error occurred. Please check your internet connection."
                } else {
                    "Authentication failure. Please try again or use email and password."
                }
                _authState.value = AuthState.Error(userFriendlyMsg)
            }
        }
    }

    fun logout() {
        repository.stopRealtimeSync()
        viewModelScope.launch {
            repository.clearAllLocalData()
            preferencesRepository.clearPreferences()
            auth.signOut()
            _authState.value = AuthState.Initial
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                handleSuccessfulLogin()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapFirebaseAuthException(e))
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                handleSuccessfulLogin()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapFirebaseAuthException(e))
            }
        }
    }

    fun resetPassword(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                onResult(true, "Password reset email sent to $email")
            } catch (e: Exception) {
                onResult(false, mapFirebaseAuthException(e))
            }
        }
    }

    private suspend fun handleSuccessfulLogin() {
        preferencesRepository.setCloudBackup(true)
        repository.startRealtimeSync()
        
        // Initialize Google Drive folders right after login
        try {
            repository.initializeDriveFolders()
        } catch (e: com.techilyfly.tfplans.data.DrivePermissionDeniedException) {
            // User denied Drive permission during sign in. 
            // We proceed anyway, but media sync won't work until they grant it.
        } catch (e: Exception) {
            // Other initialization error
        }
        
        try {
            repository.syncAllNotesWithCloud()
        } catch (e: Exception) {
            // Sync failed silently, but login succeeded
        }
        _authState.value = AuthState.Authenticated
    }

    private fun mapFirebaseAuthException(e: Exception): String {
        val msg = e.message ?: ""
        return when (e) {
            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
            is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "An account already exists with this email address."
            is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "No account found with this email. Please sign up."
            is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Password is too weak. Please use at least 6 characters."
            is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException -> "Please log in again to perform this action."
            else -> {
                if (msg.contains("network") || msg.contains("timeout") || msg.contains("Unable to resolve host")) {
                    "A network error occurred. Please check your internet connection."
                } else {
                    "Authentication failed: ${e.localizedMessage ?: "Unknown error"}"
                }
            }
        }
    }

    companion object {
        fun provideFactory(app: TFPlansApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(app.container.firebaseAuth, app.container.notesRepository, app.container.userPreferencesRepository) as T
            }
        }
    }
}

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

