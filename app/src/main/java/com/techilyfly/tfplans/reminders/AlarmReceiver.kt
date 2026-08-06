package com.techilyfly.tfplans.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.techilyfly.tfplans.MainActivity
import com.techilyfly.tfplans.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        const val CHANNEL_ID = "reminder_alarm_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra("NOTE_ID") ?: return
        val noteTitle = intent.getStringExtra("NOTE_TITLE") ?: "TFPlans Reminder"
        val rawContent = intent.getStringExtra("NOTE_CONTENT") ?: ""
        val noteContent = extractPlainText(rawContent)

        Log.d(TAG, "Alarm triggered for Note: $noteId, Title: $noteTitle")

        // 1. Show high-priority notification with actions
        showNotification(context, noteId, noteTitle, noteContent)

        // 2. Handle repeating logic (Run in background)
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val noteDao = db.noteDao()
                val note = noteDao.getNoteById(noteId)
                val reminderTime = note?.reminderTime
                if (note != null && reminderTime != null && !note.isDone && !note.isDeleted) {
                    val nextTime = ReminderScheduler.calculateNextOccurrence(reminderTime, note.reminderRepeat)
                    if (nextTime != null) {
                        // Reschedule next repeating alarm
                        val updatedNote = note.copy(reminderTime = nextTime, updatedAt = System.currentTimeMillis())
                        noteDao.updateNote(updatedNote)
                        ReminderScheduler.scheduleReminder(context, updatedNote)
                        Log.d(TAG, "Rescheduled repeating note ${note.id} for next time $nextTime")
                    } else {
                        // No repetition, leave reminderTime in DB so it shows on UI
                        // It will be cleared when the user opens the note in EditNoteScreen
                        Log.d(TAG, "Non-repeating reminder completed, left reminderTime in DB for ${note.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing repeating alarm in receiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, noteId: String, title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Create Channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Task & Note Reminders"
            val descriptionText = "Notifications for scheduled note and task reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            // Set alarm audio attributes on the channel
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action: Open Note
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("NOTE_ID", noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            noteId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark as Done
        val doneIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "MARK_AS_DONE"
            putExtra("NOTE_ID", noteId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze (10 minutes)
        val snoozeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "SNOOZE"
            putExtra("NOTE_ID", noteId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss
        val dismissIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "DISMISS"
            putExtra("NOTE_ID", noteId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.hashCode() + 3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title.ifBlank() { "Reminder" })
            .setContentText(content.ifBlank() { "You have an active reminder." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setFullScreenIntent(openPendingIntent, true) // High priority / alarm style
            .addAction(android.R.drawable.ic_menu_edit, "Open", openPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Mark Done", donePendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze (10m)", snoozePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)

        val notification = notificationBuilder.build().apply {
            flags = flags or android.app.Notification.FLAG_INSISTENT
        }

        notificationManager.notify(noteId.hashCode(), notification)
    }

    private fun extractPlainText(content: String): String {
        if (content.trimStart().startsWith("[")) {
            try {
                val jsonArray = org.json.JSONArray(content)
                val builder = java.lang.StringBuilder()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    when (obj.optString("type")) {
                        "text" -> {
                            val text = obj.optString("text", "")
                            builder.append(text).append("\n")
                        }
                        "checklist" -> {
                            val text = obj.optString("text", "")
                            val isChecked = obj.optBoolean("checked", false)
                            builder.append(if (isChecked) "[x] " else "[ ] ").append(text).append("\n")
                        }
                        "image" -> builder.append("[Image]\n")
                        "audio" -> builder.append("[Audio]\n")
                    }
                }
                return builder.toString().trimEnd()
            } catch (e: Exception) {
                return content
            }
        }
        return content
    }
}
