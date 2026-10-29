package com.shiwu.scan3d.ui.device

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

enum class DeviceMode { LIVE, CONFIG, ARCHIVE, IMPORT, PREVIEW, PROCESSING }

data class DeviceState(
    val mode: DeviceMode = DeviceMode.LIVE,
    val frames: List<String> = emptyList(),
    val sessionId: String = "",
    val name: String = "",
    val engine: Engine = Engine.TURBO,
    val quality: Quality = Quality.BALANCED,
    val job: JobUi? = null,
    val assets: List<AssetItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val probe: ConnectionProbe = ConnectionProbe(),
    val previewId: String? = null,
    val toast: String? = null
) {
    val previewAsset: AssetItem?
        get() = previewId?.let { id -> assets.firstOrNull { it.id == id } }

    val maxFrames: Int get() = 12
}

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val store = AssetStore(context)
    private val settingsStore = SettingsStore(context)
    private val api = AholoApi()

    private val _state = MutableStateFlow(DeviceState(assets = store.list(), name = defaultName()))
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    private var sessionDir: File? = null
    private var generationJob: Job? = null
    private var probeJob: Job? = null

    init {
        ensureSession()
        viewModelScope.launch {
            val settings = settingsStore.read()
            _state.update { it.copy(settings = settings) }
        }
    }

    // ---------------- 取景与底片 ----------------

    fun capture(path: String) {
        val st = _state.value
        if (st.frames.size >= st.maxFrames) {
            toast("底片已满 12 帧")
            return
        }
        _state.update { it.copy(frames = it.frames + path) }
    }

    fun clearFrames() {
        _state.update { it.copy(frames = emptyList(), toast = "已清空底片") }
    }

    fun importFrames(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val dir = ensureSession()
            val added = mutableListOf<String>()
            uris.take(_state.value.maxFrames - _state.value.frames.size).forEachIndexed { i, uri ->
                val target = File(dir, "import_${System.currentTimeMillis()}_$i.jpg")
                if (ImagePrep.copyUriToFile(uri, context.contentResolver, target)) {
                    added += target.absolutePath
                }
            }
            if (added.isEmpty()) {
                toast("没有读到图片")
                return@launch
            }
            _state.update {
                it.copy(
                    frames = (it.frames + added).take(it.maxFrames),
                    mode = DeviceMode.LIVE,
                    toast = "已载入 ${added.size} 帧"
                )
            }
        }
    }

    // ---------------- 面板 ----------------

    fun openConfig() = _state.update {
        it.copy(mode = if (it.mode == DeviceMode.CONFIG) DeviceMode.LIVE else DeviceMode.CONFIG)
    }

    fun openArchive() = _state.update {
        it.copy(mode = if (it.mode == DeviceMode.ARCHIVE) DeviceMode.LIVE else DeviceMode.ARCHIVE)
    }

    fun openImport() = _state.update { it.copy(mode = DeviceMode.IMPORT) }

    fun openPreview(id: String) = _state.update { it.copy(mode = DeviceMode.PREVIEW, previewId = id) }

    fun toLive() = _state.update { it.copy(mode = DeviceMode.LIVE, previewId = null) }

    // ---------------- 参数 ----------------

    fun cycleEngine() = _state.update {
        val next = if (it.engine == Engine.TURBO) Engine.STANDARD else Engine.TURBO
        it.copy(engine = next)
    }

    fun cycleQuality() = _state.update {
        val all = Quality.entries
        it.copy(quality = all[(all.indexOf(it.quality) + 1) % all.size])
    }

    fun updateSettings(settings: AppSettings) {
        _state.update { it.copy(settings = settings) }
        viewModelScope.launch { settingsStore.save(settings) }
    }

    fun toggleRegion() {
        val s = _state.value.settings
        updateSettings(s.copy(globalRegion = !s.globalRegion))
    }

    fun toggleDemo() {
        val s = _state.value.settings
        updateSettings(s.copy(demoMode = !s.demoMode))
    }

    fun testConnection() {
        val settings = _state.value.settings
        if (settings.apiKey.isBlank()) {
            _state.update { it.copy(probe = ConnectionProbe(ok = false, message = "未填 Key")) }
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
                        onSuccess = { ms -> ConnectionProbe(ok = true, message = "OK ${ms}ms") },
                        onFailure = { e -> ConnectionProbe(ok = false, message = e.message ?: "FAIL") }
                    )
                )
            }
        }
    }

    // ---------------- 生成 ----------------

    fun submit() {
        val snapshot = _state.value
        if (snapshot.frames.isEmpty()) {
            toast("先拍几帧再提交")
            return
        }
        if (generationJob?.isActive == true) return
        val useDemo = snapshot.settings.demoMode || snapshot.settings.apiKey.isBlank()

        _state.update {
            it.copy(
                mode = DeviceMode.PROCESSING,
                job = JobUi(
                    phase = JobPhase.PREPARING,
                    total = snapshot.frames.size,
                    message = if (useDemo) "演示模式：本地模拟成像" else "正在压缩底片"
                )
            )
        }

        generationJob = viewModelScope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                val asset = if (useDemo) runDemo(snapshot, startedAt)
                else runReal(snapshot, snapshot.settings, startedAt)

                withContext(Dispatchers.IO) { store.save(asset) }
                resetSession()
                _state.update {
                    it.copy(
                        assets = store.list(),
                        job = it.job?.copy(phase = JobPhase.DONE, progress = 1f, finishedAt = System.currentTimeMillis()),
                        mode = DeviceMode.PREVIEW,
                        previewId = asset.id,
                        frames = emptyList(),
                        sessionId = "",
                        name = defaultName()
                    )
                }
                ensureSession()
            } catch (ce: CancellationException) {
                _state.update {
                    it.copy(job = it.job?.copy(phase = JobPhase.CANCELED, error = "已中止"), mode = DeviceMode.LIVE)
                }
                throw ce
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        job = it.job?.copy(
                            phase = JobPhase.FAILED,
                            error = t.message ?: "成像失败",
                            finishedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private suspend fun runReal(snapshot: DeviceState, settings: AppSettings, startedAt: Long): AssetItem =
        withContext(Dispatchers.IO) {
            val total = snapshot.frames.size
            val cacheDir = File(context.cacheDir, "upload").apply { mkdirs() }

            _state.update { it.copy(job = it.job?.copy(phase = JobPhase.PREPARING, message = "正在压缩底片")) }
            val prepared = snapshot.frames.mapIndexed { index, path ->
                val target = File(cacheDir, "frame_${System.currentTimeMillis()}_$index.jpg")
                ImagePrep.compress(File(path), target)
            }

            val urls = mutableListOf<String>()
            prepared.forEachIndexed { index, file ->
                _state.update {
                    it.copy(
                        job = it.job?.copy(
                            phase = JobPhase.UPLOADING,
                            uploaded = index,
                            total = total,
                            progress = index.toFloat() / total * 0.12f,
                            message = "上传底片 ${index + 1}/$total"
                        )
                    )
                }
                val token = api.fetchUploadToken(settings.baseUrl, settings.apiKey)
                urls += api.uploadFile(token, file, ImagePrep.md5(file))
                _state.update {
                    it.copy(job = it.job?.copy(uploaded = index + 1, progress = (index + 1f) / total * 0.12f))
                }
            }
            prepared.forEach { runCatching { it.delete() } }

            _state.update {
                it.copy(job = it.job?.copy(phase = JobPhase.SUBMITTING, progress = 0.14f, message = "提交成像任务"))
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
                        message = "重建几何与 PBR 材质"
                    )
                )
            }

            val genStart = System.currentTimeMillis()
            var lastQueryAt = 0L
            var glbUrl: String? = null
            while (true) {
                val elapsed = (System.currentTimeMillis() - genStart) / 1000f
                val eased = (1f - kotlin.math.exp(-elapsed / 42f)) * 0.76f
                _state.update { it.copy(job = it.job?.copy(progress = (0.18f + eased).coerceAtMost(0.94f))) }

                if (System.currentTimeMillis() - lastQueryAt >= 4000) {
                    lastQueryAt = System.currentTimeMillis()
                    val snap = api.queryTask(settings.baseUrl, settings.apiKey, taskId)
                    if (snap.isTerminal) {
                        if (snap.isSuccess) {
                            glbUrl = api.pickGlbUrl(snap.outputs)
                            if (glbUrl == null) throw ApiException("任务完成但没有模型文件")
                            break
                        } else if (snap.status == 6) {
                            throw ApiException("任务已取消")
                        } else {
                            throw ApiException("成像失败，换几张更清晰的底片试试")
                        }
                    }
                }
                if (System.currentTimeMillis() - genStart > 8 * 60_000) {
                    throw ApiException("成像超时，请稍后重试")
                }
                delay(1200)
            }

            val assetId = store.newId()
            val target = store.modelFile(assetId)
            _state.update {
                it.copy(job = it.job?.copy(phase = JobPhase.DOWNLOADING, progress = 0.95f, message = "写入本地仓库"))
            }
            api.download(glbUrl!!, target) { p ->
                _state.update { it.copy(job = it.job?.copy(progress = (0.95f + p * 0.05f).coerceAtMost(1f))) }
            }

            AssetItem(
                id = assetId,
                name = snapshot.name.ifBlank { "未命名扫描" },
                createdAt = System.currentTimeMillis(),
                coverPath = snapshot.frames.firstOrNull(),
                modelPath = target.absolutePath,
                taskId = taskId,
                version = snapshot.engine.apiValue,
                faceCount = snapshot.quality.faceCount,
                durationMs = System.currentTimeMillis() - startedAt,
                sizeBytes = target.length(),
                sourceCount = snapshot.frames.size
            )
        }

    private suspend fun runDemo(snapshot: DeviceState, startedAt: Long): AssetItem {
        val total = snapshot.frames.size.coerceAtLeast(1)
        snapshot.frames.forEachIndexed { index, _ ->
            _state.update {
                it.copy(
                    job = it.job?.copy(
                        phase = JobPhase.UPLOADING,
                        uploaded = index + 1,
                        total = total,
                        progress = (index + 1f) / total * 0.15f,
                        message = "上传底片 ${index + 1}/$total（演示）"
                    )
                )
            }
            delay(400)
        }
        _state.update {
            it.copy(job = it.job?.copy(phase = JobPhase.SUBMITTING, progress = 0.2f, message = "提交成像任务（演示）"))
        }
        delay(800)
        _state.update {
            it.copy(
                job = it.job?.copy(
                    phase = JobPhase.GENERATING,
                    progress = 0.24f,
                    taskId = 1000000L + (System.currentTimeMillis() % 900000),
                    message = "重建几何与 PBR 材质（演示）"
                )
            )
        }
        val seconds = 14
        for (i in 1..seconds) {
            delay(700)
            _state.update {
                it.copy(job = it.job?.copy(progress = (0.24f + i.toFloat() / seconds * 0.68f).coerceAtMost(0.92f)))
            }
        }
        _state.update {
            it.copy(job = it.job?.copy(phase = JobPhase.DOWNLOADING, progress = 0.95f, message = "写入本地仓库"))
        }
        val assetId = store.newId()
        val target = store.modelFile(assetId)
        withContext(Dispatchers.IO) {
            context.assets.open("demo_model.glb").use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        }
        delay(400)
        return AssetItem(
            id = assetId,
            name = snapshot.name.ifBlank { "演示扫描" },
            createdAt = System.currentTimeMillis(),
            coverPath = snapshot.frames.firstOrNull(),
            modelPath = target.absolutePath,
            taskId = 0L,
            version = snapshot.engine.apiValue,
            faceCount = snapshot.quality.faceCount,
            durationMs = System.currentTimeMillis() - startedAt,
            sizeBytes = target.length(),
            sourceCount = snapshot.frames.size,
            demo = true
        )
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _state.update { it.copy(job = null, mode = DeviceMode.LIVE) }
    }

    fun retryGeneration() {
        _state.update { it.copy(job = null) }
        submit()
    }

    // ---------------- 资产 ----------------

    fun deleteAsset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(id)
            _state.update {
                it.copy(assets = store.list(), previewId = null, mode = DeviceMode.ARCHIVE, toast = "已删除")
            }
        }
    }

    // ---------------- 杂项 ----------------

    fun toast(message: String) = _state.update { it.copy(toast = message) }

    fun clearToast() = _state.update { it.copy(toast = null) }

    private fun ensureSession(): File {
        sessionDir?.let { if (it.exists()) return it }
        val id = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(store.scansDir, id).apply { mkdirs() }
        sessionDir = dir
        _state.update { it.copy(sessionId = id) }
        return dir
    }

    private fun resetSession() {
        sessionDir = null
    }

    private fun defaultName(): String =
        "扫描 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
