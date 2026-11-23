package com.xiaofu.daily.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 每天 00:05 刷新一次全部小组件。
 * 组件自身只有 30 分钟周期刷新，跨零点后「今日」汇总可能滞后，这里补一次准点刷新。
 */
class WidgetMidnightRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 先排下一次，避免本次刷新失败导致后续断档
        WidgetRefreshScheduler.scheduleMidnightRefresh(applicationContext)
        return runCatching {
            WidgetUpdater.refreshAll(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

object WidgetRefreshScheduler {

    private const val MIDNIGHT_WORK = "widget_midnight_refresh"

    /** 下一个 00:05（当天还没到就是今天，否则明天） */
    fun nextMidnightRefresh(now: ZonedDateTime): ZonedDateTime {
        val todayTarget = now.toLocalDate().atTime(0, 5).atZone(now.zone)
        return if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
    }

    fun scheduleMidnightRefresh(context: Context) {
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, nextMidnightRefresh(now))
            .toMillis()
            .coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<WidgetMidnightRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
