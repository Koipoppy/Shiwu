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
import com.shiwu.scan3d.data.ReconService
import com.shiwu.scan3d.data.SettingsStore
import com.shiwu.scan3d.recon.LocalReconstructor
import com.shiwu.scan3d.recon.TripoSrLocal
import com.shiwu.scan3d.ui.components.SubjectSegmenter
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
import java.util.UUID

enum class DeviceMode { LIVE, CONFIG, ARCHIVE, IMPORT, PREVIEW, PROCESSING }

data class DeviceState(
    val mode: DeviceMode = DeviceMode.LIVE,
    val frames: List<String> = emptyList(),
    val sessionId: String = "",
    val name: String = "",
    val engine: Engine = Engine.TURBO,
    val quality: Quality = Quality.BALANCED,
    val jobs: List<JobUi> = emptyList(),
    val assets: List<AssetItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val probe: ConnectionProbe = ConnectionProbe(),
    val previewId: String? = null,
    val toast: String? = null
) {
    val previewAsset: AssetItem?
        get() = previewId?.let { id -> assets.firstOrNull { it.id == id } }

    /** 当前正在成像的任务；没有就是 null。 */
    val job: JobUi?
        get() = jobs.firstOrNull { it.isActive }

    val maxFrames: Int get() = 12
}

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val store = AssetStore(context)
    private val settingsStore = SettingsStore(context)
    private val api = AholoApi()

    /** 取景与离线重建共用的主体分割器。 */
    val segmenter = SubjectSegmenter(app)

    private val _state = MutableStateFlow(DeviceState(assets = store.list(), name = defaultName()))
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    private var sessionDir: File? = null
    private var generationJob: Job? = null
    private var probeJob: Job? = null

    init {
        ensureSession()
        viewModelScope.launch {
            val settings = settingsStore.read()
            val engine = Engine.entries.firstOrNull { it.apiValue == settings.engine } ?: Engine.TURBO
            _state.update { it.copy(settings = settings, engine = engine, settingsLoaded = true) }
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

    fun cycleEngine() {
        val s = _state.value
        val entries = Engine.entries
        val next = entries[(entries.indexOf(s.engine) + 1) % entries.size]
        _state.update { it.copy(engine = next) }
        viewModelScope.launch { settingsStore.save(s.settings.copy(engine = next.apiValue)) }
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

    /** 出模服务地址；传空串表示关闭，本地引擎回到机内重建。 */
    fun setLocalServiceUrl(url: String) {
        val trimmed = url.trim()
        updateSettings(
            _state.value.settings.copy(
                localServiceUrl = if (trimmed.isBlank()) "" else ReconService.normalize(trimmed)
            )
        )
    }

    fun markOnboarded() {
        val s = _state.value.settings
        if (s.onboarded) return
        updateSettings(s.copy(onboarded = true))
    }

    fun testConnection() {
        val settings = _state.value.settings
        // 本地引擎 + 出模服务时，链路测试应该打服务而不是云端网关
        if (_state.value.engine == Engine.LOCAL && settings.localServiceUrl.isNotBlank()) {
            probeJob?.cancel()
            _state.update { it.copy(probe = ConnectionProbe(checking = true)) }
            probeJob = viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ReconService(settings.localServiceUrl).health() }
                }
                _state.update {
                    it.copy(
                        probe = result.fold(
                            onSuccess = { ms -> ConnectionProbe(ok = true, message = "服务 OK ${ms}ms") },
                            onFailure = { e -> ConnectionProbe(ok = false, message = e.message ?: "FAIL") }
                        )
                    )
                }
            }
            return
        }
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

    /**
     * 提交底片：任务拷走这批图片并在后台跑，取景器立刻复位可以拍下一件。
     * 图片会保留到模型真正下载到本地，失败时可在资产页直接重试。
     */
    fun submit(): Boolean {
        val st = _state.value
        if (st.frames.isEmpty()) {
            toast("先拍几帧再提交")
            return false
        }
        if (st.jobs.any { it.isActive }) {
            toast("已有任务在成像，请等它完成")
            return false
        }
        val useDemo = st.settings.demoMode || st.settings.apiKey.isBlank()
        val seed = segmenter.currentSeed
        val job = JobUi(
            id = UUID.randomUUID().toString().take(8),
            name = st.name.ifBlank { defaultName() },
            frames = st.frames.toList(),
            coverPath = st.frames.firstOrNull(),
            engine = st.engine.apiValue,
            faceCount = st.quality.faceCount,
            demo = useDemo,
            seedX = seed.first,
            seedY = seed.second,
            phase = JobPhase.PREPARING,
            total = st.frames.size,
            message = if (useDemo) "演示模式：本地模拟成像" else "正在压缩底片"
        )

        _state.update {
            it.copy(
                jobs = it.jobs + job,
                frames = emptyList(),
                sessionId = "",
                name = defaultName()
            )
        }
        ensureSession()
        startJob(job, st.settings)
        return true
    }

    fun retryJob(id: String): Boolean {
        val st = _state.value
        if (st.jobs.any { it.isActive }) {
            toast("已有任务在成像")
            return false
        }
        val old = st.jobs.firstOrNull { it.id == id } ?: return false
        if (old.frames.isEmpty() || old.frames.none { File(it).exists() }) {
            toast("底片文件已丢失，请重新拍摄")
            discardJob(id)
            return false
        }
        val job = old.copy(
            id = UUID.randomUUID().toString().take(8),
            phase = JobPhase.PREPARING,
            uploaded = 0,
            progress = 0f,
            taskId = null,
            message = "重新提交",
            error = null,
            startedAt = System.currentTimeMillis(),
            finishedAt = null
        )
        _state.update { s ->
            s.copy(jobs = s.jobs.filterNot { it.id == id } + job)
        }
        startJob(job, st.settings)
        return true
    }

    fun discardJob(id: String) {
        val st = _state.value
        val job = st.jobs.firstOrNull { it.id == id } ?: return
        if (job.isActive) {
            toast("任务进行中，先中止再放弃")
            return
        }
        _state.update { it.copy(jobs = it.jobs.filterNot { j -> j.id == id }) }
        viewModelScope.launch(Dispatchers.IO) { cleanupFrames(job) }
    }

    /** 本地引擎：OpenCV SfM + 视觉外壳，全程离线。 */
    private suspend fun runLocal(job: JobUi, startedAt: Long): AssetItem {
        // 装了端上 TripoSR 模型就优先走它（单图出模，比多视图稳妥得多）
        if (TripoSrLocal.isReady(context)) return runLocalTripo(job, startedAt)
        val serviceUrl = _state.value.settings.localServiceUrl
        if (serviceUrl.isNotBlank()) return runLocalService(job, serviceUrl, startedAt)
        patchJob(job.id) { it.copy(phase = JobPhase.PREPARING, message = "本地引擎启动") }
        var maskSegmenter: SubjectSegmenter? = null
        if (com.shiwu.scan3d.BuildConfig.SUBJECT_ENABLED) {
            maskSegmenter = SubjectSegmenter(context)
        }
        try {
            val assetId = store.newId()
            val target = store.modelFile(assetId)
            LocalReconstructor.reconstruct(
                frames = job.frames,
                segmenter = maskSegmenter,
                seed = job.seedX to job.seedY,
                useSegmenter = maskSegmenter != null,
                target = target
            ) { p, msg ->
                patchJob(job.id) {
                    it.copy(
                        phase = if (p >= 0.88f) JobPhase.DOWNLOADING else JobPhase.GENERATING,
                        progress = (p * 0.9f).coerceAtMost(0.9f),
                        message = msg
                    )
                }
            }
            return AssetItem(
                id = assetId,
                name = job.name.ifBlank { "本地扫描" },
                createdAt = System.currentTimeMillis(),
                coverPath = job.frames.firstOrNull(),
                modelPath = target.absolutePath,
                taskId = 0L,
                version = "LOCAL",
                faceCount = job.faceCount,
                durationMs = System.currentTimeMillis() - startedAt,
                sizeBytes = target.length(),
                sourceCount = job.frames.size,
                demo = false
            )
        } finally {
            runCatching { maskSegmenter?.close() }
        }
    }

    /**
     * 端上 TripoSR：整张模型都跑在手机上，不联网、不需要 PC。
     *
     * 单图生成只需要一帧，取底片里的第一张（拍摄顺序上就是用户对着物体正面按的第一下）。
     */
    private suspend fun runLocalTripo(job: JobUi, startedAt: Long): AssetItem {
        patchJob(job.id) { it.copy(phase = JobPhase.PREPARING, message = "加载端上模型") }
        val frame = job.frames.firstOrNull() ?: throw ApiException("底片文件已丢失")
        if (!File(frame).exists()) throw ApiException("底片文件已丢失")
        val assetId = store.newId()
        val target = store.modelFile(assetId)
        var segmenter: SubjectSegmenter? = null
        if (com.shiwu.scan3d.BuildConfig.SUBJECT_ENABLED) segmenter = SubjectSegmenter(context)
        try {
            TripoSrLocal.reconstruct(
                context, frame, segmenter, job.seedX to job.seedY, target
            ) { p, msg ->
                patchJob(job.id) {
                    it.copy(
                        phase = if (p >= 0.94f) JobPhase.DOWNLOADING else JobPhase.GENERATING,
                        progress = (p * 0.95f).coerceAtMost(0.95f),
                        message = msg
                    )
                }
            }
        } finally {
            runCatching { segmenter?.close() }
        }
        return AssetItem(
            id = assetId,
            name = job.name.ifBlank { "端上生成" },
            createdAt = System.currentTimeMillis(),
            coverPath = job.frames.firstOrNull(),
            modelPath = target.absolutePath,
            taskId = 0L,
            version = "TRIPO",
            faceCount = job.faceCount,
            durationMs = System.currentTimeMillis() - startedAt,
            sizeBytes = target.length(),
            sourceCount = job.frames.size,
            demo = false
        )
    }

    /**
     * 本地出模服务：把首帧底片发给 PC 上的 TripoSR，收回 GLB。
     *
     * 单图生成只需要一帧，所以取底片里的第一张（拍摄顺序上就是用户对着物体正面按的第一下）。
     */
    private suspend fun runLocalService(job: JobUi, serviceUrl: String, startedAt: Long): AssetItem {
        patchJob(job.id) { it.copy(phase = JobPhase.PREPARING, message = "连接出模服务") }
        val source = job.frames.firstOrNull() ?: throw ApiException("底片文件已丢失")
        if (!File(source).exists()) throw ApiException("底片文件已丢失")
        val service = ReconService(serviceUrl)
        val cache = File(context.cacheDir, "service").apply { mkdirs() }
        val upload = File(cache, "frame_${System.currentTimeMillis()}.jpg")

        try {
            patchJob(job.id) { it.copy(phase = JobPhase.UPLOADING, progress = 0.1f, message = "压缩底片") }
            withContext(Dispatchers.IO) { ImagePrep.compress(File(source), upload, 1280, 90) }
            patchJob(job.id) { it.copy(progress = 0.25f, message = "上传底片") }
            val glb = service.reconstruct(upload.readBytes())

            val assetId = store.newId()
            val target = store.modelFile(assetId)
            patchJob(job.id) { it.copy(phase = JobPhase.DOWNLOADING, progress = 0.9f, message = "写入本地仓库") }
            withContext(Dispatchers.IO) {
                target.parentFile?.mkdirs()
                target.writeBytes(glb)
            }
            return AssetItem(
                id = assetId,
                name = job.name.ifBlank { "本地服务扫描" },
                createdAt = System.currentTimeMillis(),
                coverPath = job.frames.firstOrNull(),
                modelPath = target.absolutePath,
                taskId = 0L,
                version = "TRIPO",
                faceCount = job.faceCount,
                durationMs = System.currentTimeMillis() - startedAt,
                sizeBytes = target.length(),
                sourceCount = job.frames.size,
                demo = false
            )
        } finally {
            runCatching { upload.delete() }
        }
    }

    /** 本地引擎的演示路径：用内置多视角合成图，方便无相机环境自测。 */
    private suspend fun runLocalBundled(job: JobUi, startedAt: Long): AssetItem {
        patchJob(job.id) { it.copy(phase = JobPhase.PREPARING, message = "载入演示底片") }
        val dir = File(context.cacheDir, "testviews").apply { mkdirs() }
        val frames = (0 until 8).map { i ->
            val f = File(dir, "view_$i.jpg")
            if (!f.exists()) {
                context.assets.open("testviews/view_$i.jpg").use { input ->
                    f.outputStream().use { input.copyTo(it) }
                }
            }
            f.absolutePath
        }
        val masks = (0 until 8).map { i ->
            val f = File(dir, "mask_$i.png")
            if (!f.exists()) {
                context.assets.open("testviews/mask_$i.png").use { input ->
                    f.outputStream().use { input.copyTo(it) }
                }
            }
            f.absolutePath
        }
        val assetId = store.newId()
        val target = store.modelFile(assetId)
        LocalReconstructor.reconstruct(
            frames = frames,
            segmenter = null,
            seed = job.seedX to job.seedY,
            useSegmenter = false,
            maskFiles = masks,
            target = target
        ) { p, msg ->
            patchJob(job.id) {
                it.copy(
                    phase = if (p >= 0.88f) JobPhase.DOWNLOADING else JobPhase.GENERATING,
                    progress = (p * 0.9f).coerceAtMost(0.9f),
                    message = "$msg（演示）"
                )
            }
        }
        return AssetItem(
            id = assetId,
            name = job.name.ifBlank { "本地演示扫描" },
            createdAt = System.currentTimeMillis(),
            coverPath = frames.firstOrNull(),
            modelPath = target.absolutePath,
            taskId = 0L,
            version = "LOCAL",
            faceCount = job.faceCount,
            durationMs = System.currentTimeMillis() - startedAt,
            sizeBytes = target.length(),
            sourceCount = frames.size,
            demo = true
        )
    }

    fun cancelGeneration() {
        val active = _state.value.job ?: return
        generationJob?.cancel()
        generationJob = null
        patchJob(active.id) {
            it.copy(phase = JobPhase.CANCELED, error = "已中止", finishedAt = System.currentTimeMillis())
        }
    }

    private fun startJob(job: JobUi, settings: AppSettings) {
        generationJob = viewModelScope.launch {
            try {
                val startedAt = job.startedAt
                val asset = when {
                    job.demo && job.engine == Engine.LOCAL.apiValue ->
                        runLocalBundled(job, startedAt)
                    job.engine == Engine.LOCAL.apiValue -> runLocal(job, startedAt)
                    job.demo -> runDemo(job, startedAt)
                    else -> runReal(job, settings, startedAt)
                }

                // 先把封面复制到仓库，再删底片；这样失败也不会丢图。
                val cover = withContext(Dispatchers.IO) {
                    copyCover(asset.id, job.frames.firstOrNull())
                }
                val finalAsset = asset.copy(coverPath = cover)
                withContext(Dispatchers.IO) { store.save(finalAsset) }
                withContext(Dispatchers.IO) { cleanupFrames(job) }

                _state.update {
                    it.copy(
                        jobs = it.jobs.filterNot { j -> j.id == job.id },
                        assets = store.list(),
                        toast = "成像完成，已存入资产"
                    )
                }
            } catch (ce: CancellationException) {
                patchJob(job.id) {
                    it.copy(
                        phase = JobPhase.CANCELED,
                        error = "已中止",
                        finishedAt = System.currentTimeMillis()
                    )
                }
                throw ce
            } catch (t: Throwable) {
                android.util.Log.e("ShiwuJob", "local/real job failed", t)
                patchJob(job.id) {
                    it.copy(
                        phase = JobPhase.FAILED,
                        error = t.message ?: "成像失败",
                        finishedAt = System.currentTimeMillis()
                    )
                }
                toast("成像失败，底片已保留，可在资产页重试")
            }
        }
    }

    private fun patchJob(id: String, transform: (JobUi) -> JobUi) {
        _state.update { s ->
            s.copy(jobs = s.jobs.map { if (it.id == id) transform(it) else it })
        }
    }

    private suspend fun runReal(job: JobUi, settings: AppSettings, startedAt: Long): AssetItem =
        withContext(Dispatchers.IO) {
            val total = job.frames.size
            val cacheDir = File(context.cacheDir, "upload").apply { mkdirs() }

            patchJob(job.id) { it.copy(phase = JobPhase.PREPARING, message = "正在压缩底片") }
            val prepared = job.frames.mapIndexed { index, path ->
                val target = File(cacheDir, "frame_${System.currentTimeMillis()}_$index.jpg")
                ImagePrep.compress(File(path), target)
            }

            val urls = mutableListOf<String>()
            prepared.forEachIndexed { index, file ->
                patchJob(job.id) {
                    it.copy(
                        phase = JobPhase.UPLOADING,
                        uploaded = index,
                        total = total,
                        progress = index.toFloat() / total * 0.12f,
                        message = "上传底片 ${index + 1}/$total"
                    )
                }
                val token = api.fetchUploadToken(settings.baseUrl, settings.apiKey)
                urls += api.uploadFile(token, file, ImagePrep.md5(file))
                patchJob(job.id) {
                    it.copy(uploaded = index + 1, progress = (index + 1f) / total * 0.12f)
                }
            }
            prepared.forEach { runCatching { it.delete() } }

            patchJob(job.id) {
                it.copy(phase = JobPhase.SUBMITTING, progress = 0.14f, message = "提交成像任务")
            }
            val taskId = api.createImgTo3d(
                settings.baseUrl,
                settings.apiKey,
                urls,
                job.engine,
                job.faceCount
            )
            patchJob(job.id) {
                it.copy(
                    phase = JobPhase.GENERATING,
                    taskId = taskId,
                    progress = 0.18f,
                    message = "重建几何与 PBR 材质"
                )
            }

            val genStart = System.currentTimeMillis()
            var lastQueryAt = 0L
            var glbUrl: String? = null
            while (true) {
                val elapsed = (System.currentTimeMillis() - genStart) / 1000f
                val eased = (1f - kotlin.math.exp(-elapsed / 42f)) * 0.76f
                patchJob(job.id) {
                    it.copy(progress = (0.18f + eased).coerceAtMost(0.94f))
                }

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
            patchJob(job.id) {
                it.copy(phase = JobPhase.DOWNLOADING, progress = 0.95f, message = "写入本地仓库")
            }
            api.download(glbUrl!!, target) { p ->
                patchJob(job.id) { it.copy(progress = (0.95f + p * 0.05f).coerceAtMost(1f)) }
            }

            AssetItem(
                id = assetId,
                name = job.name.ifBlank { "未命名扫描" },
                createdAt = System.currentTimeMillis(),
                coverPath = job.coverPath,
                modelPath = target.absolutePath,
                taskId = taskId,
                version = job.engine,
                faceCount = job.faceCount,
                durationMs = System.currentTimeMillis() - startedAt,
                sizeBytes = target.length(),
                sourceCount = job.frames.size
            )
        }

    private suspend fun runDemo(job: JobUi, startedAt: Long): AssetItem {
        val total = job.frames.size.coerceAtLeast(1)
        job.frames.forEachIndexed { index, _ ->
            patchJob(job.id) {
                it.copy(
                    phase = JobPhase.UPLOADING,
                    uploaded = index + 1,
                    total = total,
                    progress = (index + 1f) / total * 0.15f,
                    message = "上传底片 ${index + 1}/$total（演示）"
                )
            }
            delay(400)
        }
        patchJob(job.id) {
            it.copy(phase = JobPhase.SUBMITTING, progress = 0.2f, message = "提交成像任务（演示）")
        }
        delay(800)
        patchJob(job.id) {
            it.copy(
                phase = JobPhase.GENERATING,
                progress = 0.24f,
                taskId = 1000000L + (System.currentTimeMillis() % 900000),
                message = "重建几何与 PBR 材质（演示）"
            )
        }
        val seconds = 14
        for (i in 1..seconds) {
            delay(700)
            patchJob(job.id) {
                it.copy(progress = (0.24f + i.toFloat() / seconds * 0.68f).coerceAtMost(0.92f))
            }
        }
        patchJob(job.id) {
            it.copy(phase = JobPhase.DOWNLOADING, progress = 0.95f, message = "写入本地仓库")
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
            name = job.name.ifBlank { "演示扫描" },
            createdAt = System.currentTimeMillis(),
            coverPath = job.coverPath,
            modelPath = target.absolutePath,
            taskId = 0L,
            version = job.engine,
            faceCount = job.faceCount,
            durationMs = System.currentTimeMillis() - startedAt,
            sizeBytes = target.length(),
            sourceCount = job.frames.size,
            demo = true
        )
    }

    /** 兼容旧调用：失败任务直接重试。 */
    fun retryGeneration() {
        val failed = _state.value.jobs.firstOrNull { it.phase == JobPhase.FAILED } ?: return
        retryJob(failed.id)
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

    private fun copyCover(assetId: String, source: String?): String? {
        if (source == null || !File(source).exists()) return null
        val target = File(store.modelsDir, "${assetId}_cover.jpg")
        return runCatching {
            File(source).copyTo(target, overwrite = true)
            target.absolutePath
        }.getOrNull()
    }

    /** 模型落地后清掉底片和空目录；失败/中止时不动，留给重试。 */
    private fun cleanupFrames(job: JobUi) {
        val dirs = mutableSetOf<File>()
        job.frames.forEach { path ->
            runCatching {
                val file = File(path)
                file.parentFile?.let { dirs += it }
                file.delete()
            }
        }
        dirs.forEach { dir ->
            runCatching {
                if (dir.listFiles()?.isEmpty() == true) dir.delete()
            }
        }
    }

    private fun defaultName(): String =
        "扫描 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        segmenter.close()
    }
}
