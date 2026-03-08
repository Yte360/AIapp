package com.example.myapplication3.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication3.R
import com.example.myapplication3.data.Emotion
import com.example.myapplication3.data.FaceExpression
import com.example.myapplication3.data.HealthRepository
import com.example.myapplication3.ui.activity.MainTabActivity
import kotlinx.coroutines.*
import kotlin.random.Random

class MonitoringForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "monitoring_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.example.myapplication3.START_MONITORING"
        const val ACTION_STOP = "com.example.myapplication3.STOP_MONITORING"

        private var instance: MonitoringForegroundService? = null

        fun start(context: Context, userId: Int = 1) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_START
                putExtra("user_id", userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isRunning(): Boolean = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val healthRepository = HealthRepository()

    private var monitoringJob: Job? = null
    private var saveJob: Job? = null

    private val recentBuffer = mutableListOf<FaceExpression>()
    private var lastSaveTime = 0L

    private var userId: Int = 1

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Log.d("MonitoringService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                userId = intent.getIntExtra("user_id", 1)
                Log.d("MonitoringService", "onStartCommand: ACTION_START, userId=$userId")
                startForeground(NOTIFICATION_ID, createNotification())
                startMonitoring()
                Log.d("MonitoringService", "Monitoring started for user: $userId")
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d("MonitoringService", "Monitoring stopped")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
        instance = null
        Log.d("MonitoringService", "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "学习监测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "正在后台监测您的学习状态"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d("MonitoringService", "Notification channel created")
        } else {
            Log.d("MonitoringService", "Android version < O, no notification channel needed")
        }
    }

    private fun createNotification(): Notification {
        Log.d("MonitoringService", "Creating notification...")
        
        val stopIntent = Intent(this, MonitoringForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainTabActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("学习监测中")
            .setContentText("点击查看健康 dashboard")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止监测", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                simulateFaceDetection()
                delay(5000) // 每5秒模拟一次检测
            }
        }

        saveJob?.cancel()
        saveJob = serviceScope.launch {
            while (isActive) {
                delay(60000) // 每60秒保存一次数据
                saveAggregatedStatus()
            }
        }

        // 立即保存一次
        serviceScope.launch {
            delay(1000)
            saveAggregatedStatus()
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        saveJob?.cancel()
        saveJob = null
        recentBuffer.clear()
    }

    private suspend fun simulateFaceDetection() {
        val smileProb = Random.nextFloat() * 0.8f + 0.1f
        val leftEyeOpen = Random.nextFloat() * 0.6f + 0.3f
        val rightEyeOpen = Random.nextFloat() * 0.6f + 0.3f

        val emotion = when {
            smileProb > 0.7f -> Emotion.HAPPY
            smileProb < 0.3f -> Emotion.SAD
            else -> Emotion.NEUTRAL
        }

        val expression = FaceExpression(
            smileProbability = smileProb,
            leftEyeOpenProbability = leftEyeOpen,
            rightEyeOpenProbability = rightEyeOpen,
            emotion = emotion,
            eyeOpenness = (leftEyeOpen + rightEyeOpen) / 2
        )

        recentBuffer.add(expression)

        Log.d("MonitoringService", "Face detected: emotion=$emotion, smile=$smileProb")
    }

    private fun saveAggregatedStatus() {
        if (recentBuffer.isEmpty()) {
            Log.d("MonitoringService", "Buffer empty, skip saving")
            return
        }

        lastSaveTime = System.currentTimeMillis()

        val avgFatigue = recentBuffer.map { calculateFatigueFromExpression(it) }.average().toInt()
        val avgFocus = recentBuffer.map { calculateFocusFromExpression(it) }.average().toInt()
        val avgStress = recentBuffer.map { calculateStressFromExpression(it) }.average().toInt()
        val dominantEmotion = recentBuffer.groupBy { it.emotion }.maxByOrNull { it.value.size }?.key?.name ?: "NEUTRAL"

        healthRepository.saveRealtimeStatus(
            userId = userId,
            fatigueLevel = avgFatigue,
            focusLevel = avgFocus,
            stressLevel = avgStress,
            currentEmotion = dominantEmotion
        ) { success ->
            if (success) {
                Log.d("MonitoringService", "Status saved successfully: focus=$avgFocus, fatigue=$avgFatigue")
            } else {
                Log.e("MonitoringService", "Failed to save status")
            }
        }

        recentBuffer.clear()
    }

    private fun calculateFatigueFromExpression(expression: FaceExpression): Int {
        val avgEyeOpen = (expression.leftEyeOpenProbability + expression.rightEyeOpenProbability) / 2
        return when {
            avgEyeOpen < 0.3f -> 8
            avgEyeOpen < 0.5f -> 6
            avgEyeOpen < 0.7f -> 4
            else -> 2
        }.coerceIn(1, 10)
    }

    private fun calculateFocusFromExpression(expression: FaceExpression): Int {
        return when (expression.emotion) {
            Emotion.HAPPY -> 8
            Emotion.NEUTRAL -> 7
            Emotion.SAD -> 4
            else -> 5
        }.coerceIn(1, 10)
    }

    private fun calculateStressFromExpression(expression: FaceExpression): Int {
        return when (expression.emotion) {
            Emotion.SAD -> 7
            Emotion.ANGRY -> 8
            Emotion.NEUTRAL -> 5
            Emotion.HAPPY -> 2
            else -> 5
        }.coerceIn(1, 10)
    }
}
