package com.example.myapplication3.ui.activity

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication3.data.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDashboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var weeklyReport by remember { mutableStateOf<WeeklyReport?>(null) }
    var weekTrend by remember { mutableStateOf<WeeklyReport?>(null) }
    var realtimeStatuses by remember { mutableStateOf<List<RealtimeStatus>>(emptyList()) }
    var fatigueAlertCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun loadData() {
        isLoading = true
        val repo = HealthRepository()
        
        repo.getWeeklyReport(1) { report ->
            weeklyReport = report
        }
        repo.getWeeklyTrend(1) { trend ->
            weekTrend = trend
        }
        repo.getRealtimeStatus(1, 100, 1) { statuses ->
            realtimeStatuses = statuses
        }
        repo.getFatigueAlertCountToday(1) { count ->
            fatigueAlertCount = count
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
        isLoading = false
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            1 -> {
                if (weekTrend == null) {
                    val repo = HealthRepository()
                    repo.getWeeklyTrend(1) { trend ->
                        weekTrend = trend
                    }
                }
            }
            2 -> {
                if (weeklyReport == null) {
                    val repo = HealthRepository()
                    repo.getWeeklyReport(1) { report ->
                        weeklyReport = report
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("考研状态看板") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("今日概览") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("周趋势") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("分析报告") }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> TodayOverviewTab(realtimeStatuses, fatigueAlertCount)
                    1 -> WeeklyTrendTab(weekTrend)
                    2 -> AnalysisReportTab(weeklyReport)
                }
            }
        }
    }
}

@Composable
fun TodayOverviewTab(realtimeStatuses: List<RealtimeStatus>, fatigueAlertCount: Int) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatusRadarCard(realtimeStatuses)
        }

        item {
            TodayStatsCard(realtimeStatuses, fatigueAlertCount)
        }

        item {
            GoldenHourCard(realtimeStatuses)
        }
    }
}

@Composable
fun StatusRadarCard(realtimeStatuses: List<RealtimeStatus>) {
    val avgFocus = if (realtimeStatuses.isNotEmpty()) realtimeStatuses.map { it.focusLevel }.average().toInt() * 10 else 0
    val avgEnergy = if (realtimeStatuses.isNotEmpty()) (10 - realtimeStatuses.map { it.fatigueLevel }.average()).toInt() * 10 else 0
    val avgEmotion = if (realtimeStatuses.isNotEmpty()) {
        val happyCount = realtimeStatuses.count { it.currentEmotion == "HAPPY" }
        (happyCount * 100 / realtimeStatuses.size).coerceIn(0, 100)
    } else 0
    val avgEfficiency = if (realtimeStatuses.isNotEmpty()) realtimeStatuses.map { it.focusLevel }.average().toInt() * 10 else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "多维状态雷达",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RadarItem(value = avgFocus, label = "专注力", color = Color(0xFF4CAF50))
                RadarItem(value = avgEnergy, label = "精力", color = Color(0xFFFF9800))
                RadarItem(value = avgEmotion, label = "情绪", color = Color(0xFF2196F3))
                RadarItem(value = avgEfficiency, label = "效率", color = Color(0xFF9C27B0))
            }
        }
    }
}

