package com.shiwu.scan3d.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shiwu.scan3d.data.AssetItem
import com.shiwu.scan3d.data.GalleryItem
import com.shiwu.scan3d.data.JobPhase
import com.shiwu.scan3d.data.JobUi
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.AmberDim
import com.shiwu.scan3d.ui.theme.Brass
import com.shiwu.scan3d.ui.theme.Cream
import com.shiwu.scan3d.ui.theme.Deck
import com.shiwu.scan3d.ui.theme.SignalRed
import com.shiwu.scan3d.ui.theme.glossy
import com.shiwu.scan3d.ui.theme.glow
import com.shiwu.scan3d.util.humanSize
import java.io.File

private val rowShape = RoundedCornerShape(4.dp)

const val API_KEY_URL =
    "https://lux3d.aholo3d.cn/lux3d/home?entry_type=website&auth_action=get_api&from=lux3d&lang=zh_CN"

@Composable
fun PanelTitle(main: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(Amber)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                main,
                style = MaterialTheme.typography.titleSmall.glossy(
                    base = Cream,
                    highlight = Color.White
                )
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 2.sp),
                color = AmberDim
            )
        }
    }
}

@Composable
fun RetroRow(
    label: String,
    sub: String,
    value: String,
    valueColor: Color = Amber,
    index: String? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = pressed && onClick != null

    Row(
        Modifier
            .fillMaxWidth()
            .clip(ChamferShape(5.dp))
            .background(if (active) Amber.copy(alpha = 0.07f) else Color.Transparent)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text(
                index,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.5.sp),
                color = AmberDim.copy(alpha = if (active) 0.95f else 0.6f),
                modifier = Modifier.width(17.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Cream)
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, letterSpacing = 1.8.sp),
                color = AmberDim.copy(alpha = 0.72f)
            )
        }
        ReadoutChip(value, valueColor)
    }
}

