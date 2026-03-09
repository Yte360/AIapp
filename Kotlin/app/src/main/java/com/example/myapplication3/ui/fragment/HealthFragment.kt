package com.example.myapplication3.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.example.myapplication3.service.FloatingWindowService
import com.example.myapplication3.ui.activity.CameraScreen
import com.example.myapplication3.ui.activity.HealthDashboardScreen
import com.example.myapplication3.viewmodel.CameraViewModel

private val Blue500 = Color(0xFF2196F3)
private val Blue700 = Color(0xFF1976D2)
private val Blue100 = Color(0xFFBBDEFB)
private val Blue50 = Color(0xFFE3F2FD)
private val LightBlue400 = Color(0xFF29B6F6)
private val DarkBlue = Color(0xFF0D47A1)

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
    val cameraViewModel = activity?.cameraViewModel ?: remember { CameraViewModel() }

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
            val isFloatingRunning = FloatingWindowService.isRunning()
            prefs.edit().putBoolean("open_camera_fullscreen", false).apply()
            CameraScreen(
                viewModel = cameraViewModel,
                onBack = {
                    FloatingWindowService.show(context)
                    activity?.setInCameraScreen(false)
                    currentScreen = HealthScreen.Main
                },
                clearDataOnBack = shouldOpenCamera && !isFloatingRunning
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
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val shouldOpenCamera by remember { mutableStateOf(prefs.getBoolean("open_camera_fullscreen", false)) }
    
    LaunchedEffect(shouldOpenCamera) {
        if (shouldOpenCamera) {
            prefs.edit().putBoolean("open_camera_fullscreen", false).apply()
            onNavigateToCamera()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Blue50, Color.White, Blue100.copy(alpha = 0.3f))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection()

            Text(
                text = "功能服务",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Blue700,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            ServiceCard(
                title = "实时监测",
                description = "通过摄像头实时检测表情、疲劳度和专注力",
                icon = Icons.Default.CameraAlt,
                gradientColors = listOf(Blue500, LightBlue400),
                onClick = onNavigateToCamera
            )

            ServiceCard(
                title = "状态看板",
                description = "查看学习状态趋势、黄金时段和AI分析报告",
                icon = Icons.Default.Analytics,
                gradientColors = listOf(Blue700, Blue500),
                onClick = onNavigateToDashboard
            )

            Spacer(modifier = Modifier.height(8.dp))

            UsageCard()

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "身心健康监测",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DarkBlue
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Blue500)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "实时监测",
                fontSize = 14.sp,
                color = Blue700
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(LightBlue400)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "状态看板",
                fontSize = 14.sp,
                color = Blue700
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Blue100)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI分析",
                fontSize = 14.sp,
                color = Blue700
            )
        }
    }
}

@Composable
private fun ServiceCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.linearGradient(colors = gradientColors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkBlue
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun UsageCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Blue500, LightBlue400)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "使用说明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBlue
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            UsageItem(
                step = "01",
                title = "开始监测",
                description = "点击「实时监测」启动摄像头"
            )
            
            UsageItem(
                step = "02",
                title = "表情识别",
                description = "正对摄像头，系统自动分析"
            )
            
            UsageItem(
                step = "03",
                title = "疲劳提醒",
                description = "疲劳时会收到震动提醒"
            )
            
            UsageItem(
                step = "04",
                title = "查看报告",
                description = "点击「状态看板」查看数据"
            )
        }
    }
}

@Composable
private fun UsageItem(
    step: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Blue50),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Blue700
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333)
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
        }
    }
}
