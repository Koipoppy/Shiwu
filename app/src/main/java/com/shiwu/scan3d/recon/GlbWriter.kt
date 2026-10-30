package com.shiwu.scan3d.recon

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 极简二进制 glTF 2.0 导出：一个 mesh、顶点色材质，供 model-viewer 直接渲染。
 */
object GlbWriter {

    fun write(
        target: File,
        positions: FloatArray,
        normals: FloatArray,
        colors: FloatArray,
        indices: IntArray
    ): Boolean {
        if (positions.isEmpty() || indices.isEmpty()) return false

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
            i += 3
        }

        fun pad4(n: Int) = (n + 3) and (Int.MAX_VALUE - 3)

        val bin = ByteBuffer.allocate(
            positions.size * 4 + normals.size * 4 + colors.size * 4 + indices.size * 4
        ).order(ByteOrder.LITTLE_ENDIAN)

        fun addFloats(arr: FloatArray): Pair<Int, Int> {
            val start = bin.position()
            for (v in arr) bin.putFloat(v)
            val end = bin.position()
            val aligned = pad4(end)
            for (z in end until aligned) bin.put(0.toByte())
            return start to (end - start)
        }

        fun addInts(arr: IntArray): Pair<Int, Int> {
            val start = bin.position()
            for (v in arr) bin.putInt(v)
            val end = bin.position()
            val aligned = pad4(end)
            for (z in end until aligned) bin.put(0.toByte())
            return start to (end - start)
        }

        val (posOff, posLen) = addFloats(positions)
        val (nrmOff, nrmLen) = addFloats(normals)
        val (colOff, colLen) = addFloats(colors)
        val (idxOff, idxLen) = addInts(indices)
        val binLen = bin.position()
        bin.flip()

        val vertexCount = positions.size / 3
        val json = buildString {
            append("{")
            append("\"asset\":{\"version\":\"2.0\",\"generator\":\"shiwu-local-engine\"},")
            append("\"scene\":0,\"scenes\":[{\"nodes\":[0]}],")
            append("\"nodes\":[{\"mesh\":0}],")
            append("\"meshes\":[{\"primitives\":[{")
            append("\"attributes\":{\"POSITION\":0,\"NORMAL\":1,\"COLOR_0\":2},")
            append("\"indices\":3,\"material\":0")
            append("}]}],")
            append("\"materials\":[{")
            append("\"pbrMetallicRoughness\":{\"baseColorFactor\":[1.0,1.0,1.0,1.0],")
            append("\"metallicFactor\":0.0,\"roughnessFactor\":0.85},\"doubleSided\":true")
            append("}],")
            append("\"buffers\":[{\"byteLength\":").append(binLen).append("}],")
            append("\"bufferViews\":[")
            append("{\"buffer\":0,\"byteOffset\":$posOff,\"byteLength\":$posLen,\"target\":34962},")
            append("{\"buffer\":0,\"byteOffset\":$nrmOff,\"byteLength\":$nrmLen,\"target\":34962},")
            append("{\"buffer\":0,\"byteOffset\":$colOff,\"byteLength\":$colLen,\"target\":34962},")
            append("{\"buffer\":0,\"byteOffset\":$idxOff,\"byteLength\":$idxLen,\"target\":34963}")
            append("],")
            append("\"accessors\":[")
            append("{\"bufferView\":0,\"componentType\":5126,\"count\":$vertexCount,\"type\":\"VEC3\",")
            append("\"min\":[$minX,$minY,$minZ],\"max\":[$maxX,$maxY,$maxZ]},")
            append("{\"bufferView\":1,\"componentType\":5126,\"count\":$vertexCount,\"type\":\"VEC3\"},")
            append("{\"bufferView\":2,\"componentType\":5126,\"count\":$vertexCount,\"type\":\"VEC3\"},")
            append("{\"bufferView\":3,\"componentType\":5125,\"count\":${indices.size},\"type\":\"SCALAR\"}")
            append("]")
            append("}")
        }

        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val jsonPadded = pad4(jsonBytes.size)
        val total = 12 + 8 + jsonPadded + 8 + binLen

        val out = FileOutputStream(target)
        val head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(0x46546C67) // glTF
        head.putInt(2)
        head.putInt(total)
        out.write(head.array())

        val jsonHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        jsonHeader.putInt(jsonPadded)
        jsonHeader.putInt(0x4E4F534A) // JSON
        out.write(jsonHeader.array())
        out.write(jsonBytes)
        repeat(jsonPadded - jsonBytes.size) { out.write(0x20) }

        val binHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        binHeader.putInt(binLen)
        binHeader.putInt(0x004E4942) // BIN
        out.write(binHeader.array())
        out.write(bin.array())
        out.close()
        return true
    }
}