@Composable
fun RadarItem(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = value / 200f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$value%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
fun TodayStatsCard(realtimeStatuses: List<RealtimeStatus>, fatigueAlertCount: Int) {
    val studyMinutes = realtimeStatuses.size
    val hours = studyMinutes / 60
    val minutes = studyMinutes % 60
    val studyDuration = if (hours > 0) "${hours}h ${minutes}m" else if (minutes > 0) "${minutes}m" else "0m"

    val avgFocus = if (realtimeStatuses.isNotEmpty()) {
        realtimeStatuses.map { it.focusLevel }.average().toInt() * 10
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "今日学习数据",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    icon = Icons.Default.Timer,
                    label = "学习时长",
                    value = studyDuration,
                    color = Color(0xFF2196F3)
                )
                StatItem(
                    icon = Icons.Default.TrendingUp,
                    label = "专注得分",
                    value = "$avgFocus",
                    color = Color(0xFF4CAF50)
                )
                StatItem(
                    icon = Icons.Default.Warning,
                    label = "疲劳提醒",
                    value = "$fatigueAlertCount",
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GoldenHourCard(realtimeStatuses: List<RealtimeStatus>) {
    val goldenHourText = if (realtimeStatuses.isNotEmpty()) {
        val hourlyFocus = realtimeStatuses.groupBy { status ->
            val timestamp = status.timestamp * 1000
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = timestamp
            }.get(java.util.Calendar.HOUR_OF_DAY)
            hour
        }.mapValues { (_, statuses) ->
            statuses.map { it.focusLevel }.average()
        }

        if (hourlyFocus.isNotEmpty()) {
            val bestHour = hourlyFocus.maxByOrNull { it.value }?.key ?: 9
            val bestHour2 = (bestHour + 1) % 24
            when {
                bestHour in 6..11 -> "上午 ${String.format("%02d", bestHour)}:00 - ${String.format("%02d", bestHour2)}:00"
                bestHour in 12..17 -> "下午 ${String.format("%02d", bestHour - 12)}:00 - ${String.format("%02d", bestHour2 - 12)}:00"
                bestHour in 18..23 -> "晚上 ${String.format("%02d", bestHour - 12)}:00 - ${String.format("%02d", bestHour2 - 12)}:00"
                else -> "凌晨 ${String.format("%02d", bestHour)}:00 - ${String.format("%02d", bestHour2)}:00"
            }
        } else {
            "暂无数据"
        }
    } else {
        "暂无数据"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "黄金时段",
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "今日黄金时段",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = goldenHourText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "您的专注力在此时间段最高",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeeklyTrendTab(weeklyReport: WeeklyReport?) {
    if (weeklyReport == null || weeklyReport.dailyStatuses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据，请先开始学习")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            WeeklyFocusChart(weeklyReport)
        }

        item {
            WeeklyFatigueChart(weeklyReport)
        }

        item {
            WeeklyStressChart(weeklyReport)
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun WeeklyFocusChart(report: WeeklyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "专注力趋势",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val entries = report.dailyStatuses.mapIndexed { index, status ->
                entryOf(index.toFloat(), status.focusScore.toFloat())
            }
            val chartEntryModelProducer = remember { ChartEntryModelProducer(entries) }
            val dateLabels = report.dailyStatuses.map { it.date.takeLast(5) }
            val fullDateLabels = report.dailyStatuses.map { it.date }

            Chart(
                chart = lineChart(),
                chartModelProducer = chartEntryModelProducer,
                startAxis = rememberStartAxis(
                    valueFormatter = { value, _ -> "${value.toInt()}" }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        dateLabels.getOrNull(value.toInt()) ?: ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            
            Text(
                text = "日期: ${fullDateLabels.joinToString(", ")}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun WeeklyFatigueChart(report: WeeklyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "疲劳趋势",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val entries = report.dailyStatuses.mapIndexed { index, status ->
                entryOf(index.toFloat(), status.fatigueScore.toFloat())
            }
            val chartEntryModelProducer = remember { ChartEntryModelProducer(entries) }
            val dateLabels = report.dailyStatuses.map { it.date.takeLast(5) }
            val fullDateLabels = report.dailyStatuses.map { it.date }

            Chart(
                chart = lineChart(),
                chartModelProducer = chartEntryModelProducer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        dateLabels.getOrNull(value.toInt()) ?: ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            
            Text(
                text = "日期: ${fullDateLabels.joinToString(", ")}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun WeeklyStressChart(report: WeeklyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "压力趋势",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val entries = report.dailyStatuses.mapIndexed { index, status ->
                entryOf(index.toFloat(), status.stressScore.toFloat())
            }
            val chartEntryModelProducer = remember { ChartEntryModelProducer(entries) }
            val dateLabels = report.dailyStatuses.map { it.date.takeLast(5) }
            val fullDateLabels = report.dailyStatuses.map { it.date }

            Chart(
                chart = lineChart(),
                chartModelProducer = chartEntryModelProducer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        dateLabels.getOrNull(value.toInt()) ?: ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            
            Text(
                text = "日期: ${fullDateLabels.joinToString(", ")}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun AnalysisReportTab(weeklyReport: WeeklyReport?) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            WeeklySummaryCard(weeklyReport)
        }

        item {
            AITrendAnalysisCard(weeklyReport)
        }

        item {
            RecommendationsCard(weeklyReport)
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun WeeklySummaryCard(report: WeeklyReport?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "本周数据摘要",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(
                    label = "平均专注",
                    value = "${report?.averageFocus?.toInt() ?: 0}%",
                    color = Color(0xFF4CAF50)
                )
                SummaryItem(
                    label = "平均疲劳",
                    value = "${report?.averageFatigue?.toInt() ?: 0}%",
                    color = Color(0xFFFF9800)
                )
                SummaryItem(
                    label = "平均压力",
                    value = "${report?.averageStress?.toInt() ?: 0}%",
                    color = Color(0xFFF44336)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "总学习时长",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${report?.totalStudyMinutes ?: 0} 分钟",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "黄金时段",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = report?.goldenHour ?: "暂无",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AITrendAnalysisCard(report: WeeklyReport?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "AI分析",
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI 趋势分析",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = report?.trendAnalysis ?: "暂无数据",
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun RecommendationsCard(report: WeeklyReport?) {
    val aiRecommendations = report?.recommendations
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "个性化建议",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (aiRecommendations.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val defaultRecommendations = listOf(
                        "保持规律的作息时间，有助于提高学习效率",
                        "每学习45分钟后休息10分钟",
                        "注意用眼健康，适当做眼保健操",
                        "保持良好的心态，适当运动放松"
                    )
                    defaultRecommendations.forEach { text ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                val lines = aiRecommendations.split("\n").filter { it.isNotBlank() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = line.trimStart { it.isDigit() || it == '.' || it == '、' || it == ' ' }.trim(), fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