/** 右侧的框式读数：细黄铜框 + 同色余辉，像仪表上的一块翻牌显示。 */
@Composable
private fun ReadoutChip(text: String, color: Color) {
    val shape = ChamferShape(4.dp)
    Box(
        Modifier
            .clip(shape)
            .background(color.copy(alpha = if (color == Amber) 0.10f else 0.04f))
            .border(0.8.dp, color.copy(alpha = 0.38f), shape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium
                .copy(fontSize = 10.sp, letterSpacing = 1.sp)
                .glow(color, 8f, 0.45f),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DottedDivider() {
    HairlineRule()
}

// ---------------------------------------------------------------- CONFIG

@Composable
fun ConfigPanel(
    engineLabel: String,
    qualityLabel: String,
    regionLabel: String,
    demoMode: Boolean,
    hasKey: Boolean,
    frames: Int,
    probeText: String,
    probeOk: Boolean,
    serviceLabel: String,
    serviceOn: Boolean,
    onCycleEngine: () -> Unit,
    onCycleQuality: () -> Unit,
    onToggleRegion: () -> Unit,
    onToggleDemo: () -> Unit,
    onEditKey: () -> Unit,
    onEditService: () -> Unit,
    onOpenKeySite: () -> Unit,
    onOpenGuide: () -> Unit,
    onTest: () -> Unit,
    onOpenImport: () -> Unit,
    onClearFrames: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = 46.dp, bottom = 18.dp)
    ) {
        PanelTitle("配置 / MODEL CONFIG", "SYSTEM PARAMETERS")
        HairlineRule(Modifier.padding(top = 6.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            RetroRow("生成引擎", "ENGINE", engineLabel, index = "01", onClick = onCycleEngine)
            DottedDivider()
            RetroRow("模型精度", "FIDELITY", qualityLabel, index = "02", onClick = onCycleQuality)
            DottedDivider()
            RetroRow("接口区域", "REGION", regionLabel, index = "03", onClick = onToggleRegion)
            DottedDivider()
            RetroRow(
                "演示模式",
                "DEMO MODE",
                if (demoMode) "ON" else "OFF",
                valueColor = if (demoMode) Amber else Cream.copy(alpha = 0.5f),
                index = "04",
                onClick = onToggleDemo
            )
            DottedDivider()
            RetroRow(
                "出模服务",
                "TRIPO SERVICE",
                serviceLabel,
                valueColor = if (serviceOn) Amber else Cream.copy(alpha = 0.5f),
                index = "05",
                onClick = onEditService
            )
            DottedDivider()
            RetroRow(
                "链路测试",
                "LINK TEST",
                probeText,
                valueColor = if (probeOk) Amber else Cream.copy(alpha = 0.55f),
                index = "06",
                onClick = onTest
            )
            DottedDivider()
            RetroRow("导入底片", "IMPORT FRAMES", "$frames / 12", index = "07", onClick = onOpenImport)
            DottedDivider()
            RetroRow("操作引导", "FIELD MANUAL", "VIEW", index = "08", onClick = onOpenGuide)
            DottedDivider()
            RetroRow(
                "清空底片",
                "CLEAR FRAMES",
                "CLEAR",
                valueColor = SignalRed.copy(alpha = 0.85f),
                index = "09",
                onClick = onClearFrames
            )
        }

        Spacer(Modifier.height(6.dp))
        ServiceBay(hasKey = hasKey, onEditKey = onEditKey, onOpenSite = onOpenKeySite)
    }
}

/** 配置页底部的“服务舱”：接口密钥单独放在这里，和日常参数分开。 */
@Composable
private fun ServiceBay(hasKey: Boolean, onEditKey: () -> Unit, onOpenSite: () -> Unit) {
    val shape = ChamferShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF11150F), Color(0xFF080A07)))
            )
            .border(1.dp, Brass.copy(alpha = 0.5f), shape)
    ) {
        HazardStripe(height = 3.dp)
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(AmberDim, size.minDimension * 0.30f, c)
                    drawRect(
                        color = AmberDim,
                        topLeft = Offset(c.x - size.width * 0.08f, c.y),
                        size = Size(size.width * 0.16f, size.height * 0.32f)
                    )
                }
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "服务舱 / SERVICE BAY",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, letterSpacing = 1.8.sp),
                        color = AmberDim
                    )
                    Text(
                        if (hasKey) "接口密钥已装载" else "接口密钥未装载",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasKey) Cream.copy(alpha = 0.85f) else SignalRed
                    )
                }
                LedDot(on = hasKey, color = if (hasKey) Amber else SignalRed, diameter = 6.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    if (hasKey) "SET" else "MISSING",
                    style = MaterialTheme.typography.labelSmall
                        .copy(fontSize = 8.sp, letterSpacing = 1.2.sp)
                        .glow(if (hasKey) Amber else SignalRed, 8f, 0.4f),
                    color = if (hasKey) Amber else SignalRed
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelAction("写入密钥", onClick = onEditKey, modifier = Modifier.weight(1f))
                PanelAction("获取密钥 ↗", onClick = onOpenSite, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- ARCHIVE

@Composable
fun ArchivePanel(
    assets: List<AssetItem>,
    jobs: List<JobUi>,
    onOpen: (AssetItem) -> Unit,
    onCancelJob: (String) -> Unit,
    onRetryJob: (String) -> Unit,
    onDiscardJob: (String) -> Unit
) {
    val orderedJobs = jobs.sortedWith(
        compareByDescending<JobUi> { it.isActive }.thenByDescending { it.startedAt }
    )
    val total = assets.size + orderedJobs.size

    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 46.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("资产仓库 / ARCHIVE", "LOCAL STORAGE")
            Spacer(Modifier.weight(1f))
            Text(
                "%02d".format(total),
                style = MaterialTheme.typography.titleLarge.glossy(),
                color = Amber
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "REC",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, letterSpacing = 1.6.sp),
                color = AmberDim.copy(alpha = 0.7f)
            )
        }
        HairlineRule(Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))

        if (total == 0) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NO RECORDS", style = MaterialTheme.typography.titleMedium, color = AmberDim)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "拍几张底片再长按提交",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cream.copy(alpha = 0.45f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 118.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    orderedJobs,
                    key = { "job_${it.id}" },
                    span = { GridItemSpan(maxLineSpan) }
                ) { job ->
                    JobCard(
                        job = job,
                        onCancel = { onCancelJob(job.id) },
                        onRetry = { onRetryJob(job.id) },
                        onDiscard = { onDiscardJob(job.id) }
                    )
                }
                items(assets, key = { it.id }) { asset ->
                    ArchiveCell(asset) { onOpen(asset) }
                }
            }
        }
    }
}

