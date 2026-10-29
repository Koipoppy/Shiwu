package com.shiwu.scan3d.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiwu.scan3d.data.JobPhase
import com.shiwu.scan3d.data.JobUi
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.components.AmbientBackdrop
import com.shiwu.scan3d.ui.components.ProgressRing
import com.shiwu.scan3d.ui.theme.Danger
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.InkSoft
import com.shiwu.scan3d.ui.theme.Line
import com.shiwu.scan3d.ui.theme.Mint100
import com.shiwu.scan3d.ui.theme.Mint50
import com.shiwu.scan3d.ui.theme.Mint500
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White
import kotlinx.coroutines.delay

@Composable
fun ProgressScreen(vm: AppViewModel, state: AppState) {
    val job = state.job ?: JobUi()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedSeconds = ((job.finishedAt ?: now) - job.startedAt) / 1000

    AmbientBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(White)
                        .clickable {
                            if (job.isActive) vm.cancelGeneration() else vm.back()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "取消",
                        tint = InkSoft,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "已用 ${elapsedSeconds}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkFaint
                )
            }

            Spacer(Modifier.weight(1f))

            if (job.phase == JobPhase.FAILED) {
                FailureBlock(job = job, onRetry = { vm.retryGeneration() }, onBack = { vm.back() })
            } else {
                RunningBlock(job = job)
            }

            Spacer(Modifier.weight(1f))

            if (job.phase != JobPhase.FAILED) {
                Text(
                    "生成期间可以息屏，但别退出 App。\n结果会保存到资产库。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "取消生成",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { vm.cancelGeneration() }
                        .padding(vertical = 14.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RunningBlock(job: JobUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = if (job.phase == JobPhase.DONE) 1f else job.progress,
                modifier = Modifier.size(168.dp),
                strokeWidth = 9.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(job.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    phaseLabel(job.phase),
                    style = MaterialTheme.typography.labelMedium,
                    color = Mint700
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            job.message.ifBlank { "正在处理" },
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(26.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(White)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            StepRow(
                title = "上传素材",
                detail = if (job.total > 0) "${job.uploaded}/${job.total} 张" else "",
                state = stepState(job, 0)
            )
            StepRow(
                title = "提交生成任务",
                detail = job.taskId?.let { "#$it" } ?: "",
                state = stepState(job, 1)
            )
            StepRow(
                title = "AI 重建几何与材质",
                detail = "",
                state = stepState(job, 2)
            )
            StepRow(
                title = "下载模型到本机",
                detail = "",
                state = stepState(job, 3),
                last = true
            )
        }
    }
}

@Composable
private fun FailureBlock(job: JobUi, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0xFFFADAD6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Danger, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("这次没生成成功", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            job.error ?: "未知错误",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(White)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Text("返回素材", style = MaterialTheme.typography.labelLarge, color = InkSoft)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Mint700)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重试", style = MaterialTheme.typography.labelLarge, color = White)
                }
            }
        }
    }
}

@Composable
private fun StepRow(title: String, detail: String, state: Int, last: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        2 -> Mint700
                        1 -> Mint100
                        else -> Color(0xFFF1F0EA)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                2 -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(14.dp)
                )
                1 -> CircularProgressIndicator(
                    color = Mint700,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(13.dp)
                )
                else -> Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(InkFaint.copy(alpha = 0.4f))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state == 0) InkFaint else Ink,
            modifier = Modifier.weight(1f)
        )
        if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
    }
    if (!last) {
        Box(
            Modifier
                .padding(start = 11.dp)
                .width(1.dp)
                .height(4.dp)
                .background(Line)
        )
    }
}

private fun phaseLabel(phase: JobPhase): String = when (phase) {
    JobPhase.PREPARING -> "准备中"
    JobPhase.UPLOADING -> "上传素材"
    JobPhase.SUBMITTING -> "提交任务"
    JobPhase.GENERATING -> "生成中"
    JobPhase.DOWNLOADING -> "下载模型"
    JobPhase.DONE -> "完成"
    else -> "处理中"
}

/** 0 = 未开始, 1 = 进行中, 2 = 已完成 */
private fun stepState(job: JobUi, step: Int): Int {
    val index = when (job.phase) {
        JobPhase.PREPARING -> 0
        JobPhase.UPLOADING -> 0
        JobPhase.SUBMITTING -> 1
        JobPhase.GENERATING -> 2
        JobPhase.DOWNLOADING -> 3
        JobPhase.DONE -> 4
        else -> 0
    }
    return when {
        index > step -> 2
        index == step -> if (job.phase == JobPhase.DONE) 2 else 1
        else -> 0
    }
}
