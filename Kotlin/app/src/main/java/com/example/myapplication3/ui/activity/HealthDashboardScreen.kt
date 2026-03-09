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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.text.SimpleDateFormat
import java.util.*

// Primary brand color
private val Blue500 = Color(0xFF2196F3)
private val Blue700 = Color(0xFF1976D2)
private val Blue100 = Color(0xFFBBDEFB)
private val Blue50 = Color(0xFFE3F2FD)

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
        containerColor = Blue50,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "状态看板",
                        fontWeight = FontWeight.Bold,
                        color = Blue700
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Blue700)
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Blue700)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Blue50)
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Blue700,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Blue700
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("今日概览") },
                    selectedContentColor = Blue700,
                    unselectedContentColor = Color.Gray
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("周趋势") },
                    selectedContentColor = Blue700,
                    unselectedContentColor = Color.Gray
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("分析报告") },
                    selectedContentColor = Blue700,
                    unselectedContentColor = Color.Gray
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
    val avgFocus = if (realtimeStatuses.isNotEmpty()) realtimeStatuses.map { it.focusLevel }.average().toInt() * 10 else 75
    val avgEnergy = if (realtimeStatuses.isNotEmpty()) (10 - realtimeStatuses.map { it.fatigueLevel }.average()).toInt() * 10 else 60
    val avgEmotion = if (realtimeStatuses.isNotEmpty()) {
        val happyCount = realtimeStatuses.count { it.currentEmotion == "HAPPY" }
        (happyCount * 100 / realtimeStatuses.size).coerceIn(20, 100)
    } else 80
    val avgEfficiency = if (realtimeStatuses.isNotEmpty()) realtimeStatuses.map { it.focusLevel }.average().toInt() * 10 else 70

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Blue500, Blue700)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "多维状态雷达",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue700
                )
            }
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Blue500, Blue700)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "今日学习数据",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue700
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    icon = Icons.Default.Timer,
                    label = "学习时长",
                    value = studyDuration,
                    color = Blue500
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Blue50,
                            Blue100.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFF9800), Color(0xFFFFB74D))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "黄金时段",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "今日黄金时段",
                        fontSize = 14.sp,
                        color = Blue700
                    )
                    Text(
                        text = goldenHourText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "您的专注力在此时间段最高",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF4CAF50), Color(0xFF81C784))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "专注力趋势",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue700
                )
            }
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF2196F3), Color(0xFF64B5F6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "疲劳趋势",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue700
                )
            }
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFF44336), Color(0xFFE57373))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "压力趋势",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue700
                )
            }
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Summarize,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "本周数据摘要",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "${report?.startDate ?: "-"} 至 ${report?.endDate ?: "-"}",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "平均专注",
                    value = "${report?.averageFocus?.toInt() ?: 0}%",
                    color = Color(0xFF4CAF50),
                    icon = Icons.Default.TrendingUp
                )
                SummaryItem(
                    label = "平均疲劳",
                    value = "${report?.averageFatigue?.toInt() ?: 0}%",
                    color = Color(0xFFFF9800),
                    icon = Icons.Default.TrendingDown
                )
                SummaryItem(
                    label = "平均压力",
                    value = "${report?.averageStress?.toInt() ?: 0}%",
                    color = Color(0xFFF44336),
                    icon = Icons.Default.Psychology
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Blue100.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Blue500,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "总学习时长",
                            fontSize = 14.sp,
                            color = Color(0xFF888888)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${report?.totalStudyMinutes ?: 0} 分钟",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "黄金时段",
                            fontSize = 14.sp,
                            color = Color(0xFF888888)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report?.goldenHour ?: "暂无",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF666666)
        )
    }
}

@Composable
fun AITrendAnalysisCard(report: WeeklyReport?) {
    val trendText = report?.trendAnalysis ?: "暂无数据"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF2196F3), Color(0xFF64B5F6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI分析",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "AI 趋势分析",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "基于您的学习数据智能生成",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE3F2FD),
                                Color(0xFFBBDEFB)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = trendText,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                    color = Color(0xFF444444)
                )
            }
        }
    }
}

@Composable
fun RecommendationsCard(report: WeeklyReport?) {
    val aiRecommendations = report?.recommendations
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF2196F3), Color(0xFF42A5F5))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "个性化建议",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "针对您的学习情况给出定制建议",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            val recommendations = if (aiRecommendations.isNullOrBlank()) {
                listOf(
                    "保持规律的作息时间，有助于提高学习效率",
                    "每学习45分钟后休息10分钟",
                    "注意用眼健康，适当做眼保健操",
                    "保持良好的心态，适当运动放松"
                )
            } else {
                aiRecommendations.split("\n").filter { it.isNotBlank() }
                    .map { it.trimStart { c -> c.isDigit() || c == '.' || c == '、' || c == ' ' }.trim() }
            }
            
            recommendations.forEachIndexed { index, text ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (index % 2 == 0) Color(0xFFF8F9FA) else Color.White
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when (index % 4) {
                                    0 -> Color(0xFF2196F3)
                                    1 -> Color(0xFF1976D2)
                                    2 -> Color(0xFF42A5F5)
                                    else -> Color(0xFF64B5F6)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF444444),
                        lineHeight = 20.sp
                    )
                }
                if (index < recommendations.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
