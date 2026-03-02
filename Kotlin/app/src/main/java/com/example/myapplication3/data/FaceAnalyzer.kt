package com.example.myapplication3.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val context: Context,
    private val onFaceDetected: (FaceExpression) -> Unit,
    private val onNoFaceDetected: () -> Unit,
    private val onFatigueAlert: () -> Unit,
    private val onVibration: () -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector
    private var lastAnalysisTime = 0L
    private val minAnalysisInterval = 300L  // 缩短到300ms，提高采样频率

    private var lastEyeOpenTime = 0L
    private var blinkCount = 0
    private var sessionStartTime = System.currentTimeMillis()
    private var consecutiveLowEyeOpenCount = 0

    private var lastFaceExpression: FaceExpression? = null

    // 用于平滑判断的眼睛状态缓冲区
    private val eyeStateBuffer = mutableListOf<Boolean>()
    private val bufferSize = 5  // 连续5帧判断

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)  // 改为准确模式
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()

        detector = FaceDetection.getClient(options)
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < minAnalysisInterval) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        lastAnalysisTime = currentTime

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.maxByOrNull {
                        it.boundingBox.width() * it.boundingBox.height()
                    }
                    face?.let {
                        val expression = convertToFaceExpression(it)
                        lastFaceExpression = expression
                        
                        // 检测疲劳：眼睛持续闭合
                        checkFatigue(expression)
                        
                        onFaceDetected(expression)
                    }
                } else {
                    onNoFaceDetected()
                }
            }
            .addOnFailureListener {
                onNoFaceDetected()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun checkFatigue(expression: FaceExpression) {
        val currentTime = System.currentTimeMillis()
        val eyeOpenProb = (expression.leftEyeOpenProbability + expression.rightEyeOpenProbability) / 2

        // 检测眨眼
        if (lastEyeOpenTime > 0) {
            val wasEyeOpen = (lastFaceExpression?.leftEyeOpenProbability ?: 1f) > 0.5f
            val isEyeOpen = eyeOpenProb > 0.5f

            if (wasEyeOpen && !isEyeOpen) {
                blinkCount++
            }
        }
        lastEyeOpenTime = currentTime

        // 使用缓冲区平滑判断眼睛状态
        val isEyeLow = eyeOpenProb < 0.4f  // 稍微放宽阈值
        eyeStateBuffer.add(isEyeLow)
        if (eyeStateBuffer.size > bufferSize) {
            eyeStateBuffer.removeAt(0)
        }
        
        // 缓冲区中大部分帧都认为眼睛闭合，才判定为闭合
        val closedCount = eyeStateBuffer.count { it }
        val isEyeClosed = closedCount >= (bufferSize * 2 / 3)  // 60%以上为闭合

        val isSadTired = expression.emotion == Emotion.SAD

        android.util.Log.d("FaceAnalyzer", "eyeOpenProb=$eyeOpenProb, emotion=${expression.emotion}, isEyeClosed=$isEyeClosed, isSadTired=$isSadTired, buffer=$eyeStateBuffer")

        if (isEyeClosed || isSadTired) {
            consecutiveLowEyeOpenCount++
        } else {
            consecutiveLowEyeOpenCount = 0
        }

        // 连续5次检测到疲劳信号（约1.5秒）就触发
        if (consecutiveLowEyeOpenCount >= 5) {
            android.util.Log.d("FaceAnalyzer", "触发疲劳提醒!!!")
            consecutiveLowEyeOpenCount = 0
            eyeStateBuffer.clear()
            
            // 用Handler确保在主线程执行
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onFatigueAlert()
            }
        }

        lastFaceExpression = expression
    }

    private fun triggerVibration() {
        android.util.Log.d("FaceAnalyzer", "触发震动回调")
        onVibration()
    }

    private fun convertToFaceExpression(face: Face): FaceExpression {
        val smileProb: Float = face.smilingProbability?.let { if (it >= 0f) it else 0f } ?: 0f
        val leftEyeOpenProb: Float = face.leftEyeOpenProbability?.let { if (it >= 0f) it else 1f } ?: 1f
        val rightEyeOpenProb: Float = face.rightEyeOpenProbability?.let { if (it >= 0f) it else 1f } ?: 1f

        val emotion = when {
            smileProb > 0.7f -> Emotion.HAPPY
            smileProb < 0.3f -> {
                if (leftEyeOpenProb < 0.3f || rightEyeOpenProb < 0.3f) {
                    Emotion.SAD // 疲劳/难过
                } else {
                    Emotion.NEUTRAL
                }
            }
            else -> Emotion.NEUTRAL
        }

        val eyeOpenness = (leftEyeOpenProb + rightEyeOpenProb) / 2

        return FaceExpression(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            smileProbability = smileProb,
            leftEyeOpenProbability = leftEyeOpenProb,
            rightEyeOpenProbability = rightEyeOpenProb,
            emotion = emotion,
            eyeOpenness = eyeOpenness,
            blinkCount = blinkCount,
            sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000
        )
    }

    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        blinkCount = 0
        consecutiveLowEyeOpenCount = 0
        eyeStateBuffer.clear()
    }

    fun close() {
        detector.close()
    }
}
