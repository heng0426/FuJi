package com.xiaofu.daily.update

import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 拉取并解析版本清单；只做网络与格式，不做版本比较 */
object UpdateChecker {

    private const val MAX_MANIFEST_BYTES = 256 * 1024

    suspend fun fetch(): UpdateManifest = withContext(Dispatchers.IO) {
        val urls = listOf(UpdateConfig.MANIFEST_URL) + UpdateConfig.MANIFEST_MIRRORS
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                return@withContext UpdateManifest.parse(readText(url))
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw UpdateException(
            (lastError as? UpdateException)?.message ?: "检查更新失败，请稍后重试",
            lastError,
        )
    }

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "XiaoFuDaily-Android/${Build.VERSION.SDK_INT}")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw UpdateException("检查更新失败（HTTP $code）")
            val stream = connection.inputStream
            val bytes = stream.use { input ->
                val buffer = ByteArray(MAX_MANIFEST_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                buffer.copyOf(total)
            }
            return bytes.toString(Charsets.UTF_8)
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException("网络不可用，请检查连接", e)
        } finally {
            connection.disconnect()
        }
    }
}
