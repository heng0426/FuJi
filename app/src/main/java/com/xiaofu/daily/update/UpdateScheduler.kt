package com.xiaofu.daily.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 每日检查调度。
 *
 * 开关关闭时直接取消唯一任务，而不是让任务空转——关掉提醒的用户通常也不想被后台联网。
 * 开关重新打开后，下次启动或切换开关时重新入队。
 */
object UpdateScheduler {

    private const val UNIQUE_WORK = "update_check_daily"

    fun sync(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!UpdateStore.notifyEnabled(context)) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayToNextCheckMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** 下一次 10:00 的距离；已过 10:00 则顺延到明天 */
    private fun delayToNextCheckMillis(): Long {
        val now = ZonedDateTime.now()
        var next = now.withHour(UpdateConfig.CHECK_HOUR).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
