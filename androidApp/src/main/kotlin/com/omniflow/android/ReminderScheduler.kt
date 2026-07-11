package com.omniflow.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.omniflow.shared.domain.model.Reminder
import com.omniflow.shared.domain.model.ReminderScheduleKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)

    fun sync(reminders: List<Reminder>) {
        val active = reminders.filterNot(Reminder::paused)
        val previous = preferences.getStringSet("ids", emptySet()).orEmpty()
        (previous - active.mapTo(mutableSetOf(), Reminder::id)).forEach(::cancel)
        active.forEach(::schedule)
        preferences.edit().putStringSet("ids", active.mapTo(mutableSetOf(), Reminder::id)).apply()
    }

    fun schedule(reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("id", reminder.id)
            .putExtra("name", reminder.name)
            .putExtra("amount", reminder.amount?.minor)
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextOccurrence(reminder).toInstant().toEpochMilli(),
            PendingIntent.getBroadcast(context, reminder.id.hashCode(), intent, pendingFlags()),
        )
    }

    private fun cancel(id: String) {
        val pending = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ReminderReceiver::class.java),
            pendingFlags() or PendingIntent.FLAG_NO_CREATE,
        )
        pending?.let(alarms::cancel)
    }

    private fun nextOccurrence(reminder: Reminder): ZonedDateTime {
        val now = ZonedDateTime.now(CHINA_ZONE)
        val time = LocalTime.of(9, 0)
        fun future(date: LocalDate): ZonedDateTime = date.atTime(time).atZone(CHINA_ZONE)
        fun monthly(day: Int, monthOffset: Long = 0): ZonedDateTime {
            var month = YearMonth.from(now).plusMonths(monthOffset)
            var candidate = future(month.atDay(day.coerceAtMost(month.lengthOfMonth())))
            if (!candidate.isAfter(now)) {
                month = month.plusMonths(1)
                candidate = future(month.atDay(day.coerceAtMost(month.lengthOfMonth())))
            }
            return candidate
        }
        return when (reminder.schedule.kind) {
            ReminderScheduleKind.DAILY -> future(now.toLocalDate()).takeIf { it.isAfter(now) } ?: future(now.toLocalDate().plusDays(1))
            ReminderScheduleKind.WEEKLY -> {
                val target = DayOfWeek.of(reminder.schedule.dayOfWeek ?: 1)
                var date = now.toLocalDate().plusDays(((target.value - now.dayOfWeek.value + 7) % 7).toLong())
                if (!future(date).isAfter(now)) date = date.plusWeeks(1)
                future(date)
            }
            ReminderScheduleKind.MONTHLY,
            ReminderScheduleKind.FIXED_REPAYMENT_DAY -> monthly(reminder.schedule.dayOfMonth ?: 1)
            ReminderScheduleKind.DAYS_AFTER_STATEMENT -> {
                fun due(month: YearMonth): ZonedDateTime {
                    val statement = month.atDay((reminder.schedule.dayOfMonth ?: 1).coerceAtMost(month.lengthOfMonth()))
                    return future(statement.plusDays((reminder.schedule.daysAfter ?: 0).toLong()))
                }
                due(YearMonth.from(now)).takeIf { it.isAfter(now) } ?: due(YearMonth.from(now).plusMonths(1))
            }
            ReminderScheduleKind.YEARLY -> {
                val month = reminder.schedule.month ?: 1
                val day = reminder.schedule.dayOfMonth ?: 1
                fun candidate(year: Int): ZonedDateTime {
                    val yearMonth = YearMonth.of(year, month)
                    return future(yearMonth.atDay(day.coerceAtMost(yearMonth.lengthOfMonth())))
                }
                candidate(now.year).takeIf { it.isAfter(now) } ?: candidate(now.year + 1)
            }
        }
    }

    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private companion object {
        val CHINA_ZONE: ZoneId = ZoneId.systemDefault()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val name = intent.getStringExtra("name") ?: "记账提醒"
        val amount = intent.extras?.takeIf { it.containsKey("amount") }?.getLong("amount")
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "提醒与周期事项", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val openApp = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notifications.notify(
            id.hashCode(),
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(name)
                .setContentText(amount?.let { "提醒金额 ¥${it / 100}.${kotlin.math.abs(it % 100).toString().padStart(2, '0')}" } ?: "到时间了")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            val app = context.applicationContext as OmniFlowApplication
            app.sharedApp.reminders.observe().first().getOrNull()
                ?.firstOrNull { it.id == id && !it.paused }
                ?.let { ReminderScheduler(context).schedule(it) }
            pending.finish()
        }
    }

    private companion object {
        const val CHANNEL_ID = "omniflow-reminders"
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            val app = context.applicationContext as OmniFlowApplication
            app.sharedApp.reminders.observe().first().getOrNull()?.let { ReminderScheduler(context).sync(it) }
            pending.finish()
        }
    }
}
