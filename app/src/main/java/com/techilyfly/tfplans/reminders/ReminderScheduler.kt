package com.techilyfly.tfplans.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.techilyfly.tfplans.MainActivity
import com.techilyfly.tfplans.data.Note
import java.util.Calendar

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    fun scheduleReminder(context: Context, note: Note) {
        val reminderTime = note.reminderTime ?: return
        if (reminderTime <= System.currentTimeMillis() || note.isDeleted || note.isDone) {
            cancelReminder(context, note.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("NOTE_ID", note.id)
            putExtra("NOTE_TITLE", note.title)
            putExtra("NOTE_CONTENT", note.content)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Use setAlarmClock as the primary exact scheduling mechanism because it is highly reliable and doesn't require SCHEDULE_EXACT_ALARM permission starting Android 12+
            val showIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("NOTE_ID", note.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                note.id.hashCode() + 9,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val info = AlarmManager.AlarmClockInfo(reminderTime, showPendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
            Log.d(TAG, "Scheduled alarm via setAlarmClock for note ${note.id} at $reminderTime")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm via setAlarmClock: ${e.message}. Trying fallback...", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime,
                            pendingIntent
                        )
                        Log.d(TAG, "Fallback scheduled exact alarm for note ${note.id} at $reminderTime")
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime,
                            pendingIntent
                        )
                        Log.d(TAG, "Fallback scheduled in-exact alarm for note ${note.id} at $reminderTime")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Fallback scheduled exact alarm for note ${note.id} at $reminderTime")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed all exact alarm scheduling attempts: ${ex.message}", ex)
                // Ultimately fallback to non-exact standard set
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Final fallback scheduled alarm for note ${note.id}")
                } catch (anyEx: Exception) {
                    Log.e(TAG, "Critical alarm scheduling crash: ${anyEx.message}", anyEx)
                }
            }
        }
    }

    fun cancelReminder(context: Context, noteId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for note $noteId")
        }
    }

    fun calculateNextOccurrence(currentTime: Long, repeatPattern: String?): Long? {
        if (repeatPattern.isNullOrEmpty() || repeatPattern == "NONE") return null
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTime
        }
        
        val now = System.currentTimeMillis()
        while (calendar.timeInMillis <= now) {
            when (repeatPattern) {
                "DAILY" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> calendar.add(Calendar.MONTH, 1)
                "YEARLY" -> calendar.add(Calendar.YEAR, 1)
                else -> return null
            }
        }
        return calendar.timeInMillis
    }
}
