package com.xiaofu.daily.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.IntentCompat

/**
 * 安装会话结果回调。
 *
 * 注意：安装成功后本进程会被系统替换，这里的写入必须同步完成。
 *
 * 重要：**回调状态不能单独作为成败判据**。应用自我更新时，系统在杀进程前后
 * 常派发 ABORTED（会话提交方已消失），但安装实际已完成；实测部分机型
 * （如 vivo）就会出现"装好了却提示安装已取消"。因此这里以
 * "当前已安装版本是否达到目标版本"自证，回调只用来区分是否需要提示失败。
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

            PackageInstaller.STATUS_SUCCESS -> finishSuccess(context)

            else -> {
                val pending = UpdateStore.pendingInstall(context)
                if (pending > 0 && installedVersionCode(context) >= pending) {
                    // 已经装上去了，属于误报
                    finishSuccess(context)
                    return
                }

                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val text = describe(status, message)
                // 中止/拦截通常来自 ROM 安装器，重试往往无效，需要给"手动安装"的出路
                val romBlocked =
                    status == PackageInstaller.STATUS_FAILURE_ABORTED ||
                        status == PackageInstaller.STATUS_FAILURE_BLOCKED
                UpdateStore.setInstallError(context, text, romBlocked)
                UpdateNotifier.notifyInstallFailed(context, text)
                // 进程仍在时把界面从"正在打开安装器"解开，避免弹窗卡死
                UpdateManager.onInstallFailed(text, romBlocked)
            }
        }
    }

    private fun finishSuccess(context: Context) {
        UpdateStore.clearPendingInstall(context)
        UpdateStore.clearKnown(context)
        UpdateStore.clearSkipped(context)
        UpdateNotifier.cancel(context)
        UpdateManager.onInstallSucceeded()
    }

    private fun installedVersionCode(context: Context): Long = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.longVersionCode
    }.getOrDefault(0L)

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
