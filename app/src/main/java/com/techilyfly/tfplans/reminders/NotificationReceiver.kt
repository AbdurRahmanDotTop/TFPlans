package com.techilyfly.tfplans.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.techilyfly.tfplans.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val noteId = intent.getStringExtra("NOTE_ID") ?: return

        Log.d(TAG, "Notification action clicked: $action for Note $noteId")

        // Dismiss the notification immediately
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(noteId.hashCode())

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val noteDao = db.noteDao()
                val note = noteDao.getNoteById(noteId)

                when (action) {
                    "MARK_AS_DONE" -> {
                        if (note != null) {
                            val updatedNote = note.copy(
                                isDone = true,
                                reminderTime = null,
                                updatedAt = System.currentTimeMillis()
                            )
                            noteDao.updateNote(updatedNote)
                            ReminderScheduler.cancelReminder(context, noteId)
                            Log.d(TAG, "Note $noteId marked as Done.")
                        }
                    }
                    "SNOOZE" -> {
                        if (note != null) {
                            val snoozeIntervalMs = 10 * 60 * 1000 // 10 minutes snooze
                            val snoozedTime = System.currentTimeMillis() + snoozeIntervalMs
                            val updatedNote = note.copy(
                                reminderTime = snoozedTime,
                                updatedAt = System.currentTimeMillis()
                            )
                            noteDao.updateNote(updatedNote)
                            ReminderScheduler.scheduleReminder(context, updatedNote)
                            Log.d(TAG, "Note $noteId snoozed until $snoozedTime.")
                        }
                    }
                    "DISMISS" -> {
                        Log.d(TAG, "Notification $noteId dismissed.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
