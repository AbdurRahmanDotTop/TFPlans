package com.techilyfly.tfplans.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.techilyfly.tfplans.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Device rebooted. Rescheduling all active reminders...")

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val noteDao = db.noteDao()
                val allNotes = noteDao.getAllNotes()

                val now = System.currentTimeMillis()
                for (note in allNotes) {
                    if (note.isDeleted || note.isDone) continue

                    val reminderTime = note.reminderTime
                    if (reminderTime != null && reminderTime > 0) {
                        if (reminderTime > now) {
                            // Future reminder: reschedule as is
                            ReminderScheduler.scheduleReminder(context, note)
                            Log.d(TAG, "Rescheduled future reminder for note ${note.id} at $reminderTime")
                        } else {
                            // Past reminder: check if repeating and advance, otherwise clear
                            val nextTime = ReminderScheduler.calculateNextOccurrence(reminderTime, note.reminderRepeat)
                            if (nextTime != null) {
                                val updatedNote = note.copy(reminderTime = nextTime, updatedAt = System.currentTimeMillis())
                                noteDao.updateNote(updatedNote)
                                ReminderScheduler.scheduleReminder(context, updatedNote)
                                Log.d(TAG, "Advanced past repeating reminder for note ${note.id} to $nextTime")
                            } else {
                                // Clear elapsed non-repeating reminder
                                val updatedNote = note.copy(reminderTime = null, updatedAt = System.currentTimeMillis())
                                noteDao.updateNote(updatedNote)
                                Log.d(TAG, "Cleared past non-repeating reminder for note ${note.id}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling boot reminders: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
