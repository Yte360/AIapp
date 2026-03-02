package com.example.myapplication3.data

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class HealthRepository {

    private val client = OkHttpClient()
    private val baseUrl = "http://192.168.43.37:5000"

    fun saveFatigueAlert(
        userId: Int,
        callback: (Boolean) -> Unit
    ) {
        val json = JSONObject().apply {
            put("user_id", userId)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$baseUrl/api/health/save-fatigue-alert")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }

    fun saveRealtimeStatus(
        userId: Int,
        fatigueLevel: Int,
        focusLevel: Int,
        stressLevel: Int,
        currentEmotion: String,
        callback: (Boolean) -> Unit
    ) {
        val json = JSONObject().apply {
            put("user_id", userId)
            put("fatigue_level", fatigueLevel)
            put("focus_level", focusLevel)
            put("stress_level", stressLevel)
            put("current_emotion", currentEmotion)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$baseUrl/api/health/save-status")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }

    fun getRealtimeStatus(
        userId: Int,
        limit: Int = 100,
        days: Int = 0,
        callback: (List<RealtimeStatus>) -> Unit
    ) {
        val url = if (days > 0) {
            "$baseUrl/api/health/realtime-status?user_id=$userId&limit=$limit&days=$days"
        } else {
            "$baseUrl/api/health/realtime-status?user_id=$userId&limit=$limit"
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        val data = json.optJSONArray("data") ?: org.json.JSONArray()
                        val statuses = mutableListOf<RealtimeStatus>()
                        for (i in 0 until data.length()) {
                            val obj = data.getJSONObject(i)
                            statuses.add(
                                RealtimeStatus(
                                    id = obj.optLong("id"),
                                    userId = obj.optInt("user_id"),
                                    timestamp = obj.optLong("timestamp"),
                                    fatigueLevel = obj.optInt("fatigue_level"),
                                    focusLevel = obj.optInt("focus_level"),
                                    stressLevel = obj.optInt("stress_level"),
                                    currentEmotion = obj.optString("current_emotion")
                                )
                            )
                        }
                        callback(statuses)
                    } else {
                        callback(emptyList())
                    }
                } catch (e: Exception) {
                    callback(emptyList())
                }
            }
        })
    }

    fun getFatigueAlertCountToday(
        userId: Int,
        callback: (Int) -> Unit
    ) {
        val request = Request.Builder()
            .url("$baseUrl/api/health/fatigue-alerts-today?user_id=$userId")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(0)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        callback(json.optInt("count", 0))
                    } else {
                        callback(0)
                    }
                } catch (e: Exception) {
                    callback(0)
                }
            }
        })
    }

    fun getWeeklyReport(
        userId: Int,
        callback: (WeeklyReport?) -> Unit
    ) {
        val request = Request.Builder()
            .url("$baseUrl/api/health/weekly-report?user_id=$userId")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        val data = json.optJSONObject("data")
                        if (data != null) {
                            val dailyData = data.optJSONArray("daily_statuses") ?: org.json.JSONArray()
                            val dailyStatuses = mutableListOf<DailyStatus>()
                            for (i in 0 until dailyData.length()) {
                                val obj = dailyData.getJSONObject(i)
                                val emotionObj = obj.optJSONObject("emotion_counts") ?: org.json.JSONObject()
                                val emotionCounts = mutableMapOf<String, Int>()
                                emotionObj.keys().forEach { key ->
                                    emotionCounts[key] = emotionObj.getInt(key)
                                }
                                dailyStatuses.add(
                                    DailyStatus(
                                        date = obj.optString("date"),
                                        focusScore = obj.optInt("focus_score"),
                                        fatigueScore = obj.optInt("fatigue_score"),
                                        stressScore = obj.optInt("stress_score"),
                                        emotionCounts = emotionCounts
                                    )
                                )
                            }

                            val report = WeeklyReport(
                                startDate = data.optString("start_date"),
                                endDate = data.optString("end_date"),
                                dailyStatuses = dailyStatuses,
                                averageFocus = data.optDouble("average_focus").toFloat(),
                                averageFatigue = data.optDouble("average_fatigue").toFloat(),
                                averageStress = data.optDouble("average_stress").toFloat(),
                                totalStudyMinutes = data.optInt("total_study_minutes"),
                                goldenHour = data.optString("golden_hour"),
                                trendAnalysis = data.optString("trend_analysis"),
                                recommendations = data.optString("recommendations")
                            )
                            callback(report)
                        } else {
                            callback(null)
                        }
                    } else {
                        callback(null)
                    }
                } catch (e: Exception) {
                    callback(null)
                }
            }
        })
    }

    fun getWeeklyTrend(
        userId: Int,
        callback: (WeeklyReport?) -> Unit
    ) {
        val request = Request.Builder()
            .url("$baseUrl/api/health/week-trend?user_id=$userId")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        val data = json.optJSONObject("data")
                        if (data != null) {
                            val dailyData = data.optJSONArray("daily_statuses") ?: org.json.JSONArray()
                            val dailyStatuses = mutableListOf<DailyStatus>()
                            for (i in 0 until dailyData.length()) {
                                val obj = dailyData.getJSONObject(i)
                                val emotionObj = obj.optJSONObject("emotion_counts") ?: org.json.JSONObject()
                                val emotionCounts = mutableMapOf<String, Int>()
                                emotionObj.keys().forEach { key ->
                                    emotionCounts[key] = emotionObj.getInt(key)
                                }
                                dailyStatuses.add(
                                    DailyStatus(
                                        date = obj.optString("date"),
                                        focusScore = obj.optInt("focus_score"),
                                        fatigueScore = obj.optInt("fatigue_score"),
                                        stressScore = obj.optInt("stress_score"),
                                        emotionCounts = emotionCounts
                                    )
                                )
                            }

                            val report = WeeklyReport(
                                startDate = data.optString("start_date"),
                                endDate = data.optString("end_date"),
                                dailyStatuses = dailyStatuses,
                                averageFocus = data.optDouble("average_focus").toFloat(),
                                averageFatigue = data.optDouble("average_fatigue").toFloat(),
                                averageStress = data.optDouble("average_stress").toFloat(),
                                totalStudyMinutes = data.optInt("total_study_minutes"),
                                goldenHour = data.optString("golden_hour"),
                                trendAnalysis = data.optString("trend_analysis"),
                                recommendations = data.optString("recommendations")
                            )
                            callback(report)
                        } else {
                            callback(null)
                        }
                    } else {
                        callback(null)
                    }
                } catch (e: Exception) {
                    callback(null)
                }
            }
        })
    }

    fun analyzeHealth(
        currentEmotion: String,
        fatigueLevel: Int,
        focusLevel: Int,
        stressLevel: Int,
        sessionDuration: Int,
        fatigueAlerts: Int,
        emotionHistory: List<String>,
        callback: (String?, Any?) -> Unit
    ) {
        val json = JSONObject().apply {
            put("current_emotion", currentEmotion)
            put("fatigue_level", fatigueLevel)
            put("focus_level", focusLevel)
            put("stress_level", stressLevel)
            put("session_duration", sessionDuration)
            put("fatigue_alerts", fatigueAlerts)
            put("emotion_history", JSONArray(emotionHistory))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$baseUrl/api/health/analyze")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        callback(json.optString("suggestion"), json.optJSONObject("current_state"))
                    } else {
                        callback(null, null)
                    }
                } catch (e: Exception) {
                    callback(null, null)
                }
            }
        })
    }
}
