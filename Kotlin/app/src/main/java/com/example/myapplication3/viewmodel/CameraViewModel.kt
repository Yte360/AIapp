package com.example.myapplication3.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication3.data.Emotion
import com.example.myapplication3.data.FaceExpression
import com.example.myapplication3.data.MentalState
import com.example.myapplication3.data.OverallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraState(
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val isCameraReady: Boolean = false,
    val hasFaceDetected: Boolean = false
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

    fun onFaceDetected(expression: FaceExpression) {
        _state.update { it.copy(hasFaceDetected = true, isAnalyzing = false) }

        viewModelScope.launch {
            try {
                val newList = _expressions.value + expression
                _expressions.value = newList

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

        var blinkCount = 0
        for (i in 1 until recentExpressions.size) {
            val prev = recentExpressions[i - 1]
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
    }

    fun clearData() {
        _expressions.value = emptyList()
        _mentalState.value = MentalState(
            fatigueLevel = 5,
            focusLevel = 5,
            stressLevel = 5,
            overallState = OverallState.FAIR,
            recommendations = listOf("请正对摄像头开始检测")
        )
        _state.update { it.copy(hasFaceDetected = false) }
    }
}
