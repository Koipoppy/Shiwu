package com.shiwu.scan3d

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.shiwu.scan3d.recon.LocalReconstructor
import com.shiwu.scan3d.ui.components.SubjectSegmenter
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 调试版专用：把一组底片直接喂给本地引擎，绕过相机与界面。
 *
 * adb shell am start -n com.shiwu.scan3d/.DebugReconProbe \
 *     --es dir /sdcard/Android/data/com.shiwu.scan3d/files/probe \
 *     --ez segment false
 */
class DebugReconProbe : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dirArg = intent.getStringExtra("dir")
        val useSegment = intent.getBooleanExtra("segment", false)
        val seedX = intent.getFloatExtra("seedX", 0.5f)
        val seedY = intent.getFloatExtra("seedY", 0.5f)
        val serviceUrl = intent.getStringExtra("service")
        val useTripo = intent.getBooleanExtra("tripo", false)
        Thread {
            val started = System.currentTimeMillis()
            if (useTripo) {
                // 端上 TripoSR：不联网，整张模型跑在手机上
                val outcome = runCatching {
                    if (!com.shiwu.scan3d.recon.TripoSrLocal.isReady(this)) {
                        throw IllegalStateException("端上模型未安装")
                    }
                    val dir = File(dirArg ?: "/sdcard/Download/probe")
                    val frame = (dir.listFiles() ?: emptyArray())
                        .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                        .minByOrNull { it.name } ?: error("没有底片")
                    Log.i(TAG, "tripo start frame=${frame.name} bytes=${frame.length()}")
                    val out = File(dir, "tripo_out.glb")
                    val segmenter = if (useSegment && com.shiwu.scan3d.BuildConfig.SUBJECT_ENABLED) {
                        com.shiwu.scan3d.ui.components.SubjectSegmenter(this@DebugReconProbe)
                    } else null
                    try {
                        runBlocking {
                            com.shiwu.scan3d.recon.TripoSrLocal.reconstruct(
                                this@DebugReconProbe, frame.absolutePath, segmenter,
                                seedX to seedY, out
                            ) { p, msg ->
                                Log.i(TAG, "progress ${(p * 100).toInt()}% $msg")
                            }
                        }
                    } finally {
                        runCatching { segmenter?.close() }
                    }
                }
                val ms = System.currentTimeMillis() - started
                outcome.fold(
                    onSuccess = { Log.i(TAG, "TRIPO_OK $ms ms file=${it.absolutePath} bytes=${it.length()}") },
                    onFailure = { Log.e(TAG, "TRIPO_FAIL after $ms ms", it) }
                )
                finish()
                return@Thread
            }
            if (serviceUrl != null) {
                // 走本机出模服务：验证 ReconService + 落盘
                val outcome = runCatching {
                    val dir = File(dirArg ?: "/sdcard/Download/probe")
                    val frame = (dir.listFiles() ?: emptyArray())
                        .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                        .minByOrNull { it.name } ?: error("没有底片")
                    Log.i(TAG, "service=$serviceUrl frame=${frame.name} bytes=${frame.length()}")
                    val service = com.shiwu.scan3d.data.ReconService(serviceUrl)
                    val healthMs = runBlocking { service.health() }
                    Log.i(TAG, "health=${healthMs}ms")
                    val glb = runBlocking { service.reconstruct(frame.readBytes()) }
                    val out = File(dir, "service_out.glb")
                    out.writeBytes(glb)
                    out
                }
                val ms = System.currentTimeMillis() - started
                outcome.fold(
                    onSuccess = { Log.i(TAG, "SERVICE_OK $ms ms file=${it.absolutePath} bytes=${it.length()}") },
                    onFailure = { Log.e(TAG, "SERVICE_FAIL after $ms ms", it) }
                )
                finish()
                return@Thread
            }
            val outcome = runCatching {
                val dir = File(
                    dirArg ?: (getExternalFilesDir("probe")?.absolutePath
                        ?: "/sdcard/Android/data/com.shiwu.scan3d/files/probe")
                )
                val frames = (dir.listFiles() ?: emptyArray())
                    .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                    .sortedBy { it.name }
                    .map { it.absolutePath }
                Log.i(TAG, "probe start dir=${dir.absolutePath} frames=${frames.size} segment=$useSegment")
                if (frames.size < 3) throw IllegalStateException("底片不足：${frames.size}")
                val segmenter = if (useSegment) SubjectSegmenter(this) else null
                val target = File(dir, "probe_out.glb")
                try {
                    runBlocking {
                        LocalReconstructor.reconstruct(
                            frames = frames,
                            segmenter = segmenter,
                            seed = seedX to seedY,
                            useSegmenter = segmenter != null,
                            target = target
                        ) { p, msg ->
                            Log.i(TAG, "progress ${(p * 100).toInt()}% $msg")
                        }
                    }
                } finally {
                    runCatching { segmenter?.close() }
                }
                target
            }
            val ms = System.currentTimeMillis() - started
            outcome.fold(
                onSuccess = { Log.i(TAG, "PROBE_OK $ms ms file=${it.absolutePath} bytes=${it.length()}") },
                onFailure = { Log.e(TAG, "PROBE_FAIL after $ms ms", it) }
            )
            runCatching {
                File(getExternalFilesDir(null), "probe_result.txt").writeText(
                    outcome.fold(
                        onSuccess = { "OK $ms ms ${it.length()} bytes" },
                        onFailure = { "FAIL $ms ms\n" + Log.getStackTraceString(it) }
                    )
                )
            }
            finish()
        }.apply { name = "recon-probe" }.start()
    }

    private companion object {
        const val TAG = "ReconProbe"
    }
}
