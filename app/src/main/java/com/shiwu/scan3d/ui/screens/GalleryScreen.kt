package com.shiwu.scan3d.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.Screen
import com.shiwu.scan3d.ui.components.AmbientBackdrop
import com.shiwu.scan3d.ui.components.EmptyState
import com.shiwu.scan3d.ui.components.ScreenHeader
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.InkSoft
import com.shiwu.scan3d.ui.theme.Mint500
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryItem(val id: Long, val uri: Uri, val name: String)

private fun mediaPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasMediaPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, mediaPermission()) == PackageManager.PERMISSION_GRANTED

private fun queryGallery(context: Context, limit: Int = 300): List<GalleryItem> {
    val items = mutableListOf<GalleryItem>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext() && items.size < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "image"
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                items += GalleryItem(id, uri, name)
            }
        }
    }
    return items
}

@Composable
fun GalleryScreen(vm: AppViewModel, state: AppState) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(mediaPermission())
    }

    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        loading = true
        items = withContext(Dispatchers.IO) { queryGallery(context) }
        loading = false
    }

    AmbientBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "从相册选择",
                onBack = { vm.go(Screen.Home) },
                trailing = {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (selected.isEmpty()) Color.Transparent else Mint700)
                            .clickable(enabled = selected.isNotEmpty()) {
                                val uris = selected.mapNotNull { id ->
                                    items.firstOrNull { it.id == id }?.uri
                                }
                                vm.importPhotos(uris)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (selected.isEmpty()) "完成" else "完成 ${selected.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected.isEmpty()) InkFaint else White
                        )
                    }
                }
            )

            Text(
                "选同一物体的不同角度，顺序会作为生成时的参考顺序",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(10.dp))

            when {
                !granted -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                icon = Icons.Rounded.PhotoLibrary,
                                title = "需要读取相册",
                                subtitle = "只读取你选择的图片，不会上传其他内容。"
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Mint700)
                                    .clickable { launcher.launch(mediaPermission()) }
                                    .padding(horizontal = 22.dp, vertical = 12.dp)
                            ) {
                                Text("允许访问相册", style = MaterialTheme.typography.labelLarge, color = White)
                            }
                        }
                    }
                }
                loading -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Mint500, strokeWidth = 2.5.dp)
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Rounded.PhotoLibrary,
                            title = "相册里没有图片",
                            subtitle = "先用相机拍几张，再回来导入。"
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            val index = selected.indexOf(item.id)
                            GalleryCell(
                                item = item,
                                order = if (index >= 0) index + 1 else 0,
                                onClick = {
                                    selected = when {
                                        index >= 0 -> selected - item.id
                                        selected.size < 12 -> selected + item.id
                                        else -> selected
                                    }
                                    if (index < 0 && selected.size >= 12) {
                                        vm.showToast("一次最多 12 张")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryCell(item: GalleryItem, order: Int, onClick: () -> Unit) {
    val selected = order > 0
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Mint700.copy(alpha = 0.22f))
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(21.dp)
                .clip(CircleShape)
                .background(if (selected) Mint700 else White.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    "$order",
                    style = MaterialTheme.typography.labelSmall,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = White.copy(alpha = 0f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
