package com.xiaofu.daily.update

/**
 * 应用内升级的固定配置。
 *
 * 分发走 GitHub Releases（公开仓库）：`releases/latest/download/<asset>` 是恒定地址，
 * 不需要 api.github.com（有每小时 60 次的限流）。发布时资源名保持不变即可，
 * 版本信息全部由 update.json 描述。
 *
 * 备源留给后续扩展：[MANIFEST_MIRRORS] 里的地址会按顺序重试，[UpdateManifest.mirrors]
 * 则是清单内声明的 APK 备源，两者都为空的当前阶段不影响主链路。
 */
object UpdateConfig {

    private const val REPO = "heng0426/FuJi"
    private const val BASE = "https://github.com/$REPO/releases/latest/download"

    /** 版本清单（固定资源名） */
    const val MANIFEST_URL = "$BASE/update.json"

    /** 清单内缺省 apkUrl 时的兜底地址 */
    const val APK_URL = "$BASE/xiaofu.apk"

    /** 清单本身的备源，按顺序尝试；当前为空，后续可加国内镜像 */
    val MANIFEST_MIRRORS: List<String> = emptyList()

    /** 每日自动检查的时刻（24 小时制），与驱虫提醒的 09:00 错开 */
    const val CHECK_HOUR = 10

    const val CHANNEL_ID = "app_update"
    const val NOTIFICATION_ID = 1002
    const val FAILED_NOTIFICATION_ID = 1003

    const val APK_FILE_NAME = "xiaofu-update.apk"
    private const val CACHE_DIR = "update"

    fun cacheDir(context: android.content.Context): java.io.File =
        java.io.File(context.cacheDir, CACHE_DIR)
}
