package com.xiaofu.daily.update

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** 下载安装包到缓存目录；主源失败按顺序重试备源 */
class ApkDownloader(private val context: Context) {

    suspend fun download(
        urls: List<String>,
        expectedSize: Long,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = UpdateConfig.cacheDir(context).apply { mkdirs() }
        // 覆盖安装后旧包无用；先清理，避免缓存里堆积多个版本
        dir.listFiles()?.forEach { it.delete() }
        if (dir.usableSpace < expectedSize * 2) {
            throw UpdateException("存储空间不足，需要约 ${expectedSize * 2 / 1024 / 1024} MB")
        }

        val target = File(dir, UpdateConfig.APK_FILE_NAME)
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                downloadOne(url, target, expectedSize, onProgress)
                return@withContext target
            } catch (t: Throwable) {
                lastError = t
                target.delete()
                coroutineContext.ensureActive()
            }
        }
        throw UpdateException(
            lastError?.message ?: "下载失败，请稍后重试",
            lastError,
        )
    }

    private fun downloadOne(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Int) -> Unit,
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "XiaoFuDaily-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw UpdateException("下载失败（HTTP $code）")

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            val temp = File(target.parentFile, "${target.name}.part")
            var lastPercent = -1
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }
            if (temp.length() != expectedSize) {
                temp.delete()
                throw UpdateException("下载不完整，请重试")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException("下载中断，请检查网络", e)
        } finally {
            connection.disconnect()
        }
    }
}
