package com.example.myapplication3.data

data class FaceExpression(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val smileProbability: Float = 0f,
    val leftEyeOpenProbability: Float = 1f,
    val rightEyeOpenProbability: Float = 1f,
    val emotion: Emotion = Emotion.NEUTRAL,
    val eyeOpenness: Float = 1f,
    val blinkCount: Int = 0,
    val sessionDuration: Long = 0
) {
    fun getEmotionText(): String {
        return when (emotion) {
            Emotion.HAPPY -> "开心"
            Emotion.SAD -> "难过/疲劳"
            Emotion.ANGRY -> "生气"
            Emotion.SURPRISED -> "惊讶"
            Emotion.FEARFUL -> "害怕"
            Emotion.DISGUSTED -> "厌恶"
            Emotion.NEUTRAL -> "平静"
        }
    }

    fun getEmoji(): String {
        return when (emotion) {
            Emotion.HAPPY -> "😊"
            Emotion.SAD -> "😔"
            Emotion.ANGRY -> "😠"
            Emotion.SURPRISED -> "😮"
            Emotion.FEARFUL -> "😨"
            Emotion.DISGUSTED -> "🤢"
            Emotion.NEUTRAL -> "😐"
        }
    }

    fun getFatigueLevel(): Int {
        return when {
            eyeOpenness < 0.3f -> 8
            eyeOpenness < 0.5f -> 6
            eyeOpenness < 0.7f -> 4
            else -> 2
        }
    }

    fun getFocusLevel(): Int {
        return when {
            emotion == Emotion.HAPPY && smileProbability > 0.7f -> 8
            emotion == Emotion.NEUTRAL -> 7
            emotion == Emotion.SAD -> 4
            else -> 5
        }
    }

    fun getStressLevel(): Int {
        return when {
            emotion == Emotion.SAD -> 7
            emotion == Emotion.NEUTRAL -> 5
            emotion == Emotion.HAPPY -> 2
            else -> 5
        }
    }
}

enum class Emotion {
    HAPPY, SAD, ANGRY, SURPRISED, FEARFUL, DISGUSTED, NEUTRAL
}

data class MentalState(
    val fatigueLevel: Int = 5,
    val focusLevel: Int = 5,
    val stressLevel: Int = 5,
    val overallState: OverallState = OverallState.FAIR,
    val recommendations: List<String> = emptyList(),
    val blinkCount: Int = 0,
    val sessionDuration: Long = 0,
    val fatigueAlerts: Int = 0,
    val aiSuggestion: String? = null
)

enum class OverallState {
    EXCELLENT, GOOD, FAIR, POOR
}
