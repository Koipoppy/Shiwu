package com.shiwu.scan3d.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 本机出模服务（PC 上跑 TripoSR）的客户端。
 *
 * 手机端只负责发一张底片、收回一个 GLB —— 419M 的生成模型留在电脑上，
 * 避免往 Android 里塞一个 800MB 的 ONNX 图和一套三平面解码器。
 */
class ReconService(baseUrl: String) {

    private val root: String = normalize(baseUrl)

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 探测服务是否就绪，返回耗时毫秒。 */
    suspend fun health(): Long = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val request = Request.Builder().url("$root/health").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException("服务返回 ${response.code}")
            if (!body.contains("\"ok\": true") && !body.contains("\"ok\":true")) {
                throw ApiException("服务未就绪")
            }
        }
        System.currentTimeMillis() - started
    }

    /** 上传一张底片，返回 GLB 字节。 */
    suspend fun reconstruct(jpeg: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$root/reconstruct")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                val text = bytes.toString(Charsets.UTF_8)
                val message = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"")
                    .find(text)?.groupValues?.getOrNull(1) ?: "服务返回 ${response.code}"
                throw ApiException(message)
            }
            if (bytes.size < 12) throw ApiException("服务返回空模型")
            bytes
        }
    }

    companion object {
        /** 允许用户只填主机名/端口，缺协议和端口时补默认值。 */
        fun normalize(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (url.isEmpty()) return url
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            val afterScheme = url.substringAfter("://")
            if (!afterScheme.contains(':')) url = "$url:8189"
            return url
        }
    }
}
