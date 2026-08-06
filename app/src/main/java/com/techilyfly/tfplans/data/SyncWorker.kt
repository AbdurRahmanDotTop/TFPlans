package com.techilyfly.tfplans.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.techilyfly.tfplans.TFPlansApplication

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as TFPlansApplication
            val repository = app.container.notesRepository
            repository.purgeCorruptedBlankNotes() // Hard-delete any corrupted blank notes locally and from cloud before sync
            repository.syncAllNotesWithCloud()
            repository.cleanupOldDeletedNotes()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
