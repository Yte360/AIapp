package com.example.myapplication3.data

import java.time.LocalDate
import java.time.LocalTime

data class HealthRecord(
    val id: Long = 0,
    val userId: Int = 1,
    val recordDate: LocalDate = LocalDate.now(),
    val sessionDuration: Int = 0,
    val focusMinutes: Int = 0,
    val fatigueAlerts: Int = 0,
    val avgStressLevel: Float = 5.0f,
    val emotionData: Map<String, Int> = emptyMap(),
    val goldenHour: LocalTime? = null
)

data class RealtimeStatus(
    val id: Long = 0,
    val userId: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val fatigueLevel: Int = 5,
    val focusLevel: Int = 5,
    val stressLevel: Int = 5,
    val currentEmotion: String = "NEUTRAL"
)

data class DailyStatus(
    val date: String,
    val focusScore: Int,
    val fatigueScore: Int,
    val stressScore: Int,
    val emotionCounts: Map<String, Int>
)

data class WeeklyReport(
    val startDate: String,
    val endDate: String,
    val dailyStatuses: List<DailyStatus>,
    val averageFocus: Float,
    val averageFatigue: Float,
    val averageStress: Float,
    val totalStudyMinutes: Int,
    val goldenHour: String?,
    val trendAnalysis: String,
    val recommendations: String?
)
