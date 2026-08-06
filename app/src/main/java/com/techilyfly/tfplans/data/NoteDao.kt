package com.techilyfly.tfplans.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE reminderTime IS NOT NULL AND reminderTime > 0 AND isDeleted = 0 ORDER BY reminderTime ASC")
    fun getReminderNotes(): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    fun getNotesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    suspend fun getNoteCountSync(): Int

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getAllNonDeletedNotesList(): List<Note>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE isSynced = 0")
    suspend fun getUnsyncedNotes(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Update
    suspend fun updateNote(note: Note)

    @Query("UPDATE notes SET isSynced = 1 WHERE id = :id AND updatedAt = :syncedUpdatedAt")
    suspend fun markAsSyncedIfUnchanged(id: String, syncedUpdatedAt: Long)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun deleteOldSoftDeletedNotes(threshold: Long)

    @Query("DELETE FROM notes WHERE title = '' AND (content = '' OR content = '- [ ] ' OR content = '- [x] ') AND reminderTime IS NULL AND category = ''")
    suspend fun hardDeleteBlankNotes()

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDeleteNote(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
