package com.example.myapplication3.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val onFaceDetected: (FaceExpression) -> Unit,
    private val onNoFaceDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector

    private var lastAnalysisTime = 0L
    private val minAnalysisInterval = 2000L

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
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

    private fun convertToFaceExpression(face: Face): FaceExpression {
        val smileProb: Float = face.smilingProbability?.let { if (it >= 0f) it else 0f } ?: 0f
        val leftEyeOpenProb: Float = face.leftEyeOpenProbability?.let { if (it >= 0f) it else 1f } ?: 1f
        val rightEyeOpenProb: Float = face.rightEyeOpenProbability?.let { if (it >= 0f) it else 1f } ?: 1f

        val emotion = when {
            smileProb > 0.7f -> Emotion.HAPPY
            smileProb < 0.3f -> {
                if (leftEyeOpenProb < 0.5f || rightEyeOpenProb < 0.5f) {
                    Emotion.SAD
                } else {
                    Emotion.NEUTRAL
                }
            }
            else -> Emotion.NEUTRAL
        }

        return FaceExpression(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            smileProbability = smileProb,
            leftEyeOpenProbability = leftEyeOpenProb,
            rightEyeOpenProbability = rightEyeOpenProb,
            emotion = emotion
        )
    }

    fun close() {
        detector.close()
    }
}
