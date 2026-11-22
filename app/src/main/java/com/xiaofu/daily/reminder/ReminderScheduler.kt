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

        val daysAhead = repo.setting(SettingsKeys.NOTIFY_DAYS_AHEAD)?.toIntOrNull() ?: 3
        val cards = repo.observeDueCards()
            .first()
            .filter { it.daysLeft in 0..daysAhead.toLong() }

        if (cards.isNotEmpty()) {
            ReminderNotifier.show(applicationContext, cards)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}

object ReminderScheduler {

    private const val WORK_NAME = "due_reminder_daily"

    /** 下次 09:00 首次执行，之后每 24 小时一次；重复调用不会重置已有计划 */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
