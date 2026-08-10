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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val preferencesRepository: UserPreferencesRepository,
    private val driveMediaManager: DriveMediaManager
) {
    val isInitialSyncCompleted = MutableStateFlow(false)

    suspend fun initializeDriveFolders() {
        try {
            val result = driveMediaManager.initializeFolders()
            if (result == null) {
                throw Exception("Failed to create or access Google Drive folders. Please check your network and permissions.")
            }
        } catch (e: DrivePermissionDeniedException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Failed to initialize Drive folders: ${e.message}")
            throw Exception("Failed to initialize Google Drive: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun uploadMediaAndCache(uri: String): String? {
        return driveMediaManager.uploadMediaAndCache(uri)
    }

    fun getActiveNotes(): Flow<List<Note>> = noteDao.getActiveNotes()
    fun getArchivedNotes(): Flow<List<Note>> = noteDao.getArchivedNotes()
    fun getReminderNotes(): Flow<List<Note>> = noteDao.getReminderNotes()
    fun getNotesCount(): Flow<Int> = noteDao.getNotesCount()

    suspend fun hasUnsyncedData(): Boolean {
        return noteDao.getUnsyncedNotesCount() > 0
    }

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
                                    val downloadedContent = driveMediaManager.processNoteForDownload(remoteNote.content)
                                    toInsertOrUpdate.add(remoteNote.copy(content = downloadedContent, isSynced = true))
                                } else if (!localNote.isSynced) {
                                    // Conflict! Local has pending changes.
                                    // Resolution: Last write wins based on updatedAt
                                    if (remoteNote.updatedAt > localNote.updatedAt) {
                                        val downloadedContent = driveMediaManager.processNoteForDownload(remoteNote.content)
                                        toInsertOrUpdate.add(remoteNote.copy(content = downloadedContent, isSynced = true))
                                    }
                                } else if (localNote != remoteNote.copy(isSynced = true)) {
                                    // Local is synced, but data differs (e.g. updated from another device)
                                    val downloadedContent = driveMediaManager.processNoteForDownload(remoteNote.content)
                                    toInsertOrUpdate.add(remoteNote.copy(content = downloadedContent, isSynced = true))
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
                val remoteDriveRootId = snapshot.getString("driveFolderRootId")
                val remoteDriveImagesId = snapshot.getString("driveFolderImagesId")
                val remoteDriveRecordingsId = snapshot.getString("driveFolderRecordingsId")
                val remoteUpdatedAt = snapshot.getLong("updatedAt") ?: 0L

                val localUpdatedAt = preferencesRepository.lastSyncedTime.value
                if (remoteUpdatedAt > localUpdatedAt) {
                    remoteTheme?.let { preferencesRepository.setThemeMode(it) }
                    remoteFontSize?.let { preferencesRepository.setFontSize(it) }
                    remoteDefaultView?.let { preferencesRepository.setDefaultView(it) }
                    preferencesRepository.setDriveFolderIds(remoteDriveRootId, remoteDriveImagesId, remoteDriveRecordingsId)
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

    suspend fun syncAllNotesWithCloud(): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        val user = auth.currentUser ?: return@withContext false
        try {
            val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")

            // 1. Get local unsynced notes and push to cloud using a WriteBatch
            val unsyncedNotes = noteDao.getUnsyncedNotes()
            if (unsyncedNotes.isNotEmpty()) {
                val batch = firestore.batch()
                val successfullySyncedNotes = mutableListOf<Note>()
                for (note in unsyncedNotes) {
                    val processedContent = driveMediaManager.processNoteForUpload(note.content)
                    
                    // If content still has local files, upload failed, so don't mark as synced and DON'T upload to Firestore
                    if (!processedContent.contains("\"uri\":\"file://") && !processedContent.contains("\"uri\":\"/")) {
                        val processedNote = note.copy(content = processedContent)
                        batch.set(userNotesRef.document(note.id), processedNote)
                        successfullySyncedNotes.add(note)
                    } else {
                        android.util.Log.e("NotesRepository", "Skipping note ${note.id} sync because media upload failed")
                    }
                }
                if (successfullySyncedNotes.isNotEmpty()) {
                    batch.commit().await()
                }
                
                // Only mark as synced if the note hasn't been edited locally during the upload
                for (note in successfullySyncedNotes) {
                    noteDao.markAsSyncedIfUnchanged(note.id, note.updatedAt)
                }
                
                if (successfullySyncedNotes.size != unsyncedNotes.size) {
                    android.util.Log.e("NotesRepository", "Some notes failed to sync (media upload failed). Aborting sync success.")
                    return@withContext false
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
                    val remoteDriveRootId = remoteSettingsDoc.getString("driveFolderRootId")
                    val remoteDriveImagesId = remoteSettingsDoc.getString("driveFolderImagesId")
                    val remoteDriveRecordingsId = remoteSettingsDoc.getString("driveFolderRecordingsId")
                    val remoteUpdatedAt = remoteSettingsDoc.getLong("updatedAt") ?: 0L

                    val localUpdatedAt = preferencesRepository.lastSyncedTime.value
                    if (remoteUpdatedAt > localUpdatedAt) {
                        remoteTheme?.let { preferencesRepository.setThemeMode(it) }
                        remoteFontSize?.let { preferencesRepository.setFontSize(it) }
                        remoteDefaultView?.let { preferencesRepository.setDefaultView(it) }
                        preferencesRepository.setDriveFolderIds(remoteDriveRootId, remoteDriveImagesId, remoteDriveRecordingsId)
                    }
                }

                val settingsMap = mapOf(
                    "themeMode" to preferencesRepository.themeMode.value,
                    "fontSize" to preferencesRepository.fontSize.value,
                    "defaultView" to preferencesRepository.defaultView.value,
                    "driveFolderRootId" to preferencesRepository.driveFolderRootId.value,
                    "driveFolderImagesId" to preferencesRepository.driveFolderImagesId.value,
                    "driveFolderRecordingsId" to preferencesRepository.driveFolderRecordingsId.value,
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
            return@withContext true
        } catch (e: DrivePermissionDeniedException) {
            android.util.Log.w("NotesRepository", "Drive permission denied during sync. Ignoring until permission granted.")
            return@withContext false
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.w("NotesRepository", "Offline: Sync failed due to network. Will retry later.")
            return@withContext false
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Sync failed: ${e.message}")
            return@withContext false
        }
    }

    private fun syncNoteToCloud(note: Note) {
        val user = auth.currentUser ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val processedContent = driveMediaManager.processNoteForUpload(note.content)
                val processedNote = note.copy(content = processedContent)
                
                if (!processedContent.contains("\"uri\":\"file://") && !processedContent.contains("\"uri\":\"/")) {
                    firestore.collection("Users").document(user.uid)
                        .collection("Notes").document(note.id)
                        .set(processedNote)
                        .addOnSuccessListener {
                            CoroutineScope(Dispatchers.IO).launch {
                                noteDao.markAsSyncedIfUnchanged(note.id, note.updatedAt)
                            }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("NotesRepository", "Failed to sync note to cloud: ${e.message}")
                        }
                } else {
                    android.util.Log.e("NotesRepository", "Skipping syncNoteToCloud for ${note.id} because media upload failed")
                }
            } catch (e: Exception) {
                android.util.Log.e("NotesRepository", "Failed to process media for note: ${e.message}")
            }
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
                mapDocumentToNote(it)
            }
            
            if (remoteNotes.isNotEmpty()) {
                val processedNotes = remoteNotes.map { note ->
                    val downloadedContent = driveMediaManager.processNoteForDownload(note.content)
                    note.copy(content = downloadedContent, isSynced = true)
                }
                noteDao.insertNotes(processedNotes)
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
                "driveFolderRootId" to preferencesRepository.driveFolderRootId.value,
                "driveFolderImagesId" to preferencesRepository.driveFolderImagesId.value,
                "driveFolderRecordingsId" to preferencesRepository.driveFolderRecordingsId.value,
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
                val remoteDriveRootId = remoteSettingsDoc.getString("driveFolderRootId")
                val remoteDriveImagesId = remoteSettingsDoc.getString("driveFolderImagesId")
                val remoteDriveRecordingsId = remoteSettingsDoc.getString("driveFolderRecordingsId")
                
                remoteTheme?.let { preferencesRepository.setThemeMode(it) }
                remoteFontSize?.let { preferencesRepository.setFontSize(it) }
                remoteDefaultView?.let { preferencesRepository.setDefaultView(it) }
                preferencesRepository.setDriveFolderIds(remoteDriveRootId, remoteDriveImagesId, remoteDriveRecordingsId)
                preferencesRepository.updateLastSyncedTime()
            }
        } catch (e: Exception) {
            throw e
        }
    }
    
    suspend fun initialSyncWithCloud() = withContext(Dispatchers.IO + NonCancellable) {
        val user = auth.currentUser ?: return@withContext
        try {
            val userNotesRef = firestore.collection("Users").document(user.uid).collection("Notes")
            val snapshot = userNotesRef.get().await()
            val remoteNotes = snapshot.documents.mapNotNull { mapDocumentToNote(it) }
            
            if (remoteNotes.isNotEmpty()) {
                val processedNotes = remoteNotes.map { note ->
                    val downloadedContent = driveMediaManager.processNoteForDownload(note.content)
                    note.copy(content = downloadedContent, isSynced = true)
                }
                
                val localNotes = noteDao.getAllNotes().associateBy { it.id }
                val toInsertOrUpdate = mutableListOf<Note>()
                
                for (remoteNote in processedNotes) {
                    val localNote = localNotes[remoteNote.id]
                    if (localNote == null || (!localNote.isSynced && remoteNote.updatedAt > localNote.updatedAt) || (localNote.isSynced && localNote != remoteNote)) {
                        toInsertOrUpdate.add(remoteNote)
                    }
                }
                
                if (toInsertOrUpdate.isNotEmpty()) {
                    noteDao.insertNotes(toInsertOrUpdate)
                }
            }
            
            isInitialSyncCompleted.value = true
        } catch (e: Exception) {
            android.util.Log.e("NotesRepository", "Failed initial sync: ${e.message}")
            isInitialSyncCompleted.value = true // Ensure UI doesn't hang forever
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
            
            // Clear locally cached media files to prevent data leak across accounts
            val mediaDir = java.io.File(context.filesDir, "drive_media")
            if (mediaDir.exists()) {
                mediaDir.deleteRecursively()
            }
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
