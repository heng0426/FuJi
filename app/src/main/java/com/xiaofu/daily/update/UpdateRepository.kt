package com.xiaofu.daily.update

import android.content.Context
import android.os.Build
import com.xiaofu.daily.AppContainer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 升级业务编排：检查 → 下载 → 校验。不持有 UI 状态，Worker 与界面共用。
 */
class UpdateRepository(private val context: Context) {

    private val downloader = ApkDownloader(context)

    /**
     * 检查有没有比当前安装更新的版本。
     * @return 有更新时返回清单；已是最新或系统版本过低时返回 null
     */
    suspend fun check(): UpdateManifest? {
        val manifest = UpdateChecker.fetch()
        if (manifest.minSdk > Build.VERSION.SDK_INT) return null
        return if (manifest.versionCode > currentVersionCode()) manifest else null
    }

    /** 下载并按清单校验；失败抛 [UpdateException] */
    suspend fun downloadAndVerify(
        manifest: UpdateManifest,
        onProgress: (Int) -> Unit,
        onVerify: () -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val file = downloader.download(manifest.downloadUrls(), manifest.sizeBytes, onProgress)
        onVerify()
        ApkVerifier.verify(context, file, manifest)
        file
    }

    /** 升级前自动备份到系统下载目录（MediaStore），卸载后仍可找回 */
    suspend fun backupBeforeInstall() {
        runCatching { AppContainer.backup.exportToDownloads() }
    }

    fun currentVersionCode(): Long = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    fun currentVersionName(): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    fun clearDownloadCache() {
        UpdateConfig.cacheDir(context).deleteRecursively()
    }
}
