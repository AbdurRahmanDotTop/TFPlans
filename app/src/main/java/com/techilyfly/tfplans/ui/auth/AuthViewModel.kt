package com.techilyfly.tfplans.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Patterns
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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

    fun loginWithGoogle(context: Context) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val rawNonce = UUID.randomUUID().toString()
                val bytes = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
                val hashedNonce = bytes.fold("") { str, it -> str + "%02x".format(it) }

                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                val webClientId = if (resId != 0) {
                    context.getString(resId)
                } else {
                    "174888666914-8jpbbn0oud27f1gifvn7gger77djsq7j.apps.googleusercontent.com"
                }

                val activity = context.findActivity()
                if (activity == null) {
                    _authState.value = AuthState.Error("Activity context not found. Cannot launch Google Sign-In.")
                    return@launch
                }

                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val credentialResponse = credentialManager.getCredential(context = activity, request = request)

                val credential = credentialResponse!!.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleIdToken = googleIdTokenCredential.idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    handleSuccessfulLogin()
                } else {
                    _authState.value = AuthState.Error("Invalid Google credential format. Please try again.")
                }
            } catch (e: NoCredentialException) {
                _authState.value = AuthState.Error("No Google accounts found on this device.")
            } catch (e: GetCredentialException) {
                val msg = e.message ?: ""
                val type = e.type
                val userFriendlyMsg = if (e is androidx.credentials.exceptions.GetCredentialCancellationException || msg.contains("GetCredentialCancellationException") || msg.contains("cancelled")) {
                    "Google Sign-In was cancelled. Tap 'Continue with Google' whenever you want to sign in."
                } else if (msg.contains("16") || msg.contains("SHA-1")) {
                    "Google Sign-In is currently unavailable for this app version. Please use email and password to sign in."
                } else if (msg.contains("no provider dependencies found") || msg.contains("GetCredentialUnsupportedException")) {
                    "Google Sign-In is not supported on this device. Please use email and password."
                } else {
                    "Google Sign-In failed. Please try again or use email and password."
                }
                _authState.value = AuthState.Error(userFriendlyMsg)
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val userFriendlyMsg = if (e is java.util.concurrent.CancellationException || msg.contains("cancelled")) {
                     "Google Sign-In was cancelled. Tap 'Continue with Google' whenever you want to sign in."
                } else if (msg.contains("28444") || msg.contains("Developer console") || msg.contains("one tap") || msg.contains("16")) {
                    "Google Sign-In is currently unavailable for this app version. Please use email and password to sign in."
                } else if (msg.contains("no provider dependencies found") || msg.contains("Unsupported")) {
                    "Google Sign-In is not supported on this device. Please use email and password."
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

