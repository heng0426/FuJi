package com.xiaofu.daily.update

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/**
 * 安装前校验，四项全过才允许进入系统安装器：
 * 1. 文件大小与清单一致（下载完整）
 * 2. SHA-256 与清单一致（未被篡改）
 * 3. 包名为 com.xiaofu.daily（不是别的应用）
 * 4. 签名证书摘要与当前安装一致（同一个人签的，才能覆盖升级）
 *
 * 颜色/图标都不重要，签名是唯一可靠的来源证明：即使清单被改、镜像被换包，
 * 证书对不上也会在这里中止，不会把安装器拉起来。
 */
object ApkVerifier {

    fun verify(context: Context, file: File, manifest: UpdateManifest) {
        if (!file.isFile || file.length() <= 0) throw UpdateException("安装包不存在")
        if (file.length() != manifest.sizeBytes) throw UpdateException("安装包大小不符，请重新下载")

        val actual = sha256(file)
        if (!actual.equals(manifest.sha256, ignoreCase = true)) {
            throw UpdateException("安装包校验失败，已中止安装")
        }

        val pm = context.packageManager
        val archive = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: throw UpdateException("安装包无法解析")

        if (archive.packageName != context.packageName) {
            throw UpdateException("安装包包名不符，已中止安装")
        }
        if (archive.longVersionCode != manifest.versionCode) {
            throw UpdateException("安装包版本与清单不一致，已中止安装")
        }

        val target = signerDigest(archive)
        val self = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull().let { signerDigest(it) }

        if (target == null || self == null || target != self) {
            throw UpdateException("签名与已安装版本不一致，无法覆盖升级")
        }
    }

    private fun signerDigest(info: android.content.pm.PackageInfo?): String? {
        val signers = info?.signingInfo?.apkContentsSigners ?: return null
        val first = signers.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(first.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
