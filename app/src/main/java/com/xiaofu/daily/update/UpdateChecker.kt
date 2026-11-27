package com.xiaofu.daily.update

import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 拉取并解析版本清单；只做网络与格式，不做版本比较。
 *
 * 两条通道，按可用性自动回落：
 * 1. `github.com/<repo>/releases/latest/download/update.json` —— 网络通畅时最快，不消耗 API 配额；
 * 2. `api.github.com/repos/<repo>/releases/latest` —— 国内网络常屏蔽 github.com，
 *    但 api.github.com 与 objects.githubusercontent.com 一般可达，按资产名取清单与安装包。
 */
object UpdateChecker {

    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val API_LATEST =
        "https://api.github.com/repos/heng0426/FuJi/releases/latest"

    suspend fun fetch(): UpdateManifest = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null

        for (url in listOf(UpdateConfig.MANIFEST_URL) + UpdateConfig.MANIFEST_MIRRORS) {
            try {
                return@withContext UpdateManifest.parse(readText(url, "application/json"))
            } catch (t: Throwable) {
                lastError = t
            }
        }

        try {
            return@withContext fetchViaApi()
        } catch (t: Throwable) {
            lastError = t
        }

        throw UpdateException(
            (lastError as? UpdateException)?.message ?: "检查更新失败，请稍后重试",
            lastError,
        )
    }

    private fun fetchViaApi(): UpdateManifest {
        val release = runCatching { JSONObject(readText(API_LATEST, "application/vnd.github+json")) }
            .getOrElse { throw UpdateException("版本信息格式无效") }
        val assets = release.optJSONArray("assets") ?: throw UpdateException("版本信息缺少资源列表")

        var manifestAssetUrl: String? = null
        var apkAssetUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            when (asset.optString("name")) {
                "update.json" -> manifestAssetUrl = asset.optString("url")
                "xiaofu.apk" -> apkAssetUrl = asset.optString("url")
            }
        }

        val manifestUrl = manifestAssetUrl?.takeIf { it.startsWith("https://") }
            ?: throw UpdateException("发布资源缺少 update.json")
        val manifest = UpdateManifest.parse(readText(manifestUrl, "application/octet-stream"))

        // 清单来自 API 说明 github.com 不可达，安装包也走 API 资产地址，避免再等一次超时
        val apkUrl = apkAssetUrl?.takeIf { it.startsWith("https://") }
        return manifest.copy(apiAssetUrl = apkUrl)
    }

    private fun readText(url: String, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
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
