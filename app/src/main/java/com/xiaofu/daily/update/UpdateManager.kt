package com.xiaofu.daily.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        if (_state.value is UpdateState.Working) return
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

    /** 安装会话回调（跨进程）：失败时把界面从"正在打开安装器"解开 */
    fun onInstallFailed(message: String) {
        if (!::appContext.isInitialized) return
        val manifest = when (val current = _state.value) {
            is UpdateState.Working -> current.manifest
            is UpdateState.NeedsPermission -> current.manifest
            else -> lastManifest
        }
        _state.value = UpdateState.Failed(message, manifest)
    }

    /** 安装成功且进程未被杀时的收尾 */
    fun onInstallSucceeded() {
        if (!::appContext.isInitialized) return
        lastApk = null
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
        _state.value = UpdateState.Idle
    }

    // ---- 与安装结果的交接 ----

    /**
     * 启动时消费一次安装结果：
     * - 失败信息（Receiver 落盘）优先提示
     * - 成功则提示"已更新到 x.y.z"
     */
    fun consumeStartupMessage(): String? {
        val error = UpdateStore.consumeInstallError(appContext)
        if (error != null) {
            UpdateStore.clearPendingInstall(appContext)
            return error
        }
        val pending = UpdateStore.pendingInstall(appContext)
        if (pending <= 0) return null

        val repo = repository ?: return null
        UpdateStore.clearPendingInstall(appContext)
        return if (pending <= repo.currentVersionCode()) {
            repo.clearDownloadCache()
            "已更新到 ${repo.currentVersionName()}"
        } else {
            null
        }
    }

    private fun currentVersionCode(): Long = repository?.currentVersionCode() ?: 0L
}
