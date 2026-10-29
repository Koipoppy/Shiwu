package com.shiwu.scan3d.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiwu.scan3d.BuildConfig
import com.shiwu.scan3d.data.AholoApi
import com.shiwu.scan3d.data.ApiException
import com.shiwu.scan3d.data.AppSettings
import com.shiwu.scan3d.data.AssetItem
import com.shiwu.scan3d.data.AssetStore
import com.shiwu.scan3d.data.ConnectionProbe
import com.shiwu.scan3d.data.Engine
import com.shiwu.scan3d.data.ImagePrep
import com.shiwu.scan3d.data.JobPhase
import com.shiwu.scan3d.data.JobUi
import com.shiwu.scan3d.data.Quality
import com.shiwu.scan3d.data.ScanPhoto
import com.shiwu.scan3d.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data object Capture : Screen
    data object PickPhotos : Screen
    data object Review : Screen
    data object Progress : Screen
    data class Result(val assetId: String) : Screen
    data object Library : Screen
    data object Settings : Screen
}

data class AppState(
    val screen: Screen = Screen.Home,
    val photos: List<ScanPhoto> = emptyList(),
    val sessionId: String = "",
    val singleShot: Boolean = false,
    val name: String = "",
    val engine: Engine = Engine.TURBO,
    val quality: Quality = Quality.BALANCED,
    val job: JobUi? = null,
    val assets: List<AssetItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val settingsReady: Boolean = false,
    val probe: ConnectionProbe = ConnectionProbe(),
    val toast: String? = null,
    val lastShotPath: String? = null
) {
    val currentAsset: AssetItem?
        get() = (screen as? Screen.Result)?.let { result -> assets.firstOrNull { it.id == result.assetId } }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val store = AssetStore(context)
    private val settingsStore = SettingsStore(context)
    private val api = AholoApi()

    private val _state = MutableStateFlow(AppState(assets = store.list()))
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var sessionDir: File? = null
    private var generationJob: Job? = null
    private var probeJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = settingsStore.read()
            _state.update { it.copy(settings = settings, settingsReady = true) }
        }
    }

    // ---------------- 导航 ----------------

    fun go(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun back() {
        val current = _state.value.screen
        val target = when (current) {
            Screen.Capture -> Screen.Home
            Screen.PickPhotos -> Screen.Home
            Screen.Review -> if (_state.value.singleShot) Screen.Home else Screen.Capture
            Screen.Progress -> Screen.Review
            Screen.Library -> Screen.Home
            Screen.Settings -> Screen.Home
            is Screen.Result -> Screen.Home
            Screen.Home -> Screen.Home
        }
        _state.update { it.copy(screen = target) }
    }

    // ---------------- 扫描会话 ----------------

    fun startSession(singleShot: Boolean = false) {
        val id = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        sessionDir = File(store.scansDir, id).apply { mkdirs() }
        _state.update {
            it.copy(
                screen = Screen.Capture,
                sessionId = id,
                photos = emptyList(),
                singleShot = singleShot,
                name = defaultName(),
                engine = if (singleShot) Engine.TURBO else it.engine,
                job = null,
                lastShotPath = null
            )
        }
    }

    fun addPhoto(file: File) {
        val label = angleLabel(_state.value.photos.size)
        _state.update {
            it.copy(
                photos = it.photos + ScanPhoto(file.absolutePath, label),
                lastShotPath = file.absolutePath
            )
        }
    }

    fun removePhoto(path: String) = _state.update {
        it.copy(photos = it.photos.filterNot { p -> p.path == path })
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val dir = ensureSessionDir()
            val added = mutableListOf<ScanPhoto>()
            uris.forEachIndexed { index, uri ->
                val target = File(dir, "import_${System.currentTimeMillis()}_$index.jpg")
                if (ImagePrep.copyUriToFile(uri, context.contentResolver, target)) {
                    added += ScanPhoto(target.absolutePath, angleLabel(index))
                }
            }
            if (added.isEmpty()) {
                _state.update { it.copy(toast = "没有读到图片，换个方式再试") }
                return@launch
            }
            _state.update { st ->
                st.copy(
                    photos = st.photos + added,
                    screen = Screen.Review,
                    name = st.name.ifBlank { defaultName() }
                )
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun resumeCapture() = _state.update { it.copy(screen = Screen.Capture) }

    fun setEngine(engine: Engine) = _state.update { it.copy(engine = engine) }

    fun setQuality(quality: Quality) = _state.update { it.copy(quality = quality) }

    fun consumeLastShot() = _state.update { it.copy(lastShotPath = null) }

    // ---------------- 生成 ----------------

    fun startGeneration() {
        val snapshot = _state.value
        if (snapshot.photos.isEmpty() || generationJob?.isActive == true) return
        val settings = snapshot.settings
        val useDemo = settings.demoMode || settings.apiKey.isBlank()

        _state.update {
            it.copy(
                screen = Screen.Progress,
                job = JobUi(
                    phase = JobPhase.PREPARING,
                    total = snapshot.photos.size,
                    message = if (useDemo) "演示模式：本地模拟一次生成" else "正在压缩素材"
                )
            )
        }

        generationJob = viewModelScope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                val asset = if (useDemo) {
                    runDemo(snapshot, startedAt)
                } else {
                    runReal(snapshot, settings, startedAt)
                }
                withContext(Dispatchers.IO) { store.save(asset) }
                _state.update {
                    it.copy(
                        assets = store.list(),
                        job = it.job?.copy(
                            phase = JobPhase.DONE,
                            progress = 1f,
                            message = "生成完成",
                            finishedAt = System.currentTimeMillis()
                        ),
                        screen = Screen.Result(asset.id)
                    )
                }
            } catch (ce: CancellationException) {
                _state.update { it.copy(job = it.job?.copy(phase = JobPhase.CANCELED, error = "已取消")) }
                throw ce
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        job = it.job?.copy(
                            phase = JobPhase.FAILED,
                            error = t.message ?: "生成失败，请稍后重试",
                            finishedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private suspend fun runReal(
        snapshot: AppState,
        settings: AppSettings,
        startedAt: Long
    ): AssetItem = withContext(Dispatchers.IO) {
        val total = snapshot.photos.size
        val cacheDir = File(context.cacheDir, "upload").apply { mkdirs() }

        // 1) 压缩
        _state.update { it.copy(job = it.job?.copy(phase = JobPhase.PREPARING, message = "正在压缩素材")) }
        val prepared = snapshot.photos.mapIndexed { index, photo ->
            val target = File(cacheDir, "shot_${System.currentTimeMillis()}_$index.jpg")
            ImagePrep.compress(File(photo.path), target)
        }

        // 2) 上传（每张图单独取凭证）
        val urls = mutableListOf<String>()
        prepared.forEachIndexed { index, file ->
            _state.update {
                it.copy(
                    job = it.job?.copy(
                        phase = JobPhase.UPLOADING,
                        uploaded = index,
                        total = total,
                        progress = index.toFloat() / total * 0.12f,
                        message = "上传素材 ${index + 1}/$total"
                    )
                )
            }
            val token = api.fetchUploadToken(settings.baseUrl, settings.apiKey)
            val md5 = ImagePrep.md5(file)
            urls += api.uploadFile(token, file, md5)
            _state.update {
                it.copy(job = it.job?.copy(uploaded = index + 1, progress = (index + 1f) / total * 0.12f))
            }
        }
        prepared.forEach { runCatching { it.delete() } }

        // 3) 创建任务
        _state.update {
            it.copy(
                job = it.job?.copy(
                    phase = JobPhase.SUBMITTING,
                    progress = 0.14f,
                    message = "提交生成任务"
                )
            )
        }
        val taskId = api.createImgTo3d(
            settings.baseUrl,
            settings.apiKey,
            urls,
            snapshot.engine.apiValue,
            snapshot.quality.faceCount
        )
        _state.update {
            it.copy(
                job = it.job?.copy(
                    phase = JobPhase.GENERATING,
                    taskId = taskId,
                    progress = 0.18f,
                    message = "AI 正在重建几何与 PBR 材质"
                )
            )
        }

        // 4) 轮询（UI 每 1.2s 动一次，接口每 4s 查一次）
        val genStart = System.currentTimeMillis()
        var glbUrl: String? = null
        var lastQueryAt = 0L
        var status = 1
        while (true) {
            val elapsed = (System.currentTimeMillis() - genStart) / 1000f
            val eased = (1f - kotlin.math.exp(-elapsed / 42f)) * 0.76f
            _state.update {
                it.copy(job = it.job?.copy(progress = (0.18f + eased).coerceAtMost(0.94f)))
            }
            if (System.currentTimeMillis() - lastQueryAt >= 4000) {
                lastQueryAt = System.currentTimeMillis()
                val snap = api.queryTask(settings.baseUrl, settings.apiKey, taskId)
                status = snap.status
                if (snap.isTerminal) {
                    if (snap.isSuccess) {
                        glbUrl = api.pickGlbUrl(snap.outputs)
                        if (glbUrl == null) throw ApiException("任务完成但没有拿到模型文件")
                        break
                    } else if (snap.status == 6) {
                        throw ApiException("任务已取消")
                    } else {
                        throw ApiException("生成失败，可尝试「精细」模式或换几张更清晰的图")
                    }
                }
            }
            if (System.currentTimeMillis() - genStart > 8 * 60_000) {
                throw ApiException("生成超时，请稍后到资产库查看或重新生成")
            }
            delay(1200)
        }

        // 5) 下载
        val assetId = store.newId()
        val target = store.modelFile(assetId)
        _state.update {
            it.copy(
                job = it.job?.copy(
                    phase = JobPhase.DOWNLOADING,
                    progress = 0.95f,
                    message = "下载模型到本机"
                )
            )
        }
        api.download(glbUrl!!, target) { p ->
            _state.update {
                it.copy(job = it.job?.copy(progress = (0.95f + p * 0.05f).coerceAtMost(1f)))
            }
        }

        AssetItem(
            id = assetId,
            name = snapshot.name.ifBlank { "未命名扫描" },
            createdAt = System.currentTimeMillis(),
            coverPath = snapshot.photos.firstOrNull()?.path,
            modelPath = target.absolutePath,
            taskId = taskId,
            version = snapshot.engine.apiValue,
            faceCount = snapshot.quality.faceCount,
            durationMs = System.currentTimeMillis() - startedAt,
            sizeBytes = target.length(),
            sourceCount = snapshot.photos.size
        )
    }

    private suspend fun runDemo(snapshot: AppState, startedAt: Long): AssetItem {
        val total = snapshot.photos.size.coerceAtLeast(1)
        snapshot.photos.forEachIndexed { index, _ ->
            _state.update {
                it.copy(
                    job = it.job?.copy(
                        phase = JobPhase.UPLOADING,
                        uploaded = index + 1,
                        total = total,
                        progress = (index + 1f) / total * 0.15f,
                        message = "上传素材 ${index + 1}/$total（演示）"
                    )
                )
            }
            delay(450)
        }
        _state.update {
            it.copy(job = it.job?.copy(phase = JobPhase.SUBMITTING, progress = 0.2f, message = "提交生成任务（演示）"))
        }
        delay(900)
        _state.update {
            it.copy(
                job = it.job?.copy(
                    phase = JobPhase.GENERATING,
                    progress = 0.24f,
                    taskId = 1000000L + (System.currentTimeMillis() % 900000),
                    message = "AI 正在重建几何与 PBR 材质（演示）"
                )
            )
        }
        val demoSeconds = 16
        for (i in 1..demoSeconds) {
            delay(700)
            _state.update {
                it.copy(job = it.job?.copy(progress = (0.24f + i.toFloat() / demoSeconds * 0.68f).coerceAtMost(0.92f)))
            }
        }
        _state.update {
            it.copy(job = it.job?.copy(phase = JobPhase.DOWNLOADING, progress = 0.95f, message = "下载模型到本机"))
        }
        val assetId = store.newId()
        val target = store.modelFile(assetId)
        withContext(Dispatchers.IO) {
            context.assets.open("demo_model.glb").use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        }
        delay(500)
        return AssetItem(
            id = assetId,
            name = snapshot.name.ifBlank { "演示扫描" },
            createdAt = System.currentTimeMillis(),
            coverPath = snapshot.photos.firstOrNull()?.path,
            modelPath = target.absolutePath,
            taskId = 0L,
            version = snapshot.engine.apiValue,
            faceCount = snapshot.quality.faceCount,
            durationMs = System.currentTimeMillis() - startedAt,
            sizeBytes = target.length(),
            sourceCount = snapshot.photos.size,
            demo = true
        )
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _state.update {
            it.copy(
                job = it.job?.copy(phase = JobPhase.CANCELED, error = "已取消"),
                screen = Screen.Review
            )
        }
    }

    fun resetJob() {
        generationJob?.cancel()
        generationJob = null
        _state.update { it.copy(job = null) }
    }

    fun retryGeneration() {
        resetJob()
        startGeneration()
    }

    // ---------------- 资产 ----------------

    fun openAsset(id: String) = _state.update { it.copy(screen = Screen.Result(id)) }

    fun deleteAsset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(id)
            _state.update { it.copy(assets = store.list(), toast = "已删除") }
        }
    }

    fun clearAllAssets() {
        viewModelScope.launch(Dispatchers.IO) {
            store.clearAll()
            _state.update { it.copy(assets = store.list(), toast = "本地资产已清空") }
        }
    }

    // ---------------- 设置 ----------------

    fun updateSettings(settings: AppSettings) {
        _state.update { it.copy(settings = settings, settingsReady = true) }
        viewModelScope.launch { settingsStore.save(settings) }
    }

    fun testConnection() {
        val settings = _state.value.settings
        if (settings.apiKey.isBlank()) {
            _state.update { it.copy(probe = ConnectionProbe(ok = false, message = "还没有填写 API Key")) }
            return
        }
        probeJob?.cancel()
        _state.update { it.copy(probe = ConnectionProbe(checking = true)) }
        probeJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.probe(settings.baseUrl, settings.apiKey) }
            }
            _state.update {
                it.copy(
                    probe = result.fold(
                        onSuccess = { ms -> ConnectionProbe(ok = true, message = "连接正常 · ${ms}ms") },
                        onFailure = { e -> ConnectionProbe(ok = false, message = e.message ?: "连接失败") }
                    )
                )
            }
        }
    }

    // ---------------- 工具 ----------------

    fun showToast(message: String) = _state.update { it.copy(toast = message) }

    fun clearToast() = _state.update { it.copy(toast = null) }

    private fun ensureSessionDir(): File {
        sessionDir?.let { if (it.exists()) return it }
        val id = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(store.scansDir, id).apply { mkdirs() }
        sessionDir = dir
        _state.update { it.copy(sessionId = id) }
        return dir
    }

    private fun defaultName(): String {
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return "扫描 ${fmt.format(Date())}"
    }

    private fun angleLabel(index: Int): String {
        val labels = listOf("正面", "右侧", "右后", "背面", "左后", "左侧")
        return labels.getOrElse(index) { "补充 ${index + 1}" }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
