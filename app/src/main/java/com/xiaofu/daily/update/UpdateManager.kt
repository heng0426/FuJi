package com.xiaofu.daily.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 安装回调到安装真正生效之间的最长等待时间 */
private const val INSTALL_RESULT_TIMEOUT_MS = 15_000L

/** 升级界面的全部状态；Idle 表示不展示任何弹窗 */
sealed interface UpdateState {

    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val checkedAt: Long) : UpdateState

    data class Available(val manifest: UpdateManifest) : UpdateState

    data class Working(
        val manifest: UpdateManifest,
        val progress: Int,
        val phase: String,
    ) : UpdateState

    /** 已下载校验通过，缺"安装未知应用"授权 */
    data class NeedsPermission(val manifest: UpdateManifest) : UpdateState

    data class Failed(
        val message: String,
        val manifest: UpdateManifest?,
        /** 拦截来自系统/厂商安装器：重试基本无效，应引导手动安装 */
        val romBlocked: Boolean = false,
    ) : UpdateState
}

/**
 * 升级流程的唯一入口：界面、通知、安装返回都收敛到这里。
 *
 * 用 object 而非 ViewModel：通知点击发生在 Activity 重建之后，
 * 状态必须独立于任何一个 Compose 生命周期存在。
 */
object UpdateManager {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: UpdateRepository? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 最近一次发现的新版本；提醒关闭时「关于」页仍能显示可更新 */
    private val _known = MutableStateFlow<UpdateManifest?>(null)
    val known: StateFlow<UpdateManifest?> = _known.asStateFlow()

    private var lastManifest: UpdateManifest? = null

    /** 已下载并通过校验的安装包；授权返回后直接复用，避免重复下载 */
    private var lastApk: java.io.File? = null

