package com.xiaofu.daily.update

import org.json.JSONObject

/** 升级链路中的可预期失败（网络、校验、安装），文案直接面向用户 */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 版本清单。字段只增不减：旧版本 App 忽略不认识的字段，新字段必须给默认值。
 *
 * ```json
 * {
 *   "versionCode": 14,
 *   "versionName": "1.4.0",
 *   "apkUrl": "https://github.com/heng0426/FuJi/releases/latest/download/xiaofu.apk",
 *   "sha256": "…64 位十六进制…",
 *   "sizeBytes": 20710112,
 *   "minSdk": 28,
 *   "changelog": ["新增…", "修复…"],
 *   "mirrors": [],
 *   "publishedAt": "2026-12-01"
 * }
 * ```
 */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val minSdk: Int,
    val changelog: List<String>,
    val mirrors: List<String>,
    val publishedAt: String?,
    /** 经 api.github.com 解析出的资产地址；为空表示走直连通道 */
    val apiAssetUrl: String? = null,
) {

    /** 下载候选地址：API 通道优先（说明直连已被阻断），其次主源与备源 */
    fun downloadUrls(): List<String> = buildList {
        apiAssetUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        add(apkUrl)
        mirrors.forEach { if (it.isNotBlank() && it != apkUrl) add(it) }
    }

    companion object {

        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

        fun parse(text: String): UpdateManifest {
            val json = runCatching { JSONObject(text) }
                .getOrElse { throw UpdateException("版本清单格式无效") }

            val versionCode = json.optLong("versionCode", 0L)
            val versionName = json.optString("versionName").trim()
            val apkUrl = json.optString("apkUrl").trim().ifBlank { UpdateConfig.APK_URL }
            val sha256 = json.optString("sha256").trim().lowercase()
            val sizeBytes = json.optLong("sizeBytes", 0L)
            val minSdk = json.optInt("minSdk", 0)

            if (versionCode <= 0L) throw UpdateException("版本清单缺少有效的 versionCode")
            if (versionName.isEmpty()) throw UpdateException("版本清单缺少 versionName")
            if (!apkUrl.startsWith("https://")) throw UpdateException("下载地址必须为 HTTPS")
            if (!SHA256_PATTERN.matches(sha256)) throw UpdateException("版本清单校验值格式无效")
            if (sizeBytes <= 0L) throw UpdateException("版本清单缺少安装包大小")

            return UpdateManifest(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                sha256 = sha256,
                sizeBytes = sizeBytes,
                minSdk = minSdk,
                changelog = json.optJSONArray("changelog").toStringList(),
                mirrors = json.optJSONArray("mirrors").toStringList()
                    .filter { it.startsWith("https://") },
                publishedAt = json.optString("publishedAt").trim().ifBlank { null },
            )
        }

        private fun org.json.JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                optString(index).trim().ifBlank { null }
            }
        }
    }
}
