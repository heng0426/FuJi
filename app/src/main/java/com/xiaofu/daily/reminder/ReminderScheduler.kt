package com.xiaofu.daily.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.data.SettingsKeys
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** 每日 09:00 检查一次到期项，命中 T-0 ~ T-n 时发通知 */
class DueReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val repo = AppContainer.repository
        val enabled = repo.setting(SettingsKeys.NOTIFY_ENABLED) ?: "true"
        if (enabled == "false") return Result.success()

        val hour = repo.setting(SettingsKeys.NOTIFY_HOUR)?.toIntOrNull() ?: 9
        val now = LocalDateTime.now()
        if (now.hour < hour) return Result.success()

        val today = now.toLocalDate().toEpochDay().toString()
        if (repo.setting(SettingsKeys.NOTIFY_LAST_DAY) == today) return Result.success()

        val daysAhead = repo.setting(SettingsKeys.NOTIFY_DAYS_AHEAD)?.toIntOrNull() ?: 3
        val cards = repo.observeDueCards()
            .first()
            .filter { it.daysLeft in 0..daysAhead.toLong() }

        repo.putSetting(SettingsKeys.NOTIFY_LAST_DAY, today)
        if (cards.isNotEmpty()) {
            ReminderNotifier.show(applicationContext, cards)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}

object ReminderScheduler {

    private const val WORK_NAME = "due_reminder_hourly"
    private const val LEGACY_WORK_NAME = "due_reminder_daily"

    /**
     * 每小时整点检查一次：达到用户设置的提醒小时后，当天只处理一次。
     * 这样提醒时间可配置，且不依赖精确闹钟权限。
     */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        val nextHour = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1)
        val delay = Duration.between(now, nextHour).toMillis().coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(LEGACY_WORK_NAME)
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