    /** 安装回调后的等待复检任务；用户关闭弹窗或再次发起时取消 */
    private var awaitingResult: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        repository = UpdateRepository(appContext)
    }

    /**
     * 通知点击进入：优先用已缓存的清单，避免再等一次网络。
     * 直接驱动状态，不依赖 Activity 的创建时机——singleTop 复用实例时也能弹出。
     */
    fun openFromNotification() {
        val known = _known.value
        if (known != null && known.versionCode > currentVersionCode()) {
            _state.value = UpdateState.Available(known)
        } else {
            check()
        }
    }

    // ---- 检查 ----

    fun check() {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Working) return
        val repo = repository ?: return

        scope.launch {
            _state.value = UpdateState.Checking
            try {
                val manifest = repo.check()
                UpdateStore.markChecked(appContext)
                if (manifest == null) {
                    UpdateStore.clearKnown(appContext)
                    _known.value = null
                    _state.value = UpdateState.UpToDate(System.currentTimeMillis())
                } else {
                    UpdateStore.setKnown(appContext, manifest.versionCode, manifest.versionName)
                    _known.value = manifest
                    _state.value = UpdateState.Available(manifest)
                }
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(
                    t.message ?: "检查更新失败，请稍后重试",
                    null,
                )
            }
        }
    }

    // ---- 下载与安装 ----

    fun install(manifest: UpdateManifest) {
        // 等待安装结果的复检可以被"重试"打断
        if (_state.value is UpdateState.Working && awaitingResult == null) return
        awaitingResult?.cancel()
        awaitingResult = null
        val repo = repository ?: return
        lastManifest = manifest

        scope.launch {
            try {
                _state.value = UpdateState.Working(manifest, 0, "正在准备")
                repo.backupBeforeInstall()

                // 复用手上已校验过的包：失败重试、授权返回都不必再下一次 20 MB
                val cached = lastApk?.takeIf {
                    lastManifest?.versionCode == manifest.versionCode &&
                        it.isFile && it.length() == manifest.sizeBytes
                }
                val apk = cached ?: repo.downloadAndVerify(
                    manifest = manifest,
                    onProgress = { percent ->
                        _state.value = UpdateState.Working(manifest, percent, "正在下载")
                    },
                    onVerify = {
                        _state.value = UpdateState.Working(manifest, 100, "正在校验")
                    },
                )
                lastApk = apk
                if (!ApkInstaller.canInstall(appContext)) {
                    _state.value = UpdateState.NeedsPermission(manifest)
                    return@launch
                }
                startInstall(manifest, apk)
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(
                    t.message ?: "更新失败，请重试",
                    manifest,
                )
            }
        }
    }

    /**
     * 从系统设置授权返回后继续安装。
     * 优先复用已校验的包：只在真正需要时才重新下载，弱网下不会白白重来一遍。
     */
    fun installAfterPermission() {
        val manifest = lastManifest ?: return
        if (!ApkInstaller.canInstall(appContext)) return

        val cached = lastApk?.takeIf { it.isFile && it.length() == manifest.sizeBytes }
        if (cached != null) {
            scope.launch { startInstall(manifest, cached) }
        } else {
            install(manifest)
        }
    }

    private suspend fun startInstall(manifest: UpdateManifest, apk: java.io.File) {
        try {
            UpdateStore.setPendingInstall(appContext, manifest.versionCode)
            _state.value = UpdateState.Working(manifest, 100, "正在打开安装器")
            withContext(Dispatchers.IO) {
                ApkInstaller.install(appContext, apk)
            }
        } catch (t: Throwable) {
            _state.value = UpdateState.Failed(t.message ?: "无法启动安装器", manifest)
        }
    }

    /**
     * 回到前台时补一次检查：部分 ROM 在授权页返回后不派发 ActivityResult，
     * 这里根据当前状态兜底，避免用户卡在"待授权"。
     */
    fun onAppResumed() {
        if (!::appContext.isInitialized) return
        val state = _state.value
        if (state is UpdateState.NeedsPermission && ApkInstaller.canInstall(appContext)) {
            installAfterPermission()
        }
    }

    /**
     * 安装会话回调（跨进程）：失败时把界面从"正在打开安装器"解开。
     *
     * 判成败不能只看回调，也不能只看"回调这一刻"的版本：实测 OPPO 上系统会先派发
     * ABORTED，安装随后才完成，此刻读到的仍是旧版本。因此先进入等待态轮询版本，
     * 超时仍没升级才算真失败。
     */
    fun onInstallFailed(message: String, romBlocked: Boolean = false) {
        if (!::appContext.isInitialized) return
        val repo = repository
        val pending = UpdateStore.pendingInstall(appContext)
        val manifest = when (val current = _state.value) {
            is UpdateState.Working -> current.manifest
            is UpdateState.NeedsPermission -> current.manifest
            else -> lastManifest
        }

        // 快路径：已经装上了
        if (pending > 0 && (repo?.currentVersionCode() ?: 0L) >= pending) {
            onInstallSucceeded()
            return
        }

        awaitingResult?.cancel()
        if (manifest != null) {
            _state.value = UpdateState.Working(manifest, 100, "正在等待安装结果")
        }
        awaitingResult = scope.launch {
            val deadline = System.currentTimeMillis() + INSTALL_RESULT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(800)
                val current = repo?.currentVersionCode() ?: 0L
                if (pending > 0 && current >= pending) {
                    onInstallSucceeded()
                    return@launch
                }
            }
            _state.value = UpdateState.Failed(message, manifest, romBlocked)
        }
    }

    /**
     * 手动安装：把已校验的包保存到「下载/小福日常」，并尝试用系统安装器打开。
     *
     * 用于 ROM 拦截应用内安装的机型（会话安装与 ACTION_VIEW 都可能被拒）。
     * 即使安装页没能弹出，安装包已在公共下载目录，用户可从文件管理器直接打开。
     */
    fun installManually() {
        if (!::appContext.isInitialized) return
        val manifest = lastManifest ?: _known.value
        // 内存里的包优先；进程重启过则回退到下载缓存，只要大小与清单一致即可用
        val apk = sequenceOf(
            lastApk,
            java.io.File(UpdateConfig.cacheDir(appContext), UpdateConfig.APK_FILE_NAME),
        ).filterNotNull()
            .firstOrNull { file ->
                file.isFile && (manifest == null || file.length() == manifest.sizeBytes)
            }

        if (apk == null || manifest == null) {
            check()
            return
        }

        scope.launch {
            _state.value = UpdateState.Working(manifest, 100, "正在保存安装包")
            val saved = ApkExporter.exportToDownloads(appContext, apk, manifest.versionName)
            val opened = runCatching {
                withContext(Dispatchers.IO) { ApkInstaller.openWithSystemInstaller(appContext, apk) }
            }.isSuccess

            val fileName = "小福日常-v${manifest.versionName}.apk"
            val hint = when {
                saved == null ->
                    "安装包未能保存到下载目录，请点「重试」重新下载后再试。"
                opened ->
                    "已保存到「下载/小福日常/$fileName」。若安装页没有出现，请从「文件管理 → 下载 → 小福日常」点开它完成安装。"
                else ->
                    "请从「文件管理 → 下载 → 小福日常/$fileName」点开安装包完成安装。"
            }
            _state.value = UpdateState.Failed(hint, manifest, romBlocked = true)
        }
    }

    /** 安装成功且进程未被杀时的收尾 */
    fun onInstallSucceeded() {
        if (!::appContext.isInitialized) return
        awaitingResult?.cancel()
        awaitingResult = null
        lastApk = null
        UpdateStore.clearPendingInstall(appContext)
        UpdateStore.clearKnown(appContext)
        UpdateStore.clearSkipped(appContext)
        UpdateNotifier.cancel(appContext)
        _state.value = UpdateState.Idle
    }

    fun skip() {
        val manifest = when (val current = _state.value) {
            is UpdateState.Available -> current.manifest
            is UpdateState.NeedsPermission -> current.manifest
            else -> _known.value
        } ?: return
        UpdateStore.skipVersion(appContext, manifest.versionCode)
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        awaitingResult?.cancel()
        awaitingResult = null
        _state.value = UpdateState.Idle
    }

    // ---- 与安装结果的交接 ----

    /**
     * 启动时消费一次安装结果。
     *
     * 判定顺序很重要：**先看版本是否已经达到目标版本**。应用自我更新时系统常先派发
     * ABORTED 再完成安装，若按"有错误就报错"处理，会把成功显示成"安装已取消"
     * （vivo 机型实测）。
     */
    fun consumeStartupMessage(): String? {
        val repo = repository ?: return null
        val pending = UpdateStore.pendingInstall(appContext)
        val error = UpdateStore.consumeInstallError(appContext)
        val current = repo.currentVersionCode()

        if (pending > 0 && current >= pending) {
            UpdateStore.clearPendingInstall(appContext)
            UpdateStore.clearKnown(appContext)
            UpdateStore.clearSkipped(appContext)
            repo.clearDownloadCache()
            return "已更新到 ${repo.currentVersionName()}"
        }
        if (error != null) {
            UpdateStore.clearPendingInstall(appContext)
            return if (error.romBlocked) {
                "${error.message}；可在「下载/小福日常」中找到安装包手动安装"
            } else {
                error.message
            }
        }
        if (pending > 0) UpdateStore.clearPendingInstall(appContext)
        return null
    }

    private fun currentVersionCode(): Long = repository?.currentVersionCode() ?: 0L
}
