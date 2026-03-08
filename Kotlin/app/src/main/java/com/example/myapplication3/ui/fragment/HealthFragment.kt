package com.example.myapplication3.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.example.myapplication3.service.FloatingWindowService
import com.example.myapplication3.ui.activity.CameraScreen
import com.example.myapplication3.ui.activity.HealthDashboardScreen

class HealthFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                HealthMainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }
}

sealed class HealthScreen {
    object Main : HealthScreen()
    object Camera : HealthScreen()
    object Dashboard : HealthScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMainScreen() {
    var currentScreen by remember { mutableStateOf<HealthScreen>(HealthScreen.Main) }
    val context = LocalContext.current
    val activity = context as? com.example.myapplication3.ui.activity.MainTabActivity

    when (currentScreen) {
        HealthScreen.Main -> {
            MainContent(
                onNavigateToCamera = {
                    if (FloatingWindowService.isRunning()) {
                        FloatingWindowService.hide(context)
                    }
                    activity?.setInCameraScreen(true)
                    currentScreen = HealthScreen.Camera
                },
                onNavigateToDashboard = { currentScreen = HealthScreen.Dashboard },
                context = context
            )
        }
        HealthScreen.Camera -> {
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val shouldOpenCamera = prefs.getBoolean("open_camera_fullscreen", false)
            prefs.edit().putBoolean("open_camera_fullscreen", false).apply()
            CameraScreen(
                onBack = {
                    if (FloatingWindowService.isRunning()) {
                        FloatingWindowService.show(context)
                    }
                    activity?.setInCameraScreen(false)
                    currentScreen = HealthScreen.Main
                },
                clearDataOnBack = !shouldOpenCamera
            )
        }
        HealthScreen.Dashboard -> {
            HealthDashboardScreen(
                onBack = {
                    if (FloatingWindowService.isRunning()) {
                        FloatingWindowService.show(context)
                    }
                    currentScreen = HealthScreen.Main
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    onNavigateToCamera: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    context: android.content.Context
) {
    // 检查是否需要打开 CameraScreen 全屏模式
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val shouldOpenCamera by remember { mutableStateOf(prefs.getBoolean("open_camera_fullscreen", false)) }
    
    LaunchedEffect(shouldOpenCamera) {
        if (shouldOpenCamera) {
            prefs.edit().putBoolean("open_camera_fullscreen", false).apply()
            onNavigateToCamera()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "身心健康监测",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111111)
        )

        Text(
            text = "实时监测 / 状态看板 / AI 分析",
            fontSize = 13.sp,
            color = Color(0xFF666666),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        HealthFunctionCard(
            title = "实时监测",
            description = "通过摄像头实时检测表情、疲劳度和专注力",
            icon = Icons.Default.CameraAlt,
            onClick = onNavigateToCamera
        )

        HealthFunctionCard(
            title = "状态看板",
            description = "查看学习状态趋势、黄金时段和AI分析报告",
            icon = Icons.Default.Analytics,
            onClick = onNavigateToDashboard
        )

        Spacer(modifier = Modifier.height(1.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE3F2FD)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "使用说明",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. 点击「实时监测」开始表情识别\n" +
                            "2. 正对摄像头，系统将自动分析\n" +
                            "3. 疲劳时会收到震动提醒\n" +
                            "4. 点击「状态看板」查看历史数据",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthFunctionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF19C4E9)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
