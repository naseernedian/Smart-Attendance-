package com.example.biometrics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import com.example.domain.FaceCaptureOutput
import com.example.domain.FaceMatchResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class LiveFaceAnalysisState(
    val faceCount: Int,
    val isCenteredAndClear: Boolean,
    val statusMessage: String,
    val boundingBoxNormalized: FloatArray? = null
)

class FaceRecognitionEngine(private val context: Context) {

    companion object {
        private const val ALIGNED_SIZE = 112
        private const val TFLITE_MODEL_ASSET = "mobilefacenet.tflite"
        const val TFLITE_THRESHOLD = 0.78f
        const val DESCRIPTOR_THRESHOLD = 0.86f
        const val MAX_THUMBNAIL_BYTES = 24 * 1024
    }

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.18f)
            .build()
        FaceDetection.getClient(options)
    }

    private val tfliteInterpreter: Interpreter? by lazy {
        loadOptionalTfliteModel()
    }

    val activeEngineName: String
        get() = if (tfliteInterpreter != null) {
            "MobileFaceNet-TFLite-192D"
        } else {
            "Aligned-LBP-HOG-Landmark-256D"
        }

    val activeThreshold: Float
        get() = if (tfliteInterpreter != null) TFLITE_THRESHOLD else DESCRIPTOR_THRESHOLD

    private fun loadOptionalTfliteModel(): Interpreter? {
        return try {
            val assetList = context.assets.list("") ?: emptyArray()
            if (!assetList.contains(TFLITE_MODEL_ASSET)) return null
            val fd = context.assets.openFd(TFLITE_MODEL_ASSET)
            FileInputStream(fd.fileDescriptor).use { fis ->
                val channel = fis.channel
                val modelBuffer: MappedByteBuffer = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
                val opts = Interpreter.Options().apply { setNumThreads(2) }
                Interpreter(modelBuffer, opts)
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun inspectLiveFrame(bitmap: Bitmap): LiveFaceAnalysisState = withContext(Dispatchers.Default) {
        val faces = detectFaces(bitmap)
        evaluateFaces(faces, bitmap.width, bitmap.height)
    }

    suspend fun extractEmbeddingFromLiveCapture(bitmap: Bitmap): Result<FaceCaptureOutput> =
        withContext(Dispatchers.Default) {
            runCatching {
                val faces = detectFaces(bitmap)
                val evaluation = evaluateFaces(faces, bitmap.width, bitmap.height)
                require(faces.size == 1 && evaluation.isCenteredAndClear) {
                    evaluation.statusMessage
                }
                val face = faces.first()
                val alignedChip = alignAndCropFace(bitmap, face)
                val embedding = computeEmbedding(alignedChip, face)
                val thumbnail = compressFaceThumbnail(alignedChip)

                FaceCaptureOutput(
                    embedding = embedding,
                    thumbnailBytes = thumbnail,
                    engineName = activeEngineName,
                    qualityNote = "Live frontal face verified (${thumbnail.size / 1024} KB)"
                )
            }
        }

    fun compareEmbeddings(
        referenceEmbedding: FloatArray,
        probeEmbedding: FloatArray
    ): FaceMatchResult {
        if (referenceEmbedding.size != probeEmbedding.size || referenceEmbedding.isEmpty()) {
            return FaceMatchResult(
                isMatch = false,
                similarityScore = 0f,
                thresholdUsed = activeThreshold,
                engineName = activeEngineName,
                message = "Incompatible facial reference format. Re-capture live face."
            )
        }
        val similarity = cosineSimilarity(referenceEmbedding, probeEmbedding)
        val threshold = if (referenceEmbedding.size == 192) TFLITE_THRESHOLD else DESCRIPTOR_THRESHOLD
        val matched = similarity >= threshold
        val percent = ((similarity.coerceIn(0f, 1f)) * 100).toInt()
        return FaceMatchResult(
            isMatch = matched,
            similarityScore = similarity,
            thresholdUsed = threshold,
            engineName = activeEngineName,
            message = if (matched) {
                "VERIFIED ($percent% facial match)"
            } else {
                "NOT VERIFIED — Face mismatch ($percent% < ${(threshold * 100).toInt()}% required)"
            }
        )
    }

    fun evaluateFaces(faces: List<Face>, imageWidth: Int, imageHeight: Int): LiveFaceAnalysisState {
        if (faces.isEmpty()) {
            return LiveFaceAnalysisState(
                faceCount = 0,
                isCenteredAndClear = false,
                statusMessage = "No face detected. Position face directly in front of camera."
            )
        }
        if (faces.size > 1) {
            return LiveFaceAnalysisState(
                faceCount = faces.size,
                isCenteredAndClear = false,
                statusMessage = "Multiple faces detected. Only one person should be in frame."
            )
        }
        val face = faces.first()
        val box = face.boundingBox
        val widthRatio = box.width().toFloat() / max(1, imageWidth)
        if (widthRatio < 0.16f) {
            return LiveFaceAnalysisState(
                faceCount = 1,
                isCenteredAndClear = false,
                statusMessage = "Move closer to the camera."
            )
        }
        if (abs(face.headEulerAngleY) > 18f || abs(face.headEulerAngleZ) > 15f || abs(face.headEulerAngleX) > 15f) {
            return LiveFaceAnalysisState(
                faceCount = 1,
                isCenteredAndClear = false,
                statusMessage = "Look straight ahead at the camera."
            )
        }
        val leftEyeOpen = face.leftEyeOpenProbability
        val rightEyeOpen = face.rightEyeOpenProbability
        if ((leftEyeOpen != null && leftEyeOpen < 0.35f) || (rightEyeOpen != null && rightEyeOpen < 0.35f)) {
            return LiveFaceAnalysisState(
                faceCount = 1,
                isCenteredAndClear = false,
                statusMessage = "Keep both eyes open and clearly visible."
            )
        }
        val normBox = floatArrayOf(
            box.left.toFloat() / max(1, imageWidth),
            box.top.toFloat() / max(1, imageHeight),
            box.right.toFloat() / max(1, imageWidth),
            box.bottom.toFloat() / max(1, imageHeight)
        )
        return LiveFaceAnalysisState(
            faceCount = 1,
            isCenteredAndClear = true,
            statusMessage = "Live face aligned — ready to capture",
            boundingBoxNormalized = normBox
        )
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { cont ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (cont.isActive) cont.resume(faces)
            }
            .addOnFailureListener { exc ->
                if (cont.isActive) cont.resumeWithException(exc)
            }
    }

    fun alignAndCropFace(source: Bitmap, face: Face): Bitmap {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val box = face.boundingBox

        if (leftEye != null && rightEye != null) {
            val dx = rightEye.x - leftEye.x
            val dy = rightEye.y - leftEye.y
            val eyeDist = hypot(dx, dy)
            if (eyeDist > 8f) {
                val angleDeg = (atan2(dy, dx) * 180.0 / PI).toFloat()
                val desiredEyeDist = ALIGNED_SIZE * 0.38f
                val scale = desiredEyeDist / eyeDist
                val eyeCenterX = (leftEye.x + rightEye.x) / 2f
                val eyeCenterY = (leftEye.y + rightEye.y) / 2f

                val matrix = Matrix().apply {
                    postTranslate(-eyeCenterX, -eyeCenterY)
                    postRotate(-angleDeg)
                    postScale(scale, scale)
                    postTranslate(ALIGNED_SIZE * 0.5f, ALIGNED_SIZE * 0.38f)
                }
                val output = Bitmap.createBitmap(ALIGNED_SIZE, ALIGNED_SIZE, Bitmap.Config.ARGB_8888)
                Canvas(output).drawBitmap(source, matrix, null)
                return output
            }
        }

        val clamped = clampRect(box, source.width, source.height)
        val cropped = Bitmap.createBitmap(source, clamped.left, clamped.top, clamped.width(), clamped.height())
        return Bitmap.createScaledBitmap(cropped, ALIGNED_SIZE, ALIGNED_SIZE, true)
    }

    private fun clampRect(rect: Rect, w: Int, h: Int): Rect {
        val left = rect.left.coerceIn(0, max(0, w - 2))
        val top = rect.top.coerceIn(0, max(0, h - 2))
        val right = rect.right.coerceIn(left + 1, w)
        val bottom = rect.bottom.coerceIn(top + 1, h)
        return Rect(left, top, right, bottom)
    }

    fun computeEmbedding(aligned112: Bitmap, face: Face? = null): FloatArray {
        val interpreter = tfliteInterpreter
        if (interpreter != null) {
            return runMobileFaceNet(interpreter, aligned112)
        }
        return extractAlignedLbpHogLandmarkEmbedding(aligned112, face)
    }

    private fun runMobileFaceNet(interpreter: Interpreter, bitmap: Bitmap): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(4 * ALIGNED_SIZE * ALIGNED_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(ALIGNED_SIZE * ALIGNED_SIZE)
        bitmap.getPixels(pixels, 0, ALIGNED_SIZE, 0, 0, ALIGNED_SIZE, ALIGNED_SIZE)
        for (pixel in pixels) {
            val r = (((pixel shr 16) and 0xFF) - 127.5f) / 128f
            val g = (((pixel shr 8) and 0xFF) - 127.5f) / 128f
            val b = ((pixel and 0xFF) - 127.5f) / 128f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        val outputShape = interpreter.getOutputTensor(0).shape()
        val dim = outputShape.lastOrNull() ?: 192
        val output = Array(1) { FloatArray(dim) }
        interpreter.run(inputBuffer, output)
        return l2Normalize(output[0])
    }

    /**
     * Deterministic offline 256-D aligned facial descriptor combining:
     * 1) Illumination-normalized 4x4 grid Uniform-like 8-bin LBP spatial histograms (128 dims)
     * 2) 4x4 grid 6-bin Oriented Gradient Histograms (HOG) (96 dims)
     * 3) Normalized inter-landmark facial geometry ratios (32 dims)
     */
    fun extractAlignedLbpHogLandmarkEmbedding(aligned112: Bitmap, face: Face?): FloatArray {
        val w = ALIGNED_SIZE
        val h = ALIGNED_SIZE
        val pixels = IntArray(w * h)
        aligned112.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h)
        var mean = 0f
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
            gray[i] = lum
            mean += lum
        }
        mean /= pixels.size
        var variance = 0f
        for (v in gray) {
            val d = v - mean
            variance += d * d
        }
        val std = max(1f, sqrt(variance / gray.size))
        for (i in gray.indices) {
            gray[i] = (gray[i] - mean) / std
        }

        val vector = FloatArray(256)
        val gridCols = 4
        val gridRows = 4
        val cellW = w / gridCols
        val cellH = h / gridRows

        // 1. Spatial 8-bin LBP-like neighbor sign patterns across 4x4 grid = 16 * 8 = 128 dims
        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                val cellIndex = gy * gridCols + gx
                val offset = cellIndex * 8
                val startX = max(1, gx * cellW)
                val endX = min(w - 2, (gx + 1) * cellW - 1)
                val startY = max(1, gy * cellH)
                val endY = min(h - 2, (gy + 1) * cellH - 1)

                for (y in startY..endY) {
                    for (x in startX..endX) {
                        val c = gray[y * w + x]
                        val n0 = if (gray[(y - 1) * w + (x - 1)] >= c) 1 else 0
                        val n1 = if (gray[(y - 1) * w + x] >= c) 1 else 0
                        val n2 = if (gray[(y - 1) * w + (x + 1)] >= c) 1 else 0
                        val n3 = if (gray[y * w + (x + 1)] >= c) 1 else 0
                        val n4 = if (gray[(y + 1) * w + (x + 1)] >= c) 1 else 0
                        val n5 = if (gray[(y + 1) * w + x] >= c) 1 else 0
                        val n6 = if (gray[(y + 1) * w + (x - 1)] >= c) 1 else 0
                        val n7 = if (gray[y * w + (x - 1)] >= c) 1 else 0
                        val pattern = n0 + (n1 shl 1) + (n2 shl 2) + (n3 shl 3) +
                            (n4 shl 4) + (n5 shl 5) + (n6 shl 6) + (n7 shl 7)
                        val bin = pattern / 32 // 8 coarse structural bins
                        vector[offset + bin] += 1f
                    }
                }
            }
        }

        // 2. Spatial 6-bin HOG across 4x4 grid = 16 * 6 = 96 dims (indices 128..223)
        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                val cellIndex = gy * gridCols + gx
                val offset = 128 + cellIndex * 6
                val startX = max(1, gx * cellW)
                val endX = min(w - 2, (gx + 1) * cellW - 1)
                val startY = max(1, gy * cellH)
                val endY = min(h - 2, (gy + 1) * cellH - 1)

                for (y in startY..endY) {
                    for (x in startX..endX) {
                        val dx = gray[y * w + (x + 1)] - gray[y * w + (x - 1)]
                        val dy = gray[(y + 1) * w + x] - gray[(y - 1) * w + x]
                        val mag = hypot(dx, dy)
                        var angle = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
                        if (angle < 0f) angle += 180f
                        val bin = ((angle / 180f) * 6f).toInt().coerceIn(0, 5)
                        vector[offset + bin] += mag
                    }
                }
            }
        }

        // 3. Normalized Facial Landmark Proportions (indices 224..255)
        if (face != null) {
            val le = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
            val re = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
            val ml = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
            val mr = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
            val mb = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
            if (le != null && re != null && nose != null) {
                val eyeDist = max(1f, dist(le, re))
                vector[224] = dist(le, nose) / eyeDist
                vector[225] = dist(re, nose) / eyeDist
                if (ml != null && mr != null) {
                    vector[226] = dist(ml, mr) / eyeDist
                    vector[227] = dist(nose, ml) / eyeDist
                    vector[228] = dist(nose, mr) / eyeDist
                    vector[229] = dist(le, ml) / eyeDist
                    vector[230] = dist(re, mr) / eyeDist
                }
                if (mb != null) {
                    vector[231] = dist(nose, mb) / eyeDist
                }
            }
        }

        // Normalize sub-blocks independently before global L2 normalization
        l2NormalizeSlice(vector, 0, 128, 0.55f)
        l2NormalizeSlice(vector, 128, 96, 0.35f)
        l2NormalizeSlice(vector, 224, 32, 0.10f)
        return l2Normalize(vector)
    }

    private fun dist(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

    private fun l2NormalizeSlice(arr: FloatArray, start: Int, len: Int, weight: Float) {
        var norm = 0f
        for (i in start until start + len) norm += arr[i] * arr[i]
        val denom = sqrt(norm)
        if (denom > 1e-6f) {
            for (i in start until start + len) arr[i] = (arr[i] / denom) * weight
        }
    }

    fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0f
        for (v in vec) sum += v * v
        val norm = sqrt(sum)
        if (norm < 1e-6f) return vec
        return FloatArray(vec.size) { vec[it] / norm }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-6f) 0f else (dot / denom).coerceIn(-1f, 1f)
    }

    fun compressFaceThumbnail(bitmap: Bitmap): ByteArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
        var quality = 70
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 12
        } while (bytes.size > MAX_THUMBNAIL_BYTES && quality >= 25)
        return bytes
    }
}
