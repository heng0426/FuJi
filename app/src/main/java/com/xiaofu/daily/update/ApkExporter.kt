package com.xiaofu.daily.update

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把已校验的安装包导出到系统「下载/小福日常」。
 *
 * 用途：个别 ROM（如部分 MIUI/HyperOS）拦截应用内发起的安装，无论会话安装还是
 * ACTION_VIEW 都可能被拒。此时至少让用户能从文件管理器直接点开 APK 完成升级，
 * 避免卡在"更新未完成"无路可走。
 */
object ApkExporter {

    /**
     * @return 成功返回可分享的 Uri；Android 9 及以下或写入失败返回 null
     */
    suspend fun exportToDownloads(
        context: Context,
        apk: File,
        versionName: String?,
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null

        val name = if (versionName.isNullOrBlank()) "小福日常.apk" else "小福日常-v$versionName.apk"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/小福日常")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext null

        try {
            val written = runCatching {
                apk.inputStream().use { input ->
                    resolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output, 64 * 1024)
                    } ?: error("无法写入下载目录")
                }
            }
            if (written.isFailure) throw written.exceptionOrNull()!!
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
