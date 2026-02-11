package com.example.myapplication3.data

data class FaceExpression(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val smileProbability: Float = 0f,
    val leftEyeOpenProbability: Float = 0f,
    val rightEyeOpenProbability: Float = 0f,
    val emotion: Emotion = Emotion.NEUTRAL
) {
    fun getEmotionText(): String {
        return when (emotion) {
            Emotion.HAPPY -> "开心"
            Emotion.SAD -> "难过"
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
}

enum class Emotion {
    HAPPY, SAD, ANGRY, SURPRISED, FEARFUL, DISGUSTED, NEUTRAL
}

data class MentalState(
    val fatigueLevel: Int = 5, // 0-10
    val focusLevel: Int = 5, // 0-10
    val stressLevel: Int = 5, // 0-10
    val overallState: OverallState = OverallState.FAIR,
    val recommendations: List<String> = emptyList()
)

enum class OverallState {
    EXCELLENT, GOOD, FAIR, POOR
}