/** 正在处理 / 失败的扫描任务；底片还在，可以重试。 */
@Composable
private fun JobCard(
    job: JobUi,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    val failed = job.phase == JobPhase.FAILED
    val accent = when {
        job.isActive -> Amber
        failed -> SignalRed
        else -> AmberDim
    }
    val cardShape = ChamferShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF14170F), Color(0xFF0A0D09))))
            .border(1.dp, accent.copy(alpha = 0.5f), cardShape)
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF11150F))
            ) {
                val cover = job.coverPath
                if (cover != null && File(cover).exists()) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("IMG", style = MaterialTheme.typography.labelSmall, color = AmberDim)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LedDot(on = job.isActive, color = accent, diameter = 5.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        buildString {
                            append(job.phaseLabel)
                            append(" · ")
                            append(job.frames.size)
                            append(" 帧")
                            job.taskId?.let { append(" · #").append(it) }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                        color = accent
                    )
                }
                if (job.isActive) {
                    Spacer(Modifier.height(5.dp))
                    SegmentBar(
                        filled = (job.progress * 18).toInt(),
                        total = 18,
                        height = 4.dp
                    )
                    Text(
                        "${job.message}  ${(job.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = Cream.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val hint = job.error.orEmpty()
                    if (hint.isNotBlank()) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = if (failed) SignalRed else Cream.copy(alpha = 0.5f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "底片已保留，可直接重试",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = AmberDim
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (job.isActive) {
                    PanelAction("中止", onClick = onCancel, modifier = Modifier.width(70.dp))
                } else {
                    PanelAction("重试", onClick = onRetry, modifier = Modifier.width(70.dp))
                    Spacer(Modifier.height(4.dp))
                    PanelAction("放弃", onClick = onDiscard, modifier = Modifier.width(70.dp))
                }
            }
        }
    }
}

@Composable
private fun ArchiveCell(asset: AssetItem, onClick: () -> Unit) {
    val cardShape = ChamferShape(6.dp)
    Column(
        Modifier
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF171A14), Color(0xFF0D100C))))
            .border(0.9.dp, Brass.copy(alpha = 0.38f), cardShape)
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ChamferShape(4.dp))
                .background(Color(0xFF0A0D09))
        ) {
            val cover = asset.coverPath
            if (cover != null && File(cover).exists()) {
                AsyncImage(
                    model = File(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("3D", style = MaterialTheme.typography.titleMedium, color = AmberDim)
                }
            }
            // 终端里的读数感：扫描线 + 一层压暗的渐变
            ScanlineOverlay(Modifier.fillMaxSize(), step = 4.dp, alpha = 0.15f)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )
            Text(
                asset.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, letterSpacing = 0.3.sp),
                color = Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
            )
            // 版本挂牌
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(ChamferShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.66f))
                    .border(0.7.dp, Brass.copy(alpha = 0.42f), ChamferShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    asset.version,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 0.6.sp),
                    color = Amber
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${humanSize(asset.sizeBytes)} · ${asset.sourceCount} 帧",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 0.8.sp),
                color = AmberDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "GLB",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 1.sp),
                color = Cream.copy(alpha = 0.35f)
            )
        }
    }
}

// ---------------------------------------------------------------- PROCESSING

