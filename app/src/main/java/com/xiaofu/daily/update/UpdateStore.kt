package com.xiaofu.daily.update

import android.content.Context

/**
 * 升级相关的轻量状态。
 *
 * 用 SharedPreferences 而不是 Room：安装结果回调发生在 BroadcastReceiver 里，
 * 进程可能随时被系统回收，同步读写更稳；这些数据也不属于业务数据，不参与备份。
 */
object UpdateStore {

    private const val FILE = "update_prefs"

    private const val KEY_NOTIFY_ENABLED = "notify_enabled"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val KEY_LAST_CHECK_AT = "last_check_at"
    private const val KEY_KNOWN_CODE = "known_version_code"
    private const val KEY_KNOWN_NAME = "known_version_name"
    private const val KEY_PENDING_INSTALL = "pending_install_version"
    private const val KEY_INSTALL_ERROR = "install_error"
    private const val KEY_INSTALL_ERROR_ROM_BLOCKED = "install_error_rom_blocked"
    private const val KEY_LAST_BACKUP_KEY = "last_backup_key"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- 每日检查开关（默认开） ----

    fun notifyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFY_ENABLED, true)

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).apply()
    }

    // ---- 跳过某个版本 ----

    fun skippedVersion(context: Context): Long =
        prefs(context).getLong(KEY_SKIPPED_VERSION, -1L)

    fun skipVersion(context: Context, versionCode: Long) {
        prefs(context).edit().putLong(KEY_SKIPPED_VERSION, versionCode).apply()
    }

    fun clearSkipped(context: Context) {
        prefs(context).edit().remove(KEY_SKIPPED_VERSION).apply()
    }

    // ---- 最近一次检查 ----

    fun markChecked(context: Context, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_CHECK_AT, at).apply()
    }

    fun lastCheckAt(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK_AT, 0L)

    // ---- 已知的新版本（提醒关掉后，关于页仍能显示"有新版本"） ----

    fun setKnown(context: Context, versionCode: Long, versionName: String) {
        prefs(context).edit()
            .putLong(KEY_KNOWN_CODE, versionCode)
            .putString(KEY_KNOWN_NAME, versionName)
            .apply()
    }

    fun knownVersionCode(context: Context): Long = prefs(context).getLong(KEY_KNOWN_CODE, -1L)

    fun knownVersionName(context: Context): String? =
        prefs(context).getString(KEY_KNOWN_NAME, null)

    fun clearKnown(context: Context) {
        prefs(context).edit().remove(KEY_KNOWN_CODE).remove(KEY_KNOWN_NAME).apply()
    }

    // ---- 安装结果交接：发起安装 → 进程可能被杀 → 下次启动消费 ----

    fun setPendingInstall(context: Context, versionCode: Long) {
        prefs(context).edit()
            .putLong(KEY_PENDING_INSTALL, versionCode)
            .remove(KEY_INSTALL_ERROR)
            .apply()
    }

    fun clearPendingInstall(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_INSTALL).apply()
    }

    fun pendingInstall(context: Context): Long = prefs(context).getLong(KEY_PENDING_INSTALL, -1L)

    // ---- 升级前备份去重：同一天对同一版本只备份一次 ----

    fun lastBackupKey(context: Context): String? =
        prefs(context).getString(KEY_LAST_BACKUP_KEY, null)

    fun setLastBackupKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_LAST_BACKUP_KEY, key).apply()
    }

    /** 安装失败信息；[romBlocked] 表示拦截来自系统/厂商安装器，重试通常无效 */
    data class InstallError(val message: String, val romBlocked: Boolean)

    fun setInstallError(context: Context, message: String, romBlocked: Boolean = false) {
        prefs(context).edit()
            .putString(KEY_INSTALL_ERROR, message)
            .putBoolean(KEY_INSTALL_ERROR_ROM_BLOCKED, romBlocked)
            .apply()
    }

    /** 取出并清空安装失败信息，保证只提示一次 */
    fun consumeInstallError(context: Context): InstallError? {
        val prefs = prefs(context)
        val message = prefs.getString(KEY_INSTALL_ERROR, null) ?: return null
        val romBlocked = prefs.getBoolean(KEY_INSTALL_ERROR_ROM_BLOCKED, false)
        prefs.edit()
            .remove(KEY_INSTALL_ERROR)
            .remove(KEY_INSTALL_ERROR_ROM_BLOCKED)
            .apply()
        return InstallError(message, romBlocked)
    }
}
