package com.shiwu.scan3d

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiwu.scan3d.ui.device.DeviceScreen
import com.shiwu.scan3d.ui.device.DeviceViewModel
import com.shiwu.scan3d.ui.theme.RetroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            RetroTheme {
                val vm: DeviceViewModel = viewModel()
                DeviceScreen(vm)
            }
        }
    }
}
