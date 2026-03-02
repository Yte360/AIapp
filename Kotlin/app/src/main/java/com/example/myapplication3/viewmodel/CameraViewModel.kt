package com.example.myapplication3.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication3.data.Emotion
import com.example.myapplication3.data.FaceExpression
import com.example.myapplication3.data.HealthRepository
import com.example.myapplication3.data.MentalState
import com.example.myapplication3.data.OverallState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.util.*

data class CameraState(
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val isCameraReady: Boolean = false,
    val hasFaceDetected: Boolean = false,
    val showFatigueAlert: Boolean = false,
    val fatigueAlerts: Int = 0,
    val lastFatigueAlertTime: Long = 0,
    val isPaused: Boolean = false,
    val isRequestingAiAnalysis: Boolean = false
)

class CameraViewModel : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _expressions = MutableStateFlow<List<FaceExpression>>(emptyList())
    val expressions: StateFlow<List<FaceExpression>> = _expressions.asStateFlow()

    private val _mentalState = MutableStateFlow(
        MentalState(
            fatigueLevel = 5,
            focusLevel = 5,
            stressLevel = 5,
            overallState = OverallState.FAIR,
            recommendations = listOf("请正对摄像头开始检测")
        )
    )
    val mentalState: StateFlow<MentalState> = _mentalState.asStateFlow()

    private val healthRepository = HealthRepository()

    private var statusSaveJob: kotlinx.coroutines.Job? = null
    private var lastSaveTime: Long = 0L

    private val recentBuffer = mutableListOf<FaceExpression>()

    // 自定义 Float 闭合区间的 random() 扩展函数
    fun ClosedFloatingPointRange<Float>.random(): Float {
        // 核心逻辑：生成 [0.0, 1.0) 之间的随机 Float，映射到当前区间内
        return Random.nextFloat() * (endInclusive - start) + start
    }

    fun analyzeFrame() {
        if (_state.value.isAnalyzing) return

        _state.update { it.copy(isAnalyzing = true, hasFaceDetected = true) }

        viewModelScope.launch {
            try {
                delay(1000) // 模拟处理时间

                // 生成模拟数据
                val smileProb = (0.1f..0.9f).random()
                val leftEyeOpenProb = (0.3f..0.9f).random()
                val rightEyeOpenProb = (0.3f..0.9f).random()

                val emotion = when {
                    smileProb > 0.7 -> Emotion.HAPPY
                    smileProb < 0.3 -> Emotion.SAD
                    else -> Emotion.NEUTRAL
                }

                val expression = FaceExpression(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    smileProbability = smileProb,
                    leftEyeOpenProbability = leftEyeOpenProb,
                    rightEyeOpenProbability = rightEyeOpenProb,
                    emotion = emotion
                )

                // 添加到列表
                val newList = _expressions.value + expression
                _expressions.value = newList

                // 更新心理状态
                val newMentalState = analyzeMentalState(newList)
                _mentalState.value = newMentalState

            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update {
                    it.copy(
                        isAnalyzing = false,
                        hasFaceDetected = _expressions.value.isNotEmpty()
                    )
                }
            }
        }
    }

    fun onFaceDetected(expression: FaceExpression) {
        if (_state.value.isPaused || _state.value.showFatigueAlert) {
            return
        }

        _state.update { it.copy(hasFaceDetected = true) }

        viewModelScope.launch {
            try {
                val newList = _expressions.value + expression
                _expressions.value = newList

                recentBuffer.add(expression)

                val newMentalState = analyzeMentalState(newList)
                _mentalState.value = newMentalState
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onNoFaceDetected() {
        _state.update { it.copy(hasFaceDetected = false) }
    }

    private fun analyzeMentalState(expressions: List<FaceExpression>): MentalState {
        if (expressions.isEmpty()) {
            return MentalState(
                recommendations = listOf("请正对摄像头开始检测")
            )
        }

        val recentExpressions = expressions.takeLast(10)
        val avgSmile = recentExpressions.map { it.smileProbability }.average()

        // 计算疲劳程度（基于闭眼频率）
        var blinkCount = 0
        for (i in 1 until recentExpressions.size) {
            val prev = recentExpressions[i-1]
            val curr = recentExpressions[i]
            if (prev.leftEyeOpenProbability > 0.5f && curr.leftEyeOpenProbability < 0.3f) blinkCount++
            if (prev.rightEyeOpenProbability > 0.5f && curr.rightEyeOpenProbability < 0.3f) blinkCount++
        }

        val blinkRate = if (recentExpressions.size > 1) blinkCount.toDouble() / (recentExpressions.size - 1) else 0.0

        val fatigueLevel = when {
            blinkRate > 0.4 -> 8
            blinkRate > 0.2 -> 6
            else -> 4
        }.coerceIn(1, 10)

        val stressLevel = when {
            avgSmile < 0.3 -> 8
            avgSmile < 0.5 -> 6
            avgSmile < 0.7 -> 4
            else -> 2
        }.coerceIn(1, 10)

        val focusLevel = when {
            recentExpressions.count { it.emotion == Emotion.NEUTRAL } > 5 -> 8
            recentExpressions.count { it.emotion == Emotion.NEUTRAL } > 3 -> 6
            else -> 4
        }.coerceIn(1, 10)

        val overallState = when {
            avgSmile > 0.6 && fatigueLevel < 4 -> OverallState.EXCELLENT
            avgSmile > 0.4 && fatigueLevel < 6 -> OverallState.GOOD
            avgSmile > 0.2 -> OverallState.FAIR
            else -> OverallState.POOR
        }

        val recommendations = mutableListOf<String>()

        if (avgSmile < 0.3) {
            recommendations.add("😊 尝试微笑一下，可以缓解压力")
            recommendations.add("🎵 听一些轻松的音乐")
        }

        if (fatigueLevel > 6) {
            recommendations.add("💤 检测到疲劳，建议休息一下")
            recommendations.add("☕ 喝杯水，活动一下身体")
            recommendations.add("👀 做一下眼保健操")
        }

        if (stressLevel > 6) {
            recommendations.add("🧘‍♀️ 尝试深呼吸放松")
            recommendations.add("🚶‍♂️ 起来走动一下，活动身体")
        }

        // 通用学习建议
        recommendations.add("📖 保持正确的坐姿和距离")
        recommendations.add("💡 合理安排学习与休息时间")
        recommendations.add("🎯 设定明确的学习目标")

        return MentalState(
            fatigueLevel = fatigueLevel,
            focusLevel = focusLevel,
            stressLevel = stressLevel,
            overallState = overallState,
            recommendations = recommendations.distinct()
        )
    }

    fun startCamera() {
        _state.update { it.copy(isCameraReady = true) }
        startStatusSaveTimer()
    }

    private fun startStatusSaveTimer() {
        statusSaveJob?.cancel()
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSave = if (lastSaveTime > 0) currentTime - lastSaveTime else 0L
        val initialDelay = maxOf(0L, 60000L - timeSinceLastSave)
        
        statusSaveJob = viewModelScope.launch {
            var delayTime = initialDelay
            while (true) {
                delay(delayTime)
                saveAggregatedStatus()
                delayTime = 60000L
            }
        }
    }

    private fun stopStatusSaveTimer() {
        statusSaveJob?.cancel()
        statusSaveJob = null
    }

    private fun saveAggregatedStatus() {
        if (recentBuffer.isEmpty()) return

        lastSaveTime = System.currentTimeMillis()

        val avgFatigue = recentBuffer.map { calculateFatigueFromExpression(it) }.average().toInt()
        val avgFocus = recentBuffer.map { calculateFocusFromExpression(it) }.average().toInt()
        val avgStress = recentBuffer.map { calculateStressFromExpression(it) }.average().toInt()
        val dominantEmotion = recentBuffer.groupBy { it.emotion }.maxByOrNull { it.value.size }?.key?.name ?: "NEUTRAL"

        healthRepository.saveRealtimeStatus(
            userId = 1,
            fatigueLevel = avgFatigue,
            focusLevel = avgFocus,
            stressLevel = avgStress,
            currentEmotion = dominantEmotion
        ) { success ->
            if (success) {
                android.util.Log.d("CameraViewModel", "状态数据保存成功")
            } else {
                android.util.Log.d("CameraViewModel", "状态数据保存失败")
            }
        }

        recentBuffer.clear()
    }

    private fun calculateFatigueFromExpression(expression: FaceExpression): Int {
        val avgEyeOpen = (expression.leftEyeOpenProbability + expression.rightEyeOpenProbability) / 2
        return when {
            avgEyeOpen < 0.3 -> 8
            avgEyeOpen < 0.5 -> 6
            avgEyeOpen < 0.7 -> 4
            else -> 2
        }.coerceIn(1, 10)
    }

    private fun calculateFocusFromExpression(expression: FaceExpression): Int {
        return when {
            expression.smileProbability > 0.6 && expression.leftEyeOpenProbability > 0.7 -> 9
            expression.smileProbability > 0.4 && expression.leftEyeOpenProbability > 0.6 -> 7
            expression.smileProbability > 0.3 -> 5
            else -> 3
        }.coerceIn(1, 10)
    }

    private fun calculateStressFromExpression(expression: FaceExpression): Int {
        return when {
            expression.smileProbability < 0.2 -> 8
            expression.smileProbability < 0.4 -> 6
            expression.smileProbability < 0.6 -> 4
            else -> 2
        }.coerceIn(1, 10)
    }

    fun clearData() {
        stopStatusSaveTimer()
        recentBuffer.clear()
        _expressions.value = emptyList()
        _mentalState.value = MentalState(
            fatigueLevel = 5,
            focusLevel = 5,
            stressLevel = 5,
            overallState = OverallState.FAIR,
            recommendations = listOf("请正对摄像头开始检测")
        )
        _state.update {
            it.copy(
                hasFaceDetected = false,
                showFatigueAlert = false,
                fatigueAlerts = 0,
                lastFatigueAlertTime = 0,
                isPaused = false,
                isRequestingAiAnalysis = false
            )
        }
    }

    fun onFatigueAlert() {
        if (_state.value.showFatigueAlert || _state.value.isRequestingAiAnalysis) {
            return
        }

        stopStatusSaveTimer()

        val currentTime = System.currentTimeMillis()
        val lastAlertTime = _state.value.lastFatigueAlertTime

        if (lastAlertTime > 0 && currentTime - lastAlertTime < 180000) {
            return
        }

        _state.update {
            it.copy(
                isPaused = true,
                fatigueAlerts = it.fatigueAlerts + 1,
                lastFatigueAlertTime = currentTime,
                isRequestingAiAnalysis = true
            )
        }

        _mentalState.value = _mentalState.value.copy(
            fatigueAlerts = _state.value.fatigueAlerts,
            recommendations = listOf(
                "⚠️ 检测到深度疲劳！",
                "😴 请立即休息 5-10 分钟",
                "💧 喝杯水，活动一下身体",
                "👀 做眼保健操放松眼睛"
            ) + _mentalState.value.recommendations,
            aiSuggestion = null
        )

        healthRepository.saveFatigueAlert(1) { }
        requestAiAnalysisWithCallback { aiSuggestion ->
            _mentalState.value = _mentalState.value.copy(aiSuggestion = aiSuggestion)
            _state.update {
                it.copy(
                    showFatigueAlert = true,
                    isRequestingAiAnalysis = false
                )
            }
        }
    }

    fun dismissFatigueAlert(isExit: Boolean = false) {
        if (isExit) {
            _state.update {
                it.copy(
                    showFatigueAlert = false,
                    isPaused = false,
                    lastFatigueAlertTime = 0,
                    isRequestingAiAnalysis = false
                )
            }
            recentBuffer.clear()
        } else {
            _state.update {
                it.copy(
                    showFatigueAlert = false,
                    isPaused = false,
                    isRequestingAiAnalysis = false
                )
            }
            startStatusSaveTimer()
        }
    }

    private fun requestAiAnalysis() {
        val currentState = _mentalState.value
        val recentExpressions = _expressions.value.takeLast(10)
        val emotionHistory = recentExpressions.map { it.emotion.name }

        HealthRepository().analyzeHealth(
            currentEmotion = recentExpressions.lastOrNull()?.emotion?.name ?: "NEUTRAL",
            fatigueLevel = currentState.fatigueLevel,
            focusLevel = currentState.focusLevel,
            stressLevel = currentState.stressLevel,
            sessionDuration = currentState.sessionDuration.toInt(),
            fatigueAlerts = currentState.fatigueAlerts,
            emotionHistory = emotionHistory
        ) { suggestion: String?, _ ->
            if (suggestion != null) {
                _mentalState.value = _mentalState.value.copy(aiSuggestion = suggestion)
            }
        }
    }

    private fun requestAiAnalysisWithCallback(callback: (String) -> Unit) {
        val currentState = _mentalState.value
        val recentExpressions = _expressions.value.takeLast(10)
        val emotionHistory = recentExpressions.map { it.emotion.name }

        HealthRepository().analyzeHealth(
            currentEmotion = recentExpressions.lastOrNull()?.emotion?.name ?: "NEUTRAL",
            fatigueLevel = currentState.fatigueLevel,
            focusLevel = currentState.focusLevel,
            stressLevel = currentState.stressLevel,
            sessionDuration = currentState.sessionDuration.toInt(),
            fatigueAlerts = currentState.fatigueAlerts,
            emotionHistory = emotionHistory
        ) { suggestion: String?, _ ->
            callback(suggestion ?: "您似乎已经很疲劳了，建议立即休息一下。")
        }
    }
}