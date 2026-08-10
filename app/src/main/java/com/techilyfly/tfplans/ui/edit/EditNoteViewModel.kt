package com.techilyfly.tfplans.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techilyfly.tfplans.ai.GenerativeAiProvider
import com.techilyfly.tfplans.TFPlansApplication
import com.techilyfly.tfplans.data.Note
import com.techilyfly.tfplans.data.NotesRepository
import com.techilyfly.tfplans.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class EditNoteViewModel(
    private val repository: NotesRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val fontSize: StateFlow<String> = preferencesRepository.fontSize

    private val _note = MutableStateFlow(Note(id = UUID.randomUUID().toString(), title = "", content = "", color = android.graphics.Color.WHITE))
    val note: StateFlow<Note> = _note

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing
    
    private val _showColorPicker = MutableStateFlow(false)
    val showColorPicker: StateFlow<Boolean> = _showColorPicker

    // AI Assistant State
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading
    
    private val _aiPreviewText = MutableStateFlow<String?>(null)
    val aiPreviewText: StateFlow<String?> = _aiPreviewText
    
    private val _pendingAiAction = MutableStateFlow<AiAction?>(null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var originalNote: Note? = null

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun loadNote(id: String?) {
        if (id != null) {
            viewModelScope.launch {
                val existing = repository.getNoteById(id)
                if (existing != null) {
                    _note.value = existing
                    originalNote = existing.copy()
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _note.value = _note.value.copy(title = title, updatedAt = System.currentTimeMillis())
    }

    fun updateContent(content: String) {
        _note.value = _note.value.copy(content = content, updatedAt = System.currentTimeMillis())
    }

    fun updateCategory(category: String) {
        _note.value = _note.value.copy(category = category, updatedAt = System.currentTimeMillis())
    }

    fun updateReminder(timeInMillis: Long?, repeatPattern: String? = null) {
        _note.value = _note.value.copy(
            reminderTime = timeInMillis,
            reminderRepeat = repeatPattern,
            updatedAt = System.currentTimeMillis()
        )
        saveNote()
    }

    fun updateColor(color: Int) {
        _note.value = _note.value.copy(color = color, updatedAt = System.currentTimeMillis())
        saveNote()
        _showColorPicker.value = false
    }

    fun toggleColorPicker() {
        _showColorPicker.value = !_showColorPicker.value
    }

    fun hideColorPicker() {
        _showColorPicker.value = false
    }

    fun togglePin() {
        _note.value = _note.value.copy(isPinned = !_note.value.isPinned, updatedAt = System.currentTimeMillis())
        saveNote()
    }
    
    fun toggleArchive() {
        _note.value = _note.value.copy(isArchived = !_note.value.isArchived, updatedAt = System.currentTimeMillis())
        saveNote()
    }

    fun deleteNote() {
        // Update the local state FIRST to prevent saveNote from reviving it!
        _note.value = _note.value.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
        
        // Use an independent scope to ensure deletion happens even if ViewModel is cleared during back navigation
        CoroutineScope(Dispatchers.IO).launch {
            repository.deleteNote(_note.value)
        }
    }

    private fun isNoteEffectivelyBlank(note: Note): Boolean {
        if (note.title.isNotBlank()) return false
        if (note.category.isNotBlank()) return false
        if (note.reminderTime != null && note.reminderTime!! > 0) return false
        
        val textWithoutEmptyChecklists = note.content.lines().filter { line ->
            val trimmed = line.trim()
            trimmed != "- [ ]" && trimmed != "- [x]"
        }.joinToString("").trim()
        
        return textWithoutEmptyChecklists.isEmpty()
    }

    fun saveNote() {
        val currentNote = _note.value
        
        // Fix for "Blank Note Created Unexpectedly" & "Deleting note turns into blank note"
        val isEffectivelyBlank = isNoteEffectivelyBlank(currentNote)
        
        if (isEffectivelyBlank) {
            if (originalNote != null) {
                // The note existed, but the user erased it. We should delete it.
                CoroutineScope(Dispatchers.IO).launch {
                    repository.deleteNote(currentNote)
                }
            }
            // For a brand new note that is blank, just do nothing.
            return
        }
        
        // Prevent unnecessary saves when nothing actually changed (Read-Overwrite Corruption fix)
        if (originalNote != null && 
            currentNote.title == originalNote?.title && 
            currentNote.content == originalNote?.content && 
            currentNote.color == originalNote?.color && 
            currentNote.category == originalNote?.category && 
            currentNote.reminderTime == originalNote?.reminderTime && 
            currentNote.reminderRepeat == originalNote?.reminderRepeat &&
            currentNote.isPinned == originalNote?.isPinned && 
            currentNote.isArchived == originalNote?.isArchived &&
            currentNote.isDone == originalNote?.isDone &&
            currentNote.isDeleted == originalNote?.isDeleted
        ) {
            return
        }

        // Update original immediately to prevent race conditions on double back-press
        originalNote = currentNote.copy()

        // Use independent scope so it saves properly even if navigating back destroys the ViewModel
        CoroutineScope(Dispatchers.IO).launch {
            repository.saveNote(currentNote)
        }
    }
    
    // --- AI Assistant Methods ---
    
    fun dismissAiPreview() {
        _aiPreviewText.value = null
        _pendingAiAction.value = null
    }
    
    fun acceptAiSuggestion() {
        val suggestion = _aiPreviewText.value ?: return
        val action = _pendingAiAction.value
        
        if (action == AiAction.GENERATE_TITLE) {
            updateTitle(suggestion.replace("\"", "").trim())
        } else {
            var isJson = false
            var jsonArray: org.json.JSONArray? = null
            if (_note.value.content.trimStart().startsWith("[")) {
                try {
                    jsonArray = org.json.JSONArray(_note.value.content)
                    isJson = true
                } catch (e: Exception) {}
            }
            
            if (isJson && jsonArray != null) {
                val prefix = when (action) {
                    AiAction.SUMMARIZE -> "\n\n--- AI Summary ---\n"
                    AiAction.FIX_GRAMMAR -> "\n\n--- AI Grammar Fix ---\n"
                    AiAction.REWRITE -> "\n\n--- AI Rewritten ---\n"
                    AiAction.EXPAND -> "\n\n--- AI Expanded ---\n"
                    AiAction.CONTINUE_WRITING -> "\n\n"
                    else -> "\n\n--- AI Output ---\n"
                }
                val newBlock = org.json.JSONObject()
                newBlock.put("type", "text")
                newBlock.put("text", (prefix + suggestion).trimStart())
                jsonArray.put(newBlock)
                updateContent(jsonArray.toString())
            } else {
                if (action == AiAction.CONTINUE_WRITING) {
                    val newContent = _note.value.content + (if (_note.value.content.endsWith(" ")) "" else " ") + suggestion
                    updateContent(newContent)
                } else if (action == AiAction.SUMMARIZE) {
                    val newContent = _note.value.content + "\n\n--- AI Summary ---\n" + suggestion
                    updateContent(newContent)
                } else {
                    updateContent(suggestion)
                }
            }
        }
        
        dismissAiPreview()
    }
    
    fun performAiAction(action: AiAction) {
        var currentContent = _note.value.content
        if (currentContent.trimStart().startsWith("[")) {
            try {
                val sb = StringBuilder()
                val jsonArray = org.json.JSONArray(currentContent)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("text")) {
                        sb.append(obj.getString("text")).append("\n")
                    }
                }
                currentContent = sb.toString().trim()
            } catch (e: Exception) {}
        }
        
        if (currentContent.isBlank()) {
            _errorMessage.value = "AI Error: Note is empty. Write something first!"
            return
        }
        
        _pendingAiAction.value = action
        _isAiLoading.value = true
        _aiPreviewText.value = null
        
        viewModelScope.launch {
            try {
                val prompt = when (action) {
                    AiAction.SUMMARIZE -> "Summarize the following text concisely. Do not add conversational filler:\n\n$currentContent"
                    AiAction.FIX_GRAMMAR -> "Fix any spelling and grammar mistakes in the following text. Preserve the original meaning and language. Do not add conversational filler:\n\n$currentContent"
                    AiAction.EXPAND -> "Expand on the following notes. Add relevant details, structure, and professional tone. Do not add conversational filler:\n\n$currentContent"
                    AiAction.REWRITE -> "Rewrite the following text to make it sound more professional, clear, and well-structured. Do not add conversational filler:\n\n$currentContent"
                    AiAction.GENERATE_TITLE -> "Generate a very short, catchy title (max 5 words) for the following text. Output ONLY the title, no quotes or filler:\n\n$currentContent"
                    AiAction.CONTINUE_WRITING -> "Continue the following thought or paragraph logically. Add 1-2 new paragraphs based on the context. Do not repeat what is already there. Output ONLY the continuation:\n\n$currentContent"
                }
                
                val response = GenerativeAiProvider.model.generateContent(prompt)
                val resultText = response.text
                
                if (!resultText.isNullOrBlank()) {
                    _aiPreviewText.value = resultText
                } else {
                    _errorMessage.value = "AI Error: Received an empty response."
                    dismissAiPreview()
                }
            } catch (e: Exception) {
                android.util.Log.e("GeminiAI", "AI Generation Failed", e)
                var errorMsg = "${e.javaClass.simpleName}: ${e.localizedMessage}"
                if (e.cause != null) {
                    errorMsg += "\nCause: ${e.cause?.javaClass?.simpleName}: ${e.cause?.localizedMessage}"
                }
                if (errorMsg.contains("API key not valid", ignoreCase = true) || errorMsg.contains("400", ignoreCase = true) || errorMsg.contains("API_KEY_INVALID", ignoreCase = true)) {
                    errorMsg = "Invalid API Key! The key in your .env file is incorrect. It must start with 'AIza...'. Get a valid key from Google AI Studio."
                }
                _errorMessage.value = "AI Error: $errorMsg"
                dismissAiPreview()
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    suspend fun uploadMediaAndCache(uri: String): String? {
        return repository.uploadMediaAndCache(uri)
    }

    // Kept for backward compatibility with the current UI button
    fun summarizeWithGemini() {
        performAiAction(AiAction.SUMMARIZE)
    }

    companion object {
        fun provideFactory(app: TFPlansApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditNoteViewModel(app.container.notesRepository, app.container.userPreferencesRepository) as T
            }
        }
    }
}

enum class AiAction {
    SUMMARIZE, FIX_GRAMMAR, EXPAND, REWRITE, GENERATE_TITLE, CONTINUE_WRITING
}
