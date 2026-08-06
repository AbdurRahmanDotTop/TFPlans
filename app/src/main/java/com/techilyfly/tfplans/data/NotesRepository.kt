package com.techilyfly.tfplans.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.techilyfly.tfplans.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy

class NotesRepository(
    private val context: Context,
    private val noteDao: NoteDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferencesRepository: UserPreferencesRepository
) {
    fun getActiveNotes(): Flow<List<Note>> = noteDao.getActiveNotes()
    fun getArchivedNotes(): Flow<List<Note>> = noteDao.getArchivedNotes()
    fun getReminderNotes(): Flow<List<Note>> = noteDao.getReminderNotes()
    fun getNotesCount(): Flow<Int> = noteDao.getNotesCount()

    suspend fun getNoteById(id: String): Note? = noteDao.getNoteById(id)

    private fun scheduleImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ImmediateSyncWorker",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    suspend fun saveNote(note: Note) {
        val updatedNote = note.copy(updatedAt = System.currentTimeMillis(), isSynced = false)
        noteDao.insertNote(updatedNote)
        if (preferencesRepository.cloudBackup.value) {
            syncNoteToCloud(updatedNote)
            scheduleImmediateSync()
        }
        // Sync scheduled reminders with system AlarmManager
        ReminderScheduler.scheduleReminder(context, updatedNote)
    }

    suspend fun deleteNote(note: Note) {
        val deletedNote = note.copy(isDeleted = true, updatedAt = System.currentTimeMillis(), isSynced = false)
        noteDao.updateNote(deletedNote)
        if (preferencesRepository.cloudBackup.value) {
            syncNoteToCloud(deletedNote)
            scheduleImmediateSync()
        }
        // Cancel reminder for deleted note
        ReminderScheduler.cancelReminder(context, note.id)
    }

    private var snapshotListenerRegistration: ListenerRegistration? = null
    private var settingsListenerRegistration: ListenerRegistration? = null

    fun startRealtimeSync() {
        val user = auth.currentUser ?: return
        if (snapshotListenerRegistration != null) return

        CoroutineScope(Dispatchers.IO).launch {
            purgeCorruptedBlankNotes()
        }

        val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")
        snapshotListenerRegistration = userNotesRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val localNotes = noteDao.getAllNotes().associateBy { it.id }
                    val toInsertOrUpdate = mutableListOf<Note>()

                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val remoteNote = mapDocumentToNote(doc) ?: continue
                        val localNote = localNotes[remoteNote.id]

                        when (change.type) {
                            com.google.firebase.firestore.DocumentChange.Type.ADDED,
                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                if (localNote == null) {
                                    // New note from remote
                                    toInsertOrUpdate.add(remoteNote.copy(isSynced = true))
                                } else if (!localNote.isSynced) {
                                    // Conflict! Local has pending changes.
                                    // Resolution: Last write wins based on updatedAt
                                    if (remoteNote.updatedAt > localNote.updatedAt) {
                                        toInsertOrUpdate.add(remoteNote.copy(isSynced = true))
                                    }
                                } else if (localNote != remoteNote.copy(isSynced = true)) {
                                    // Local is synced, but data differs (e.g. updated from another device)
                                    toInsertOrUpdate.add(remoteNote.copy(isSynced = true))
                                }
                            }
                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                // Hard deleted on remote, so delete locally
                                noteDao.hardDeleteNote(remoteNote.id)
                            }
                        }
                    }

                    if (toInsertOrUpdate.isNotEmpty()) {
                        noteDao.insertNotes(toInsertOrUpdate)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotesRepository", "Realtime sync error: ${e.message}")
                }
            }
        }

        val settingsRef = firestore.collection("Users").document(user.uid).collection("Settings").document("preferences")
        settingsListenerRegistration = settingsRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            if (snapshot.exists()) {
                val remoteTheme = snapshot.getString("themeMode")
                val remoteFontSize = snapshot.getString("fontSize")
                val remoteDefaultView = snapshot.getString("defaultView")
                val remoteUpdatedAt = snapshot.getLong("updatedAt") ?: 0L

                val localUpdatedAt = preferencesRepository.lastSyncedTime.value
                if (remoteUpdatedAt > localUpdatedAt) {
                    remoteTheme?.let { preferencesRepository.setThemeMode(it) }
                    remoteFontSize?.let { preferencesRepository.setFontSize(it) }
                    remoteDefaultView?.let { preferencesRepository.setDefaultView(it) }
                    preferencesRepository.updateLastSyncedTime(remoteUpdatedAt)
                }
            }
        }
    }

    fun stopRealtimeSync() {
        snapshotListenerRegistration?.remove()
        snapshotListenerRegistration = null
        
        settingsListenerRegistration?.remove()
        settingsListenerRegistration = null
    }

    suspend fun syncAllNotesWithCloud() = withContext(Dispatchers.IO + NonCancellable) {
        val user = auth.currentUser ?: return@withContext
        try {
            val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")

            // 1. Get local unsynced notes and push to cloud using a WriteBatch
            val unsyncedNotes = noteDao.getUnsyncedNotes()
            if (unsyncedNotes.isNotEmpty()) {
                val batch = firestore.batch()
                for (note in unsyncedNotes) {
                    batch.set(userNotesRef.document(note.id), note)
                }
                batch.commit().await()
                
                // Only mark as synced if the note hasn't been edited locally during the upload
                for (note in unsyncedNotes) {
                    noteDao.markAsSyncedIfUnchanged(note.id, note.updatedAt)
                }
            }

            // 3. Sync settings safely
            try {
                val settingsRef = firestore.collection("Users").document(user.uid).collection("Settings").document("preferences")
                val remoteSettingsDoc = settingsRef.get().await()
                if (remoteSettingsDoc.exists()) {
                    val remoteTheme = remoteSettingsDoc.getString("themeMode")
                    val remoteFontSize = remoteSettingsDoc.getString("fontSize")
                    val remoteDefaultView = remoteSettingsDoc.getString("defaultView")
                    val remoteUpdatedAt = remoteSettingsDoc.getLong("updatedAt") ?: 0L

                    val localUpdatedAt = preferencesRepository.lastSyncedTime.value
                    if (remoteUpdatedAt > localUpdatedAt) {
                        remoteTheme?.let { preferencesRepository.setThemeMode(it) }
                        remoteFontSize?.let { preferencesRepository.setFontSize(it) }
                        remoteDefaultView?.let { preferencesRepository.setDefaultView(it) }
                    }
                }

                val settingsMap = mapOf(
                    "themeMode" to preferencesRepository.themeMode.value,
                    "fontSize" to preferencesRepository.fontSize.value,
                    "defaultView" to preferencesRepository.defaultView.value,
                    "updatedAt" to System.currentTimeMillis()
                )
                settingsRef.set(settingsMap).addOnFailureListener { e ->
                    android.util.Log.e("NotesRepository", "Failed to sync settings to cloud: ${e.message}")
                }
            } catch (settingsError: Exception) {
                android.util.Log.e("NotesRepository", "Settings sync skipped: ${settingsError.message}")
            }

            preferencesRepository.updateLastSyncedTime()
            startRealtimeSync()
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Sync failed: ${e.message}")
        }
    }

    private fun syncNoteToCloud(note: Note) {
        val user = auth.currentUser ?: return
        firestore.collection("Users").document(user.uid)
            .collection("Notes").document(note.id)
            .set(note)
            .addOnSuccessListener {
                CoroutineScope(Dispatchers.IO).launch {
                    noteDao.markAsSyncedIfUnchanged(note.id, note.updatedAt)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotesRepository", "Failed to sync note to cloud: ${e.message}. If this is a permission error, check your Firestore Security Rules.")
            }
    }

    suspend fun purgeCorruptedBlankNotes() = withContext(Dispatchers.IO + NonCancellable) {
        try {
            val user = auth.currentUser
            val allNotes = noteDao.getAllNotes()
            val blankNotes = allNotes.filter { note ->
                note.title.isBlank() && note.category.isBlank() && (note.reminderTime == null || note.reminderTime == 0L) &&
                note.content.lines().filter { line ->
                    val trimmed = line.trim()
                    trimmed != "- [ ]" && trimmed != "- [x]"
                }.joinToString("").trim().isEmpty()
            }
            
            if (blankNotes.isNotEmpty()) {
                // Delete from remote
                if (user != null) {
                    val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")
                    val batch = firestore.batch()
                    for (note in blankNotes) {
                        batch.delete(userNotesRef.document(note.id))
                    }
                    batch.commit().await()
                }
                // Delete from local
                noteDao.hardDeleteBlankNotes()
            }
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Failed to purge corrupted blank notes: ${e.message}")
        }
    }
    suspend fun forceRecoverFromCloud() = withContext(Dispatchers.IO + NonCancellable) {
        val user = auth.currentUser ?: return@withContext
        try {
            val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")
            val remoteSnapshot = userNotesRef.get().await()
            val remoteNotes = remoteSnapshot.documents.mapNotNull { 
                mapDocumentToNote(it)?.copy(isSynced = true) 
            }
            
            if (remoteNotes.isNotEmpty()) {
                noteDao.insertNotes(remoteNotes)
            }
            recoverSettingsFromCloud()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun backupSettingsToCloud() = withContext(Dispatchers.IO + NonCancellable) {
        val user = auth.currentUser ?: return@withContext
        try {
            val settingsRef = firestore.collection("Users").document(user.uid).collection("Settings").document("preferences")
            val settingsMap = mapOf(
                "themeMode" to preferencesRepository.themeMode.value,
                "fontSize" to preferencesRepository.fontSize.value,
                "defaultView" to preferencesRepository.defaultView.value,
                "updatedAt" to System.currentTimeMillis()
            )
            settingsRef.set(settingsMap).await()
            preferencesRepository.updateLastSyncedTime()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun recoverSettingsFromCloud() = withContext(Dispatchers.IO + NonCancellable) {
        val user = auth.currentUser ?: return@withContext
        try {
            val settingsRef = firestore.collection("Users").document(user.uid).collection("Settings").document("preferences")
            val remoteSettingsDoc = settingsRef.get().await()
            if (remoteSettingsDoc.exists()) {
                val remoteTheme = remoteSettingsDoc.getString("themeMode")
                val remoteFontSize = remoteSettingsDoc.getString("fontSize")
                val remoteDefaultView = remoteSettingsDoc.getString("defaultView")
                
                remoteTheme?.let { preferencesRepository.setThemeMode(it) }
                remoteFontSize?.let { preferencesRepository.setFontSize(it) }
                remoteDefaultView?.let { preferencesRepository.setDefaultView(it) }
                preferencesRepository.updateLastSyncedTime()
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun mapDocumentToNote(doc: com.google.firebase.firestore.DocumentSnapshot): Note? {
        if (!doc.exists()) return null
        return try {
            Note(
                id = doc.id,
                title = doc.getString("title") ?: "",
                content = doc.getString("content") ?: "",
                color = doc.getLong("color")?.toInt() ?: 0,
                category = doc.getString("category") ?: "",
                reminderTime = doc.getLong("reminderTime"),
                reminderRepeat = doc.getString("reminderRepeat"),
                isDone = doc.getBoolean("isDone") ?: doc.getBoolean("done") ?: false,
                isPinned = doc.getBoolean("isPinned") ?: doc.getBoolean("pinned") ?: false,
                isArchived = doc.getBoolean("isArchived") ?: doc.getBoolean("archived") ?: false,
                isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("deleted") ?: false,
                createdAt = doc.getLong("createdAt") ?: 0L,
                updatedAt = doc.getLong("updatedAt") ?: 0L
            )
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Failed to map note: ${e.message}")
            null
        }
    }

    suspend fun cleanupOldDeletedNotes() = withContext(Dispatchers.IO + NonCancellable) {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            
            // Fetch notes that are about to be hard deleted locally
            val oldNotes = noteDao.getAllNotes().filter { it.isDeleted && it.updatedAt < thirtyDaysAgo }
            
            // Delete them from Firebase FIRST so they don't sync back
            val user = auth.currentUser
            if (user != null && oldNotes.isNotEmpty()) {
                val batch = firestore.batch()
                val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")
                for (note in oldNotes) {
                    batch.delete(userNotesRef.document(note.id))
                }
                batch.commit().await()
            }
            
            // Delete them locally
            noteDao.deleteOldSoftDeletedNotes(thirtyDaysAgo)
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Failed to cleanup old notes: ${e.message}")
        }
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO + NonCancellable) {
        try {
            noteDao.deleteAllNotes()
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Failed to clear local data: ${e.message}")
        }
    }

    suspend fun deleteAllCloudData() = withContext(Dispatchers.IO + NonCancellable) {
        val user = auth.currentUser ?: return@withContext
        try {
            val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")
            val snapshot = userNotesRef.get().await()
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            
            val settingsRef = firestore.collection("Users").document(user.uid).collection("Settings").document("preferences")
            settingsRef.delete().await()
            
            firestore.collection("Users").document(user.uid).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Failed to clear cloud data: ${e.message}")
            throw e
        }
    }
}
