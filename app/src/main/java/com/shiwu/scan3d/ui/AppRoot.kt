package com.shiwu.scan3d.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiwu.scan3d.ui.screens.CaptureScreen
import com.shiwu.scan3d.ui.screens.GalleryScreen
import com.shiwu.scan3d.ui.screens.HomeScreen
import com.shiwu.scan3d.ui.screens.LibraryScreen
import com.shiwu.scan3d.ui.screens.ProgressScreen
import com.shiwu.scan3d.ui.screens.ResultScreen
import com.shiwu.scan3d.ui.screens.ReviewScreen
import com.shiwu.scan3d.ui.screens.SettingsScreen
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.Mint50
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        val message = state.toast
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            vm.clearToast()
        }
    }

    BackHandler(enabled = state.screen != Screen.Home) { vm.back() }

    val showBar = state.screen is Screen.Home || state.screen is Screen.Library || state.screen is Screen.Settings
    val contentPadding = PaddingValues(bottom = if (showBar) 126.dp else 0.dp)

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 28 })
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "screen"
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(vm, state, contentPadding) { vm.go(it) }
                Screen.Capture -> CaptureScreen(vm, state)
                Screen.PickPhotos -> GalleryScreen(vm, state)
                Screen.Review -> ReviewScreen(vm, state)
                Screen.Progress -> ProgressScreen(vm, state)
                Screen.Library -> LibraryScreen(vm, state, contentPadding)
                Screen.Settings -> SettingsScreen(vm, state, contentPadding)
                is Screen.Result -> ResultScreen(vm, state, screen.assetId)
            }
        }

        if (showBar) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
            ShiwuBottomBar(
                current = state.screen,
                onNavigate = { vm.go(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showBar) 126.dp else 16.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun ShiwuBottomBar(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 40.dp, end = 40.dp, bottom = 14.dp),
        shape = RoundedCornerShape(28.dp),
        color = White,
        shadowElevation = 14.dp
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            NavButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Home,
                label = "首页",
                selected = current is Screen.Home
            ) { onNavigate(Screen.Home) }
            NavButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ViewInAr,
                label = "资产",
                selected = current is Screen.Library
            ) { onNavigate(Screen.Library) }
            NavButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Tune,
                label = "设置",
                selected = current is Screen.Settings
            ) { onNavigate(Screen.Settings) }
        }
    }
}

@Composable
private fun NavButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Mint50 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Mint700 else InkFaint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Mint700 else InkFaint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
