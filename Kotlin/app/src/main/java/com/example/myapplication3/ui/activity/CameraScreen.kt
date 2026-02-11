package com.example.myapplication3.ui.activity

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication3.viewmodel.CameraViewModel
import com.example.myapplication3.data.FaceExpression
import com.example.myapplication3.data.MentalState
import com.example.myapplication3.data.OverallState
import com.google.accompanist.permissions.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // 检查权限状态
    when (cameraPermissionState.status) {
        is PermissionStatus.Granted -> {
            CameraContent(onBack = onBack, context = context)
        }
        is PermissionStatus.Denied -> {
            PermissionDeniedScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraContent(
    onBack: () -> Unit,
    context: Context
) {
    val viewModel: CameraViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val expressions by viewModel.expressions.collectAsState()
    val mentalState by viewModel.mentalState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var previewView: PreviewView? by remember { mutableStateOf(null) }

    // 模拟定期分析（每3秒一次）
    LaunchedEffect(state.isCameraReady) {
        while (state.isCameraReady) {
            delay(3000)
            if (!state.isAnalyzing) {
                viewModel.analyzeFrame()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("表情识别分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新开始")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 相机预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            previewView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 分析指示器
                if (state.isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "分析中...",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // 如果没有检测到面部
                if (!state.hasFaceDetected && !state.isAnalyzing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "检测面部",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请正对摄像头",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "系统将自动分析您的表情",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 分析结果区域
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 心理状态卡片
                item {
                    MentalStateCard(
                        mentalState = mentalState,
                        expressionCount = expressions.size
                    )
                }

                // 实时表情卡片
                item {
                    RealTimeExpressionCard(
                        latestExpression = expressions.lastOrNull(),
                        hasDetected = state.hasFaceDetected
                    )
                }

                // 建议卡片
                item {
                    RecommendationsCard(recommendations = mentalState.recommendations)
                }

                // 历史记录标题
                if (expressions.isNotEmpty()) {
                    item {
                        Text(
                            text = "最近表情记录",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(expressions.takeLast(5).reversed()) { expression ->
                        ExpressionItem(expression = expression)
                    }
                }
            }
        }
    }

    // 初始化相机（简化版）
    LaunchedEffect(Unit) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()

                    previewView?.let { pv ->
                        preview.setSurfaceProvider(pv.surfaceProvider)

                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )

                        viewModel.startCamera()
                    }
                } catch (e: Exception) {
                    Log.e("CameraScreen", "相机初始化失败", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e("CameraScreen", "无法获取相机提供者", e)
        }
    }
}

@Composable
fun MentalStateCard(
    mentalState: MentalState,
    expressionCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "学习状态分析",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // 状态指示器
                val (color, text) = when (mentalState.overallState) {
                    OverallState.EXCELLENT -> Pair(Color.Green, "优秀")
                    OverallState.GOOD -> Pair(Color(0xFF4CAF50), "良好")
                    OverallState.FAIR -> Pair(Color(0xFFFF9800), "一般")
                    OverallState.POOR -> Pair(Color(0xFFF44336), "需改善")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = text, color = color, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 指标展示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IndicatorItem(
                    label = "疲劳度",
                    value = mentalState.fatigueLevel,
                    color = when {
                        mentalState.fatigueLevel > 7 -> Color(0xFFF44336)
                        mentalState.fatigueLevel > 5 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }
                )

                IndicatorItem(
                    label = "专注度",
                    value = mentalState.focusLevel,
                    color = when {
                        mentalState.focusLevel > 7 -> Color(0xFF4CAF50)
                        mentalState.focusLevel > 5 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )

                IndicatorItem(
                    label = "压力值",
                    value = mentalState.stressLevel,
                    color = when {
                        mentalState.stressLevel > 7 -> Color(0xFFF44336)
                        mentalState.stressLevel > 5 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "已分析 $expressionCount 次表情",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun IndicatorItem(
    label: String,
    value: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$value",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "/10",
                    fontSize = 12.sp,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RealTimeExpressionCard(
    latestExpression: FaceExpression?,
    hasDetected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "实时表情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (hasDetected && latestExpression != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "情绪状态",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${latestExpression.getEmoji()} ${latestExpression.getEmotionText()}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "微笑程度",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(latestExpression.smileProbability * 100).toInt()}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                latestExpression.smileProbability > 0.7 -> Color.Green
                                latestExpression.smileProbability > 0.4 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 眼睛状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EyeStatusItem(
                        label = "左眼",
                        isOpen = latestExpression.leftEyeOpenProbability > 0.5f
                    )

                    EyeStatusItem(
                        label = "右眼",
                        isOpen = latestExpression.rightEyeOpenProbability > 0.5f
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "未检测到面部",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "等待检测面部...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EyeStatusItem(
    label: String,
    isOpen: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isOpen) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isOpen) "👁️" else "😴",
                fontSize = 24.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isOpen) "睁开" else "闭合",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RecommendationsCard(recommendations: List<String>) {
    if (recommendations.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "建议",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "学习建议",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recommendations.forEach { recommendation ->
                    RecommendationItem(recommendation)
                }
            }
        }
    }
}

@Composable
fun RecommendationItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Circle,
            contentDescription = null,
            modifier = Modifier.size(8.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ExpressionItem(expression: FaceExpression) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = expression.getEmoji(),
                fontSize = 24.sp,
                modifier = Modifier.width(40.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expression.getEmotionText(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "微笑: ${(expression.smileProbability * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(expression.timestamp)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PermissionDeniedScreen(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = "摄像头",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "需要摄像头权限",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "表情识别功能需要使用摄像头来检测您的面部表情",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("授予摄像头权限")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}