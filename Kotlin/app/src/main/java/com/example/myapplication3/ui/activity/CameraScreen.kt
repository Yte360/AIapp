package com.example.myapplication3.ui.activity

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication3.viewmodel.CameraViewModel
import com.example.myapplication3.data.FaceAnalyzer
import com.example.myapplication3.data.MentalState
import com.example.myapplication3.data.FaceExpression
import com.example.myapplication3.data.OverallState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    when (cameraPermissionState.status) {
        is PermissionStatus.Granted -> {
            CameraContent(onBack = onBack, context = context)
        }
        is PermissionStatus.Denied -> {
            PermissionDeniedScreen(onRequestPermission = { cameraPermissionState.launchPermissionRequest() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraContent(onBack: () -> Unit, context: Context) {
    val viewModel: CameraViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val expressions by viewModel.expressions.collectAsState()
    val mentalState by viewModel.mentalState.collectAsState()

    var showAlert by remember { mutableStateOf(false) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.showFatigueAlert) {
        if (state.showFatigueAlert) {
            showAlert = true
            // 弹窗显示时触发震动
            try {
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            } catch (e: Exception) {}
        } else {
            showAlert = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    LaunchedEffect(previewView, state.isCameraReady) {
        if (previewView != null && state.isCameraReady) {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView?.surfaceProvider)

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val faceAnalyzer = FaceAnalyzer(
                        context = context,
                        onFaceDetected = { expression -> viewModel.onFaceDetected(expression) },
                        onNoFaceDetected = { viewModel.onNoFaceDetected() },
                        onFatigueAlert = { viewModel.onFatigueAlert() },
                        onVibration = {
                            Log.d("CameraScreen", "收到震动回调")
                            try {
                                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                                    vibratorManager.defaultVibrator
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    Log.d("CameraScreen", "震动已触发")
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(500)
                                }
                            } catch (e: Exception) {
                                Log.e("CameraScreen", "震动失败: ${e.message}")
                            }
                        }
                    )

                    imageAnalysis.setAnalyzer(cameraExecutor, faceAnalyzer)
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e("CameraScreen", "相机初始化失败", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startCamera()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                TopAppBar(
                    title = { Text("表情识别分析") },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.clearData()
                            onBack()
                        }) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx -> PreviewView(ctx).apply { previewView = this } },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!state.hasFaceDetected) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Face, contentDescription = "检测面部", tint = Color.White, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("请正对摄像头", color = Color.White, fontSize = 16.sp)
                            Text("系统将自动分析您的表情", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { MentalStateCard(mentalState = mentalState, expressionCount = expressions.size) }
                    item { RealTimeExpressionCard(latestExpression = expressions.lastOrNull(), hasDetected = state.hasFaceDetected) }
                    item { RecommendationsCard(recommendations = mentalState.recommendations) }
                    if (expressions.isNotEmpty()) {
                        item { Text("最近表情记录", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(expressions.takeLast(5).reversed()) { expression -> ExpressionItem(expression = expression) }
                    }
                }
            }
        }

        if (state.showFatigueAlert) {
            Log.d("CameraScreen", "渲染弹窗: showFatigueAlert=${state.showFatigueAlert}")
            FatigueAlertDialog(
                aiSuggestion = mentalState.aiSuggestion,
                onContinue = {
                    viewModel.dismissFatigueAlert(isExit = false)
                },
                onExit = {
                    viewModel.clearData()
                    onBack()
                }
            )
        }
    }
}

@Composable
fun MentalStateCard(mentalState: MentalState, expressionCount: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("学习状态分析", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val (color, text) = when (mentalState.overallState) {
                    OverallState.EXCELLENT -> Pair(Color.Green, "优秀")
                    OverallState.GOOD -> Pair(Color(0xFF4CAF50), "良好")
                    OverallState.FAIR -> Pair(Color(0xFFFF9800), "一般")
                    OverallState.POOR -> Pair(Color(0xFFF44336), "需改善")
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(text, color = color, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IndicatorItem("疲劳度", mentalState.fatigueLevel, when { mentalState.fatigueLevel > 7 -> Color(0xFFF44336); mentalState.fatigueLevel > 5 -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) })
                IndicatorItem("专注度", mentalState.focusLevel, when { mentalState.focusLevel > 7 -> Color(0xFF4CAF50); mentalState.focusLevel > 5 -> Color(0xFFFF9800); else -> Color(0xFFF44336) })
                IndicatorItem("压力值", mentalState.stressLevel, when { mentalState.stressLevel > 7 -> Color(0xFFF44336); mentalState.stressLevel > 5 -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("已分析 $expressionCount 次表情", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IndicatorItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$value", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                Text("/10", fontSize = 12.sp, color = color.copy(alpha = 0.7f))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RealTimeExpressionCard(latestExpression: FaceExpression?, hasDetected: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("实时表情", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (hasDetected && latestExpression != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("情绪状态", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${latestExpression.getEmoji()} ${latestExpression.getEmotionText()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("微笑程度", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(latestExpression.smileProbability * 100).toInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = when { latestExpression.smileProbability > 0.7f -> Color.Green; latestExpression.smileProbability > 0.4f -> Color(0xFFFF9800); else -> Color(0xFFF44336) }, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EyeStatusItem("左眼", latestExpression.leftEyeOpenProbability > 0.5f)
                    EyeStatusItem("右眼", latestExpression.rightEyeOpenProbability > 0.5f)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Face, contentDescription = "未检测到面部", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("等待检测面部...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun EyeStatusItem(label: String, isOpen: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isOpen) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)), contentAlignment = Alignment.Center) {
            Text(if (isOpen) "👁" else "😴", fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (isOpen) "睁开" else "闭合", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RecommendationsCard(recommendations: List<String>) {
    if (recommendations.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(Icons.Default.Lightbulb, contentDescription = "建议", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("学习建议", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recommendations.forEach { text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun AISuggestionCard(suggestion: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = "AI分析", tint = Color(0xFF4CAF50))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 智能分析", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(suggestion, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
fun FatigueAlertDialog(aiSuggestion: String?, onContinue: () -> Unit, onExit: () -> Unit) {
    Dialog(onDismissRequest = { }) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = "警告", tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("检测到疲劳！", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Text(if (aiSuggestion != null && aiSuggestion.isNotBlank()) aiSuggestion else "您似乎已经很疲劳了，建议立即休息一下。\n\n长时间疲劳学习会降低效率，请照顾好自己的身体！", fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onContinue) { Text("继续学习", color = Color.Gray, fontSize = 16.sp) }
                    TextButton(onClick = onExit) { Text("退出休息", color = Color(0xFF4CAF50), fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
fun ExpressionItem(expression: FaceExpression) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(expression.getEmoji(), fontSize = 24.sp, modifier = Modifier.width(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expression.getEmotionText(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("微笑: ${(expression.smileProbability * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(expression.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PermissionDeniedScreen(onRequestPermission: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CameraAlt, contentDescription = "摄像头", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("需要摄像头权限", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("表情识别功能需要使用摄像头来检测您的面部表情", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("授予摄像头权限") }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}
