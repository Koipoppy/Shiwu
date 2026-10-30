package com.shiwu.scan3d.data

import org.json.JSONArray
import org.json.JSONObject

enum class Engine(val apiValue: String, val label: String, val hint: String) {
    TURBO("G1-Turbo", "极速", "约 1 分钟 · 日常扫描"),
    STANDARD("G1", "精细", "约 5 分钟 · 高质量交付"),
    LOCAL("LOCAL", "本地", "离线重建 · 免联网免配额")
}

enum class Quality(val faceCount: Int, val label: String, val hint: String) {
    LIGHT(120_000, "轻量", "12 万面 · 网页 / 游戏"),
    BALANCED(200_000, "标准", "20 万面 · 通用"),
    FINE(300_000, "精细", "30 万面 · 展示 / 打印")
}

data class ScanPhoto(
    val path: String,
    val label: String = ""
)

data class AppSettings(
    val apiKey: String = "",
    val globalRegion: Boolean = false,
    val demoMode: Boolean = false,
    val autoRotate: Boolean = true,
    val onboarded: Boolean = false,
    val engine: String = "G1-Turbo",
    /** 非空时，本地引擎改为把底片发给这台机器上的 TripoSR 出模服务。 */
    val localServiceUrl: String = ""
) {
    val baseUrl: String
        get() = if (globalRegion) "https://api.aholo.ai/global" else "https://api.aholo3d.cn"
}

data class AssetItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val coverPath: String?,
    val modelPath: String,
    val taskId: Long,
    val version: String,
    val faceCount: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val sourceCount: Int,
    val demo: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("coverPath", coverPath ?: JSONObject.NULL)
        put("modelPath", modelPath)
        put("taskId", taskId)
        put("version", version)
        put("faceCount", faceCount)
        put("durationMs", durationMs)
        put("sizeBytes", sizeBytes)
        put("sourceCount", sourceCount)
        put("demo", demo)
    }

    companion object {
        fun fromJson(o: JSONObject): AssetItem = AssetItem(
            id = o.getString("id"),
            name = o.optString("name", "未命名"),
            createdAt = o.optLong("createdAt"),
            coverPath = if (o.isNull("coverPath")) null else o.optString("coverPath"),
            modelPath = o.getString("modelPath"),
            taskId = o.optLong("taskId"),
            version = o.optString("version", "G1-Turbo"),
            faceCount = o.optInt("faceCount", 0),
            durationMs = o.optLong("durationMs"),
            sizeBytes = o.optLong("sizeBytes"),
            sourceCount = o.optInt("sourceCount", 0),
            demo = o.optBoolean("demo", false)
        )

        fun listFromJson(s: String): List<AssetItem> = runCatching {
            val arr = JSONArray(s)
            buildList {
                for (i in 0 until arr.length()) add(fromJson(arr.getJSONObject(i)))
            }
        }.getOrDefault(emptyList())
    }
}

enum class JobPhase { PREPARING, UPLOADING, SUBMITTING, GENERATING, DOWNLOADING, DONE, FAILED, CANCELED }

data class JobUi(
    val id: String = "",
    val name: String = "",
    val frames: List<String> = emptyList(),
    val coverPath: String? = null,
    val engine: String = "G1-Turbo",
    val faceCount: Int = 200_000,
    val demo: Boolean = false,
    val seedX: Float = 0.5f,
    val seedY: Float = 0.5f,
    val phase: JobPhase = JobPhase.PREPARING,
    val uploaded: Int = 0,
    val total: Int = 0,
    val progress: Float = 0f,
    val taskId: Long? = null,
    val message: String = "",
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
) {
    val isActive: Boolean
        get() = phase == JobPhase.PREPARING || phase == JobPhase.UPLOADING ||
                phase == JobPhase.SUBMITTING || phase == JobPhase.GENERATING ||
                phase == JobPhase.DOWNLOADING

    val phaseLabel: String
        get() = when (phase) {
            JobPhase.PREPARING -> "准备中"
            JobPhase.UPLOADING -> "上传底片"
            JobPhase.SUBMITTING -> "提交任务"
            JobPhase.GENERATING -> "重建中"
            JobPhase.DOWNLOADING -> "写入本地"
            JobPhase.DONE -> "完成"
            JobPhase.FAILED -> "失败"
            JobPhase.CANCELED -> "已中止"
        }
}

data class ConnectionProbe(
    val checking: Boolean = false,
    val ok: Boolean = false,
    val message: String = ""
)
