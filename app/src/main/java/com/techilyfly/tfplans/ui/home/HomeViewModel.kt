package com.techilyfly.tfplans.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.techilyfly.tfplans.TFPlansApplication
import com.techilyfly.tfplans.data.Note
import com.techilyfly.tfplans.data.NotesRepository
import com.techilyfly.tfplans.data.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: NotesRepository,
    private val auth: FirebaseAuth,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {
    val fontSize: StateFlow<String> = preferencesRepository.fontSize
    val defaultView: StateFlow<String> = preferencesRepository.defaultView

    fun setDefaultView(view: String) {
        preferencesRepository.setDefaultView(view)
    }

    fun setFontSize(size: String) {
        preferencesRepository.setFontSize(size)
    }
    private val _currentTab = MutableStateFlow("Notes")
    val currentTab: StateFlow<String> = _currentTab

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery


    val notesCount: StateFlow<Int> = repository.getNotesCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    init {
        if (auth.currentUser != null && preferencesRepository.cloudBackup.value) {
            repository.startRealtimeSync()
            viewModelScope.launch {
                try {
                    repository.syncAllNotesWithCloud()
                } catch (_: Exception) {}
            }
        }
    }



    fun setTab(tab: String) {
        _currentTab.value = tab
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getUserEmail(): String {
        return auth.currentUser?.email ?: ""
    }

    fun logout(context: Context, onComplete: () -> Unit) {
        repository.stopRealtimeSync()
        viewModelScope.launch {
            repository.clearAllLocalData()
            preferencesRepository.clearPreferences()
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {}
            auth.signOut()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    private val rawNotes = _currentTab.flatMapLatest { tab ->
        when (tab) {
            "Archive" -> repository.getArchivedNotes()
            "Reminders" -> repository.getReminderNotes()
            else -> repository.getActiveNotes()
        }
    }

    private val filteredNotes = rawNotes.combine(_searchQuery) { notes, query ->
        if (query.isBlank()) {
            notes
        } else {
            val q = query.trim().lowercase()
            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            notes.filter { note ->
                val reminderTime = note.reminderTime
                val reminderStr = if (reminderTime != null && reminderTime > 0) {
                    try {
                        sdf.format(Date(reminderTime)).lowercase()
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    ""
                }
                
                val reminderRepeat = note.reminderRepeat
                note.title.lowercase().contains(q) ||
                note.content.lowercase().contains(q) ||
                note.category.lowercase().contains(q) ||
                (reminderRepeat != null && reminderRepeat.lowercase().contains(q)) ||
                reminderStr.contains(q)
            }
        }
    }

    val pinnedNotes: StateFlow<List<Note>> = filteredNotes
        .map { notes -> notes.filter { it.isPinned } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val otherNotes: StateFlow<List<Note>> = filteredNotes
        .map { notes -> notes.filter { !it.isPinned } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    companion object {
        fun provideFactory(app: TFPlansApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(app.container.notesRepository, app.container.firebaseAuth, app.container.userPreferencesRepository) as T
            }
        }
    }
}