@Composable
fun ProcessingPanel(job: JobUi, onCancel: () -> Unit, onRetry: () -> Unit) {
    val failed = job.phase == JobPhase.FAILED
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 46.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PanelTitle(
            if (failed) "处理失败 / FAULT" else "成像中 / PROCESSING",
            if (failed) "CHECK MATERIAL" else "PLEASE HOLD"
        )

        Spacer(Modifier.weight(1f))

        if (failed) {
            Text(
                "ERROR",
                style = MaterialTheme.typography.headlineSmall,
                color = SignalRed
            )
            Spacer(Modifier.height(6.dp))
            Text(
                job.error ?: "未知错误",
                style = MaterialTheme.typography.bodySmall,
                color = Cream.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            Text(
                "${(job.progress * 100).toInt()}%",
                style = MaterialTheme.typography.displayMedium.glossy(),
                color = Amber
            )
            Spacer(Modifier.height(10.dp))
            Text(
                job.message,
                style = MaterialTheme.typography.bodySmall,
                color = Cream.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            SegmentBar(
                filled = (job.progress * 24).toInt(),
                total = 24,
                height = 6.dp
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            job.taskId?.let { "TASK #$it" } ?: "QUEUED",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.6.sp),
            color = AmberDim
        )
        Spacer(Modifier.height(10.dp))
        if (failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelAction("重试 / RETRY", onClick = onRetry, modifier = Modifier.weight(1f))
                PanelAction("返回 / BACK", onClick = onCancel, modifier = Modifier.weight(1f))
            }
        } else {
            PanelAction("中止 / ABORT", onClick = onCancel)
        }
    }
}

// ---------------------------------------------------------------- IMPORT

@Composable
fun ImportPanel(
    items: List<GalleryItem>,
    selected: List<Long>,
    loading: Boolean,
    onToggle: (Long) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 46.dp, bottom = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("导入底片 / IMPORT", "FROM ALBUM")
            Spacer(Modifier.weight(1f))
            Text(
                "${selected.size}",
                style = MaterialTheme.typography.titleLarge,
                color = Amber
            )
        }
        Spacer(Modifier.height(10.dp))

        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("READING…", style = MaterialTheme.typography.titleSmall, color = Amber)
            }
            items.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("相册里没有可用底片", style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.5f))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 82.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val index = selected.indexOf(item.id)
                    val cellShape = ChamferShape(4.dp)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(cellShape)
                            .then(
                                if (index >= 0) Modifier.border(1.4.dp, Amber, cellShape)
                                else Modifier.border(0.8.dp, Cream.copy(alpha = 0.10f), cellShape)
                            )
                            .clickable { onToggle(item.id) }
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        ScanlineOverlay(Modifier.fillMaxSize(), step = 4.dp, alpha = 0.12f)
                        if (index >= 0) {
                            Box(Modifier.fillMaxSize().background(Amber.copy(alpha = 0.20f)))
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(ChamferShape(2.dp))
                                    .background(Amber),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color(0xFF17130E),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelAction("取消 / CANCEL", onClick = onClose, modifier = Modifier.weight(1f))
            PanelAction(
                "载入 ${selected.size} 帧",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = selected.isNotEmpty()
            )
        }
    }
}

// ---------------------------------------------------------------- SHARED

@Composable
fun PanelAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    
    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(
                // 按下时的内部阴影效果
                if (pressed && enabled) 
                    Modifier.background(
                        Brush.verticalGradient(listOf(Color(0xFF18150F), Color(0xFF1C1812)))
                    )
                else 
                    Modifier.background(Color.Transparent)
            )
            .border(
                1.5.dp,
                if (enabled) {
                    if (pressed) Amber.copy(alpha = 0.65f)
                    else Brass.copy(alpha = 0.55f)
                } else Brass.copy(alpha = 0.2f),
                RoundedCornerShape(4.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) Amber else Cream.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun PreviewOverlay(
    asset: AssetItem,
    onClose: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxHeight().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, Brass.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✕", style = MaterialTheme.typography.labelMedium, color = Amber)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, Brass.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onShare)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("分享", style = MaterialTheme.typography.labelSmall, color = Amber)
            }
        }
        Spacer(Modifier.weight(1f))
        Column {
            Text(
                asset.name,
                style = MaterialTheme.typography.titleSmall,
                color = Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${asset.version} · ${asset.faceCount / 1000}K · ${humanSize(asset.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                color = AmberDim
            )
        }
    }
}
