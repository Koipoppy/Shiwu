package com.shiwu.scan3d.data

import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

data class UploadToken(val token: String, val domain: String, val blockSize: Long)

data class TaskSnapshot(
    val status: Int,
    val outputs: List<String>,
    val message: String
) {
    val isSuccess get() = status == 3
    val isTerminal get() = status == 3 || status == 4 || status == 6
}

class AholoApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---------- 连通性 ----------

    fun probe(baseUrl: String, apiKey: String): Long {
        val start = System.currentTimeMillis()
        val req = Request.Builder()
            .url("$baseUrl/lux3d/v1/generate/task/list?page=1&pagesize=1")
            .header("Authorization", apiKey)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw ApiException("API Key 无效（401），请检查后重试")
            if (!resp.isSuccessful) throw ApiException("连接失败 HTTP ${resp.code}")
            val c = runCatching { JSONObject(body).optString("c") }.getOrDefault("")
            if (c != "0") throw ApiException("接口返回异常：${friendly(body)}")
        }
        return System.currentTimeMillis() - start
    }

    // ---------- 素材上传 ----------

    fun fetchUploadToken(baseUrl: String, apiKey: String): UploadToken {
        val req = Request.Builder()
            .url("$baseUrl/asset/v1/token")
            .header("Authorization", apiKey)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("获取上传凭证失败 HTTP ${resp.code}")
            val o = JSONObject(body)
            val token = o.optString("ousToken")
            val domain = o.optString("globalDomain")
            if (token.isBlank() || domain.isBlank()) throw ApiException("上传凭证为空，请稍后再试")
            return UploadToken(token, domain, o.optLong("blockSize", 5 * 1024 * 1024))
        }
    }

    /**
     * 单文件直传 OUS，随后轮询上传状态直到拿到可访问 URL。
     * 注意：上传凭证与一次上传任务绑定，因此每个文件都要单独取 token。
     */
    fun uploadFile(token: UploadToken, file: File, md5: String): String {
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("md5", md5)
            .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()

        val req = Request.Builder()
            .url("${token.domain}/ous/api/v2/single/upload")
            .header("ous-token-v2", token.token)
            .post(part)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("上传失败 HTTP ${resp.code}")
            val o = JSONObject(body)
            if (o.optString("c") != "0") throw ApiException("上传失败：${o.optString("m")}")
        }

        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(400)
            val statusReq = Request.Builder()
                .url("${token.domain}/ous/api/v2/upload/status")
                .header("ous-token-v2", token.token)
                .get()
                .build()
            client.newCall(statusReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use
                val d = runCatching { JSONObject(body).optJSONObject("d") }.getOrNull() ?: return@use
                when (d.optInt("status", -1)) {
                    5 -> {
                        val url = d.optString("url")
                        if (url.isBlank()) throw ApiException("上传完成但未返回地址")
                        return url
                    }
                    6, 8 -> throw ApiException("上传被中断，请检查网络后重试")
                }
            }
        }
        throw ApiException("上传超时，请检查网络")
    }

    // ---------- Lux3D ----------

    fun createImgTo3d(
        baseUrl: String,
        apiKey: String,
        imageUrls: List<String>,
        version: String,
        faceCount: Int
    ): Long {
        val payload = JSONObject().apply {
            put("imgs", JSONArray(imageUrls))
            put("version", version)
            put("faceCount", faceCount)
            put("outputFormat", JSONArray(listOf("glb")))
            put("enablePbr", true)
            put("aiPredictSize", true)
        }
        val req = Request.Builder()
            .url("$baseUrl/lux3d/v1/generate/img-to-3d/task/create")
            .header("Authorization", apiKey)
            .post(payload.toString().toRequestBody(jsonType))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw ApiException("API Key 无效（401）")
            if (!resp.isSuccessful) throw ApiException("创建任务失败 HTTP ${resp.code}")
            val o = JSONObject(body)
            val c = o.optString("c")
            when {
                c == "0" -> {}
                c.contains("CONCURRENCY") ->
                    throw ApiException("当前已有进行中的生成任务，等它完成后再试（免费账号同时只有 5 个）")
                c.contains("CREDIT") || c.contains("INSUFFICIENT") ->
                    throw ApiException("积分不足，请到 Lux3D 控制台补充积分")
                else -> throw ApiException(o.optString("m").ifBlank { "创建任务失败（$c）" })
            }
            val id = o.opt("d")
            val taskId = when (id) {
                is Number -> id.toLong()
                is String -> id.toLongOrNull() ?: 0L
                else -> 0L
            }
            if (taskId <= 0L) throw ApiException("创建任务未返回任务号")
            return taskId
        }
    }

    fun queryTask(baseUrl: String, apiKey: String, taskId: Long): TaskSnapshot {
        val req = Request.Builder()
            .url("$baseUrl/lux3d/v1/generate/task/get?taskid=$taskId")
            .header("Authorization", apiKey)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("查询任务失败 HTTP ${resp.code}")
            val o = JSONObject(body)
            if (o.optString("c") != "0") throw ApiException(o.optString("m").ifBlank { "查询任务失败" })
            val d = o.optJSONObject("d") ?: return TaskSnapshot(1, emptyList(), "")
            val outputs = mutableListOf<String>()
            d.optJSONArray("outputs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val content = arr.optJSONObject(i)?.optString("content").orEmpty()
                    if (content.isNotBlank()) outputs += content
                }
            }
            return TaskSnapshot(
                status = d.optInt("status", 1),
                outputs = outputs,
                message = (runCatching { JSONObject(outputs.firstOrNull().orEmpty()).optString("message") }
                    .getOrDefault(""))
            )
        }
    }

    fun pickGlbUrl(outputs: List<String>): String? {
        val clean = outputs.filter { it.isNotBlank() && it != "NOT_REQUESTED" && it.startsWith("http") }
        return clean.firstOrNull { it.substringBefore('?').endsWith(".glb", true) }
            ?: clean.lastOrNull()
    }

    fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("下载模型失败 HTTP ${resp.code}")
            val bodyStream = resp.body?.byteStream() ?: throw ApiException("下载内容为空")
            val total = resp.body?.contentLength() ?: -1L
            dest.parentFile?.mkdirs()
            var read = 0L
            val buffer = ByteArray(64 * 1024)
            dest.outputStream().use { out ->
                while (true) {
                    val n = bodyStream.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
                out.flush()
            }
        }
    }

    private fun friendly(raw: String): String = runCatching {
        val o = JSONObject(raw)
        o.optString("m").ifBlank { o.optString("status") }
    }.getOrDefault(raw.take(120))

    companion object {
        fun isCancel(t: Throwable) = t is CancellationException
    }
}
