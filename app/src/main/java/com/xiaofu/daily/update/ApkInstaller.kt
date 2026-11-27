package com.xiaofu.daily.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/** 通过 PackageInstaller 会话发起覆盖安装；系统安装器仍会向用户显示确认页 */
object ApkInstaller {

    /** 是否已获得"安装未知应用"授权（Android 8+ 按来源应用单独授权） */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 跳到本应用的"安装未知应用"开关；Android 13+ 侧载应用可能还需在应用信息里放开受限设置 */
    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // 保留系统安装器的确认步骤：静默安装需要 UPDATE_PACKAGES_WITHOUT_USER_ACTION，
            // 个人自用没有必要为此扩大权限面。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                    session.fsync(output)
                }
                val callback = Intent(context, InstallResultReceiver::class.java)
                    .putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                val pending = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw UpdateException("无法启动安装器：${t.message ?: "未知错误"}", t)
        }
    }
}
