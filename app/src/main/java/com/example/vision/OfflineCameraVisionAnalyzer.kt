package com.example.vision

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val TAG = "OfflineVisionAnalyzer"

/**
 * Fully bundled on-device ML Kit analyzer (Image Labeling + Object Detection +
 * Hindi/English OCR + Barcode/QR Scanning). Zero internet or cloud download needed.
 */
class OfflineCameraVisionAnalyzer {

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.55f)
                .build()
        )
    }

    private val streamObjectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )
    }

    private val stillObjectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(
            DevanagariTextRecognizerOptions.Builder().build()
        )
    }

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient()
    }

    private val isBusy = AtomicBoolean(false)
    private var lastFrameRunMillis = 0L
    private var lastOcrRunMillis = 0L
    private var cachedOcrText = ""

    @OptIn(ExperimentalGetImage::class)
    fun analyzeImageProxy(
        imageProxy: ImageProxy,
        onResult: (OfflineVisionAnalysis) -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (now - lastFrameRunMillis < 420L || !isBusy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isBusy.set(false)
            imageProxy.close()
            return
        }

        lastFrameRunMillis = now
        val rotation = imageProxy.imageInfo.rotationDegrees
        val uprightWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val uprightHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        val runOcrNow = (now - lastOcrRunMillis) >= 1300L
        if (runOcrNow) {
            lastOcrRunMillis = now
        }

        val labelTask = labeler.process(inputImage)
        val objectTask = streamObjectDetector.process(inputImage)
        val barcodeTask = barcodeScanner.process(inputImage)
        val ocrTask = if (runOcrNow) textRecognizer.process(inputImage) else null

        val allTasks = listOfNotNull(labelTask, objectTask, barcodeTask, ocrTask)
        Tasks.whenAllComplete(allTasks)
            .addOnCompleteListener {
                try {
                    val labels = if (labelTask.isSuccessful) {
                        labelTask.result.orEmpty().map { it.text to it.confidence }
                    } else emptyList()

                    val boxes = if (objectTask.isSuccessful) {
                        objectTask.result.orEmpty().mapNotNull { obj ->
                            val primaryCat = obj.labels.maxByOrNull { it.confidence }
                            val box = obj.boundingBox
                            VisionBoundingBox(
                                left = box.left.toFloat(),
                                top = box.top.toFloat(),
                                right = box.right.toFloat(),
                                bottom = box.bottom.toFloat(),
                                label = primaryCat?.text.orEmpty(),
                                confidence = primaryCat?.confidence ?: 0.7f
                            )
                        }
                    } else emptyList()

                    val barcodes = if (barcodeTask.isSuccessful) {
                        barcodeTask.result.orEmpty().mapNotNull { it.rawValue }
                    } else emptyList()

                    if (ocrTask != null && ocrTask.isSuccessful) {
                        cachedOcrText = ocrTask.result?.text.orEmpty()
                    }

                    val analysis = OfflineVisionInterpreter.interpret(
                        rawLabels = labels,
                        rawBoxes = boxes,
                        recognizedText = cachedOcrText,
                        rawBarcodes = barcodes,
                        frameWidth = uprightWidth,
                        frameHeight = uprightHeight
                    )
                    onResult(analysis)
                } catch (e: Exception) {
                    Log.w(TAG, "Offline frame analysis failed cleanly", e)
                } finally {
                    isBusy.set(false)
                    imageProxy.close()
                }
            }
    }

    suspend fun analyzeBitmapOffline(bitmap: Bitmap): OfflineVisionAnalysis = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val labelsResult = awaitTaskOrNull(labeler.process(inputImage)).orEmpty()
            .map { it.text to it.confidence }
        val objectsResult = awaitTaskOrNull(stillObjectDetector.process(inputImage)).orEmpty()
            .map { obj ->
                val primaryCat = obj.labels.maxByOrNull { it.confidence }
                val box = obj.boundingBox
                VisionBoundingBox(
                    left = box.left.toFloat(),
                    top = box.top.toFloat(),
                    right = box.right.toFloat(),
                    bottom = box.bottom.toFloat(),
                    label = primaryCat?.text.orEmpty(),
                    confidence = primaryCat?.confidence ?: 0.7f
                )
            }
        val textResult = awaitTaskOrNull(textRecognizer.process(inputImage))?.text.orEmpty()
        val barcodeResult = awaitTaskOrNull(barcodeScanner.process(inputImage)).orEmpty()
            .mapNotNull { it.rawValue }

        OfflineVisionInterpreter.interpret(
            rawLabels = labelsResult,
            rawBoxes = objectsResult,
            recognizedText = textResult,
            rawBarcodes = barcodeResult,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height
        )
    }

    private suspend fun <T> awaitTaskOrNull(task: com.google.android.gms.tasks.Task<T>): T? =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result)
            }.addOnFailureListener { err ->
                Log.w(TAG, "ML Kit offline task failed: ${err.localizedMessage}")
                if (cont.isActive) cont.resume(null)
            }
        }

    fun close() {
        runCatching { labeler.close() }
        runCatching { streamObjectDetector.close() }
        runCatching { stillObjectDetector.close() }
        runCatching { textRecognizer.close() }
        runCatching { barcodeScanner.close() }
    }
}
