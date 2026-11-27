package com.xiaofu.daily.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters

/**
 * 每日静默检查：只在有新版本时发通知，失败不打扰用户。
 *
 * 国产 ROM 的后台限制可能让任务延后；个人使用可接受，手动入口始终可用。
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!UpdateStore.notifyEnabled(context)) return Result.success()

        return try {
            val manifest = UpdateRepository(context).check()
            UpdateStore.markChecked(context)
            if (manifest != null &&
                manifest.versionCode != UpdateStore.skippedVersion(context)
            ) {
                UpdateStore.setKnown(context, manifest.versionCode, manifest.versionName)
                UpdateNotifier.notifyAvailable(context, manifest)
            }
            Result.success()
        } catch (t: Throwable) {
            // 网络抖动重试一次即可，避免离线状态下反复唤醒
            if (runAttemptCount < 1) Result.retry() else Result.success()
        }
    }
}
