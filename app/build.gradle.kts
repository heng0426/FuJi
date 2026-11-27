@file:Suppress("DEPRECATION")

import java.security.MessageDigest
import java.time.LocalDate
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 版本号与发布信息统一来自根目录 version.properties，避免 versionCode 与 tag 漂移
val versionFile = rootProject.file("version.properties")
val appVersion = Properties().apply {
    if (versionFile.exists()) versionFile.reader(Charsets.UTF_8).use { load(it) }
}
val appVersionCode = appVersion.getProperty("versionCode")?.toIntOrNull() ?: 1
val appVersionName = appVersion.getProperty("versionName") ?: "0.0.0"
val appMinSdk = appVersion.getProperty("minSdk")?.toIntOrNull() ?: 28
val appRepo = appVersion.getProperty("repo") ?: "heng0426/FuJi"
val appChangelog = appVersion.getProperty("changelog", "")
    .split("|")
    .map { it.trim() }
    .filter { it.isNotEmpty() }

// 签名配置不入库：keystore.properties 与 .jks 均在本机/离线备份中
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.reader(Charsets.UTF_8).use { load(it) }
}
val hasReleaseKey = keystoreFile.exists() && !keystoreProps.getProperty("storeFile").isNullOrBlank()

android {
    namespace = "com.xiaofu.daily"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xiaofu.daily"
        minSdk = appMinSdk
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 正式发布签名：keystore 与口令走 keystore.properties（已 gitignore）。
    // 之前复用 debug.keystore，换机/清 SDK 目录即换证书，无法覆盖升级，故迁移。
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "[FuJi] 未找到 keystore.properties，release 包将不签名；" +
                        "请从离线备份恢复 xiaofu-release.jks 后再出包。",
                )
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // 迁移测试需要使用导出的 schema 快照
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        // Gradle 9.x 已移除 kotlinOptions DSL，用 compilerOptions 替代
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room 导出 schema，供迁移测试与版本追踪
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * 一键出包：assembleRelease + 复制到 dist/ + 生成 GitHub Release 需要的 update.json。
 *
 * 产物：
 * - dist/xiaofu.apk             发布用固定资源名（URL 恒定）
 * - dist/小福日常-v<版本>.apk    便于人工识别与存档
 * - dist/update.json            版本清单，随 Release 一起上传
 *
 * 用独立 Task 类而不是 doLast 闭包：闭包会捕获脚本对象，导致配置缓存无法序列化。
 */
abstract class ReleaseArtifactsTask : DefaultTask() {

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val repo: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val changelog: ListProperty<String>

    @get:Input
    abstract val signingReady: Property<Boolean>

    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:OutputFile
    abstract val fixedApk: RegularFileProperty

    @get:OutputFile
    abstract val versionedApk: RegularFileProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generate() {
        if (!signingReady.get()) {
            throw GradleException(
                "缺少 keystore.properties 或 storeFile，无法生成可覆盖升级的 release 包。" +
                    "请先恢复 xiaofu-release.jks 与 keystore.properties。",
            )
        }
        val apkFile = apk.get().asFile
        if (!apkFile.isFile) throw GradleException("未找到 release 产物：${apkFile.absolutePath}")

        fixedApk.get().asFile.apply { parentFile.mkdirs() }
        apkFile.copyTo(fixedApk.get().asFile, overwrite = true)
        apkFile.copyTo(versionedApk.get().asFile, overwrite = true)

        val sha256 = MessageDigest.getInstance("SHA-256").run {
            apkFile.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    update(buffer, 0, read)
                }
            }
            digest().joinToString("") { "%02x".format(it) }
        }

        val name = versionName.get()
        val apkUrl = "https://github.com/${repo.get()}/releases/latest/download/xiaofu.apk"
        val changelogJson = changelog.get().joinToString(", ") { "\"${it.jsonEscape()}\"" }
        val json = buildString {
            append("{\n")
            append("  \"versionCode\": ${versionCode.get()},\n")
            append("  \"versionName\": \"${name.jsonEscape()}\",\n")
            append("  \"apkUrl\": \"${apkUrl.jsonEscape()}\",\n")
            append("  \"sha256\": \"$sha256\",\n")
            append("  \"sizeBytes\": ${apkFile.length()},\n")
            append("  \"minSdk\": ${minSdk.get()},\n")
            append("  \"changelog\": [$changelogJson],\n")
            append("  \"mirrors\": [],\n")
            append("  \"publishedAt\": \"${LocalDate.now()}\"\n")
            append("}\n")
        }
        manifestFile.get().asFile.writeText(json, Charsets.UTF_8)

        logger.lifecycle(
            """
            |
            |[FuJi] 发布产物已生成：
            |  dist/xiaofu.apk            ${apkFile.length() / 1024 / 1024} MB
            |  dist/小福日常-v$name.apk
            |  dist/update.json           versionCode ${versionCode.get()}
            |  sha256 $sha256
            |
            |下一步：
            |  git tag v$name && git push origin v$name
            |  gh release create v$name dist/xiaofu.apk dist/update.json --title "小福日常 $name" --notes-file docs/发布说明-$name.md
            |
            """.trimMargin(),
        )
    }

    private fun String.jsonEscape(): String = buildString {
        for (ch in this@jsonEscape) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }
}

val distDir = rootProject.layout.projectDirectory.dir("dist")

tasks.register<ReleaseArtifactsTask>("releaseArtifacts") {
    group = "release"
    description = "构建 release 包，输出 dist/xiaofu.apk 与 dist/update.json"

    dependsOn("assembleRelease")

    versionCode.set(appVersionCode)
    versionName.set(appVersionName)
    repo.set(appRepo)
    minSdk.set(appMinSdk)
    changelog.set(appChangelog)
    signingReady.set(hasReleaseKey)
    apk.set(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    fixedApk.set(distDir.file("xiaofu.apk"))
    versionedApk.set(distDir.file("小福日常-v$appVersionName.apk"))
    manifestFile.set(distDir.file("update.json"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Tab 图标所需；显式声明避免经传递依赖在 runtime classpath 丢失
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    // Room 本地数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 到期提醒（每日检查一次，09:00 触发）
    implementation(libs.androidx.work.runtime.ktx)

    // 真机 DAO 集成测试（无模拟器环境，instrumentation 测试跑在真机上）
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
