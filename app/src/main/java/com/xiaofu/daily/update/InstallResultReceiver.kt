package com.xiaofu.daily.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/**
 * 安装会话结果回调。
 *
 * 注意：安装成功后本进程会被系统替换，这里的写入必须同步完成；
 * 失败信息落盘，下次启动由 UpdateManager 消费并提示一次。
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                ) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                UpdateStore.clearPendingInstall(context)
                UpdateStore.clearKnown(context)
                UpdateStore.clearSkipped(context)
                UpdateNotifier.cancel(context)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val text = describe(status, message)
                UpdateStore.setInstallError(context, text)
                UpdateNotifier.notifyInstallFailed(context, text)
            }
        }
    }

    private fun describe(status: Int, raw: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "安装被系统拦截，请检查系统安全设置"
        PackageInstaller.STATUS_FAILURE_ABORTED -> "安装已取消"
        PackageInstaller.STATUS_FAILURE_INVALID -> "安装包无效，请重新下载"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "与已安装版本冲突（签名或版本不一致）"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "存储空间不足，无法安装"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "安装包与当前系统不兼容"
        else -> raw?.takeIf { it.isNotBlank() }?.let { "安装失败：$it" } ?: "安装失败，请重试"
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}
