package com.shiwu.scan3d.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

class AssetStore(private val context: Context) {

    val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }
    val scansDir: File = File(context.filesDir, "scans").apply { mkdirs() }
    private val libraryFile: File = File(context.filesDir, "library.json")
    private val lock = Any()

    fun list(): List<AssetItem> = synchronized(lock) {
        if (!libraryFile.exists()) return emptyList()
        val raw = runCatching { libraryFile.readText() }.getOrDefault("[]")
        AssetItem.listFromJson(raw).sortedByDescending { it.createdAt }
    }

    fun save(asset: AssetItem) = synchronized(lock) {
        val current = list().toMutableList()
        current.removeAll { it.id == asset.id }
        current.add(0, asset)
        write(current)
    }

    fun delete(id: String) = synchronized(lock) {
        val current = list()
        val target = current.firstOrNull { it.id == id }
        if (target != null) {
            runCatching { File(target.modelPath).delete() }
            runCatching { File(context.filesDir, "models/${target.id}.glb").delete() }
        }
        write(current.filterNot { it.id == id })
    }

    fun clearAll() = synchronized(lock) {
        modelsDir.listFiles()?.forEach { runCatching { it.delete() } }
        scansDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        write(emptyList())
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun modelFile(id: String): File = File(modelsDir, "$id.glb")

    private fun write(items: List<AssetItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        libraryFile.writeText(arr.toString())
    }
}
