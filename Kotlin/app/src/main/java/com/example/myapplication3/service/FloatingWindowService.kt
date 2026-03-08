package com.example.myapplication3.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication3.R
import com.example.myapplication3.data.HealthRepository
import com.example.myapplication3.ui.activity.MainTabActivity
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.Executors

class FloatingWindowService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    companion object {
        const val CHANNEL_ID = "floating_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_START = "com.example.myapplication3.START_FLOATING"
        const val ACTION_STOP = "com.example.myapplication3.STOP_FLOATING"
        const val ACTION_SHOW = "com.example.myapplication3.SHOW_FLOATING"
        const val ACTION_HIDE = "com.example.myapplication3.HIDE_FLOATING"

        private var instance: FloatingWindowService? = null

        fun start(context: Context, userId: Int = 1) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
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
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun show(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }

        fun isRunning(): Boolean = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val healthRepository = HealthRepository()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var cameraContainer: FrameLayout? = null
    private var previewView: PreviewView? = null

    private var userId: Int = 1
    private var saveJob: Job? = null
    private var monitorJob: Job? = null
    private var isPaused = false
    private var lastFatigueAlertTime = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.d("FloatingService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        when (intent?.action) {
            ACTION_START -> {
                userId = intent.getIntExtra("user_id", 1)
                startForeground(NOTIFICATION_ID, createNotification())
                showFloatingWindow()
                startMonitoring()
                Log.d("FloatingService", "Floating window started for user: $userId")
            }
            ACTION_STOP -> {
                hideFloatingWindow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d("FloatingService", "Floating window stopped")
            }
            ACTION_SHOW -> {
                if (floatingView == null) {
                    showFloatingWindow()
                }
                Log.d("FloatingService", "Floating window shown")
            }
            ACTION_HIDE -> {
                hideFloatingWindowOnly()
                Log.d("FloatingService", "Floating window hidden")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        hideFloatingWindow()
        serviceScope.cancel()
        cameraExecutor.shutdown()
        instance = null
        Log.d("FloatingService", "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗监测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "学习监测悬浮窗正在运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val stopIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainTabActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("学习监测运行中")
            .setContentText("点击查看或停止")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun showFloatingWindow() {
        if (floatingView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_window, null)

        cameraContainer = floatingView?.findViewById(R.id.camera_container)
        
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        cameraContainer?.addView(previewView)

        val closeBtn = floatingView?.findViewById<ImageButton>(R.id.btn_close)
        closeBtn?.setOnClickListener {
            stop(this)
        }

        val params = WindowManager.LayoutParams(
            280,
            400,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        setupTouchListener(floatingView!!, params)

        try {
            windowManager?.addView(floatingView, params)
            startCamera()
        } catch (e: Exception) {
            Log.e("FloatingService", "Error showing floating window", e)
        }
    }

    private fun setupTouchListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                Log.d("FloatingService", "Double tap detected, starting fullscreen")
                hideFloatingWindowOnly()
                val intent = android.content.Intent(this@FloatingWindowService, com.example.myapplication3.ui.activity.MainTabActivity::class.java)
                intent.putExtra("open_camera_fullscreen", true)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            }
        })

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveX = Math.abs(event.rawX - initialTouchX)
                    val moveY = Math.abs(event.rawY - initialTouchY)
                    if (moveX > 10 || moveY > 10) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, params)
                    }
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun hideFloatingWindow() {
        monitorJob?.cancel()
        saveJob?.cancel()
        
        try {
            previewView?.let {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                    } catch (e: Exception) {
                        Log.e("FloatingService", "Error unbinding camera", e)
                    }
                }, ContextCompat.getMainExecutor(this))
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error stopping camera", e)
        }
        
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e("FloatingService", "Error removing view", e)
            }
        }
        floatingView = null
        cameraContainer = null
        previewView = null
    }

    private fun hideFloatingWindowOnly() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e("FloatingService", "Error removing view", e)
            }
        }
        floatingView = null
        cameraContainer = null
        previewView = null
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                Log.e("FloatingService", "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView?.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview)
        } catch (e: Exception) {
            Log.e("FloatingService", "Camera binding failed: ${e.message}", e)
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(5000)
                simulateAndSave()
            }
        }

        saveJob?.cancel()
        saveJob = serviceScope.launch {
            while (isActive) {
                delay(60000)
                saveToServer()
            }
        }

        serviceScope.launch {
            delay(1000)
            simulateAndSave()
        }
    }

    private fun simulateAndSave() {
        if (isPaused) return

        val fatigueLevel = (1..9).random()
        val focusLevel = (3..9).random()
        val stressLevel = (2..8).random()
        val emotions = listOf("HAPPY", "NEUTRAL", "SAD", "ANGRY")
        val emotion = emotions.random()

        if (fatigueLevel >= 7) {
            val now = System.currentTimeMillis()
            if (lastFatigueAlertTime == 0L || now - lastFatigueAlertTime > 180000) {
                lastFatigueAlertTime = now
                isPaused = true
                requestAiAnalysisAndShowAlert()
            }
        }
    }

    private fun requestAiAnalysisAndShowAlert() {
        healthRepository.analyzeHealth(
            currentEmotion = "NEUTRAL",
            fatigueLevel = 8,
            focusLevel = 4,
            stressLevel = 6,
            sessionDuration = 30,
            fatigueAlerts = 1,
            emotionHistory = listOf("NEUTRAL", "TIRED")
        ) { suggestion, _ ->
            mainHandler.post {
                showFatigueAlert(suggestion ?: "您似乎已经很疲劳了，建议立即休息一下。\n\n长时间疲劳学习会降低效率，请照顾好自己的身体！")
            }
        }
    }

    private fun showFatigueAlert(aiSuggestion: String? = null) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasVibrator()) {
                val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Vibration error", e)
        }

        mainHandler.post {
            showFatigueDialog(aiSuggestion)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ 学习疲劳提醒")
            .setContentText("检测到疲劳，建议休息一下！")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(100, notification)

        Log.d("FloatingService", "Fatigue alert shown")
    }

    private fun showFatigueDialog(aiSuggestion: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.alert_fatigue, null)
        
        val alertParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(dialogView, alertParams)

            val btnContinue = dialogView.findViewById<TextView>(R.id.btn_continue)
            val btnExit = dialogView.findViewById<TextView>(R.id.btn_exit)
            val alertMessage = dialogView.findViewById<TextView>(R.id.alert_message)

            val message = aiSuggestion ?: "您似乎已经很疲劳了，建议立即休息一下。\n\n长时间疲劳学习会降低效率，请照顾好自己的身体！"
            alertMessage?.text = message

            btnContinue.setOnClickListener {
                isPaused = false
                try {
                    windowManager?.removeView(dialogView)
                } catch (e: Exception) {}
            }

            btnExit.setOnClickListener {
                isPaused = false
                lastFatigueAlertTime = 0
                try {
                    windowManager?.removeView(dialogView)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error showing fatigue dialog", e)
        }
    }

    private fun saveToServer() {
        val fatigueLevel = (1..9).random()
        val focusLevel = (3..9).random()
        val stressLevel = (2..8).random()
        val emotions = listOf("HAPPY", "NEUTRAL", "SAD", "ANGRY")
        val emotion = emotions.random()

        healthRepository.saveRealtimeStatus(
            userId = userId,
            fatigueLevel = fatigueLevel,
            focusLevel = focusLevel,
            stressLevel = stressLevel,
            currentEmotion = emotion
        ) { success ->
            if (success) {
                Log.d("FloatingService", "Status saved: focus=$focusLevel, fatigue=$fatigueLevel")
            }
        }
    }
}
