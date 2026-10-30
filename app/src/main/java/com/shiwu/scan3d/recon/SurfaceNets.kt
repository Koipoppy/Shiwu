package com.shiwu.scan3d.recon

/**
 * Naive Surface Nets：标量场（`> iso` 为内部）→ 平滑三角网格。
 *
 * 顶点放在「跨越等值面的那条边上按值线性插值」的位置，所以既吃得下二值占据场
 * （[LocalReconstructor] 的雕刻结果），也吃得下 TripoSR 那种连续密度场。
 */
object SurfaceNets {

    data class Mesh(val positions: FloatArray, val normals: FloatArray, val indices: IntArray)

    private val CORNER_X = intArrayOf(0, 1, 0, 1, 0, 1, 0, 1)
    private val CORNER_Y = intArrayOf(0, 0, 1, 1, 0, 0, 1, 1)
    private val CORNER_Z = intArrayOf(0, 0, 0, 0, 1, 1, 1, 1)
    private val EDGE_A = intArrayOf(0, 2, 4, 6, 0, 1, 4, 5, 0, 1, 2, 3)
    private val EDGE_B = intArrayOf(1, 3, 5, 7, 2, 3, 6, 7, 4, 5, 6, 7)

    fun build(
        field: FloatArray,
        nx: Int,
        ny: Int,
        nz: Int,
        smoothingPasses: Int = 2,
        iso: Float = 0f
    ): Mesh {
        val cellNx = nx - 1
        val cellNy = ny - 1
        val cellNz = nz - 1
        if (cellNx < 1 || cellNy < 1 || cellNz < 1) {
            return Mesh(FloatArray(0), FloatArray(0), IntArray(0))
        }
        val cellCount = cellNx * cellNy * cellNz
        val cellVertex = IntArray(cellCount) { -1 }
        fun cellIdx(x: Int, y: Int, z: Int) = x + y * cellNx + z * cellNx * cellNy

        fun valueAt(x: Int, y: Int, z: Int): Float =
            if (x in 0 until nx && y in 0 until ny && z in 0 until nz) {
                field[x + y * nx + z * nx * ny]
            } else {
                iso - 1f
            }

        val positions = ArrayList<Float>(cellCount * 3)

        // 1) 每个符号翻转的 cell 放一个顶点：12 条跨越边上插值点的平均
        for (z in 0 until cellNz) {
            for (y in 0 until cellNy) {
                for (x in 0 until cellNx) {
                    var maskBits = 0
                    for (c in 0 until 8) {
                        if (valueAt(x + CORNER_X[c], y + CORNER_Y[c], z + CORNER_Z[c]) > iso) {
                            maskBits = maskBits or (1 shl c)
                        }
                    }
                    if (maskBits == 0 || maskBits == 0xFF) continue

                    var sx = 0f
                    var sy = 0f
                    var sz = 0f
                    var n = 0
                    for (e in 0 until 12) {
                        val ca = EDGE_A[e]
                        val cb = EDGE_B[e]
                        val va = valueAt(x + CORNER_X[ca], y + CORNER_Y[ca], z + CORNER_Z[ca])
                        val vb = valueAt(x + CORNER_X[cb], y + CORNER_Y[cb], z + CORNER_Z[cb])
                        if ((va > iso) == (vb > iso)) continue
                        val denom = vb - va
                        val t = if (kotlin.math.abs(denom) < 1e-12f) 0.5f else (iso - va) / denom
                        val ax = (x + CORNER_X[ca]).toFloat()
                        val ay = (y + CORNER_Y[ca]).toFloat()
                        val az = (z + CORNER_Z[ca]).toFloat()
                        sx += ax + t * ((x + CORNER_X[cb]) - ax)
                        sy += ay + t * ((y + CORNER_Y[cb]) - ay)
                        sz += az + t * ((z + CORNER_Z[cb]) - az)
                        n++
                    }
                    if (n == 0) continue
                    cellVertex[cellIdx(x, y, z)] = positions.size / 3
                    positions.add(sx / n)
                    positions.add(sy / n)
                    positions.add(sz / n)
                }
            }
        }
        if (positions.isEmpty()) {
            return Mesh(FloatArray(0), FloatArray(0), IntArray(0))
        }

        // 2) 生成 quad：沿轴跨越边的四个相邻 cell
        val indices = ArrayList<Int>(cellCount * 12)
        val axes = arrayOf(intArrayOf(1, 0, 0), intArrayOf(0, 1, 0), intArrayOf(0, 0, 1))
        for (z in 0 until cellNz) {
            for (y in 0 until cellNy) {
                for (x in 0 until cellNx) {
                    val v0 = cellVertex[cellIdx(x, y, z)]
                    if (v0 < 0) continue
                    for (axis in 0 until 3) {
                        val dx = axes[axis][0]
                        val dy = axes[axis][1]
                        val dz = axes[axis][2]
                        val a = valueAt(x, y, z) > iso
                        val b = valueAt(x + dx, y + dy, z + dz) > iso
                        if (a == b) continue

                        val u = axes[(axis + 1) % 3]
                        val v = axes[(axis + 2) % 3]
                        // 与这条跨越边共边的四个 cell：本格，以及沿 u/v 各退一格。
                        // 退的方向必须是负向：边 (x,y,z)->(x+d) 只落在「u/v 维上以本格为起点」的四个格里。
                        fun vAt(ox: Int, oy: Int, oz: Int): Int {
                            val cx = x + ox
                            val cy = y + oy
                            val cz = z + oz
                            if (cx < 0 || cy < 0 || cz < 0 ||
                                cx >= cellNx || cy >= cellNy || cz >= cellNz
                            ) return -1
                            return cellVertex[cellIdx(cx, cy, cz)]
                        }
                        // 四个格绕这条边成环：本格 → 退 u → 退 u 退 v → 退 v
                        val v2 = vAt(-u[0], -u[1], -u[2])
                        val v3 = vAt(-u[0] - v[0], -u[1] - v[1], -u[2] - v[2])
                        val v4 = vAt(-v[0], -v[1], -v[2])
                        if (v2 < 0 || v3 < 0 || v4 < 0) continue

                        if (a) {
                            indices.add(v0); indices.add(v2); indices.add(v3)
                            indices.add(v0); indices.add(v3); indices.add(v4)
                        } else {
                            indices.add(v0); indices.add(v4); indices.add(v3)
                            indices.add(v0); indices.add(v3); indices.add(v2)
                        }
                    }
                }
            }
        }

        // 3) 拉普拉斯平滑
        val vertexCount = positions.size / 3
        repeat(smoothingPasses) {
            val acc = FloatArray(positions.size)
            val cnt = IntArray(vertexCount)
            var i = 0
            while (i < indices.size) {
                for (e in 0 until 4) {
                    val a = indices[i + e]
                    val b = indices[i + (e + 1) % 4]
                    acc[a * 3] += positions[b * 3]
                    acc[a * 3 + 1] += positions[b * 3 + 1]
                    acc[a * 3 + 2] += positions[b * 3 + 2]
                    acc[b * 3] += positions[a * 3]
                    acc[b * 3 + 1] += positions[a * 3 + 1]
                    acc[b * 3 + 2] += positions[a * 3 + 2]
                    cnt[a]++; cnt[b]++
                }
                i += 6
            }
            for (v in 0 until vertexCount) {
                if (cnt[v] == 0) continue
                for (c in 0 until 3) {
                    positions[v * 3 + c] = positions[v * 3 + c] * 0.5f +
                            (acc[v * 3 + c] / cnt[v]) * 0.5f
                }
            }
        }

        // 4) 顶点法线
        val normals = FloatArray(positions.size)
        var i = 0
        while (i < indices.size) {
            val a = indices[i] * 3
            val b = indices[i + 1] * 3
            val c = indices[i + 2] * 3
            val e1x = positions[b] - positions[a]
            val e1y = positions[b + 1] - positions[a + 1]
            val e1z = positions[b + 2] - positions[a + 2]
            val e2x = positions[c] - positions[a]
            val e2y = positions[c + 1] - positions[a + 1]
            val e2z = positions[c + 2] - positions[a + 2]
            val nxv = e1y * e2z - e1z * e2y
            val nyv = e1z * e2x - e1x * e2z
            val nzv = e1x * e2y - e1y * e2x
            normals[a] += nxv; normals[a + 1] += nyv; normals[a + 2] += nzv
            normals[b] += nxv; normals[b + 1] += nyv; normals[b + 2] += nzv
            normals[c] += nxv; normals[c + 1] += nyv; normals[c + 2] += nzv
            i += 3
        }
        for (v in 0 until vertexCount) {
            val len = kotlin.math.sqrt(
                normals[v * 3] * normals[v * 3] +
                        normals[v * 3 + 1] * normals[v * 3 + 1] +
                        normals[v * 3 + 2] * normals[v * 3 + 2]
            )
            if (len > 1e-8f) {
                normals[v * 3] /= len
                normals[v * 3 + 1] /= len
                normals[v * 3 + 2] /= len
            } else {
                normals[v * 3 + 1] = 1f
            }
        }

        return Mesh(positions.toFloatArray(), normals, indices.toIntArray())
    }
}
