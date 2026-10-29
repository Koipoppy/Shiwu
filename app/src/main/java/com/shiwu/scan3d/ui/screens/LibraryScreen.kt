package com.shiwu.scan3d.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shiwu.scan3d.data.AssetItem
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.components.AmbientBackdrop
import com.shiwu.scan3d.ui.components.AssetCoverPlaceholder
import com.shiwu.scan3d.ui.components.EmptyState
import com.shiwu.scan3d.ui.components.LogoMark
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.Mint100
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White
import com.shiwu.scan3d.util.humanSize
import java.io.File

@Composable
fun LibraryScreen(
    vm: AppViewModel,
    state: AppState,
    bottomPadding: PaddingValues
) {
    var pendingDelete by remember { mutableStateOf<AssetItem?>(null) }

    AmbientBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoMark(size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("资产库", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(
                        "${state.assets.size} 个作品 · 仅保存在本机",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint
                    )
                }
            }

            if (state.assets.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.ViewInAr,
                        title = "还没有 3D 作品",
                        subtitle = "回到首页扫描一件东西，\n生成的结果会自动存到这里。"
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 6.dp,
                        bottom = bottomPadding.calculateBottomPadding()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.assets, key = { it.id }) { asset ->
                        LibraryCard(
                            asset = asset,
                            onOpen = { vm.openAsset(asset.id) },
                            onDelete = { pendingDelete = asset }
                        )
                    }
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("本机上的模型文件会一起删掉，不可恢复。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingDelete = null
                    vm.deleteAsset(target.id)
                }) { Text("删除", color = com.shiwu.scan3d.ui.theme.Danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = Ink)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun LibraryCard(asset: AssetItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onOpen)
        ) {
            if (asset.coverPath != null && File(asset.coverPath).exists()) {
                AsyncImage(
                    model = File(asset.coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AssetCoverPlaceholder(Modifier.fillMaxSize(), iconSize = 34.dp)
            }

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.86f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "删除",
                    tint = Ink.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    asset.version,
                    style = MaterialTheme.typography.labelSmall,
                    color = Mint700
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            asset.name,
            style = MaterialTheme.typography.titleSmall,
            color = Ink,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDate(asset.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(Mint100)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                humanSize(asset.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
        }
    }
}
