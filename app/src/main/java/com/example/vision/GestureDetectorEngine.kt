package com.example.vision

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.data.model.GestureSignature
import com.example.data.model.GestureType
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DetectionResult(
    val gesture: GestureType?,
    val confidence: Float,
    val latencyMs: Long,
    val handDetected: Boolean,
    val palmCenter: PointF = PointF(0.5f, 0.5f),
    val palmRadius: Float = 0f,
    val fingertips: List<PointF> = emptyList(),
    val boundingBox: RectF = RectF(),
    val rawFingerCount: Int = 0,
    val fps: Float = 0f,
    val signature: GestureSignature? = null,
    val matchScore: Float = 0f,
    val isTrainedMatch: Boolean = false,
    val faceDetected: Boolean = false,
    val faceBoundingBox: RectF? = null
)

class GestureDetectorEngine(
    var sensitivityThreshold: Float = 0.65f,
    var matchThreshold: Float = 0.82f,
    var requireTrainedOnly: Boolean = false
) {
    var trainedProfiles: Map<String, List<GestureSignature>> = emptyMap()

    // Fast on-device ML Kit Face Detector for guaranteed face exclusion
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private val motionHistory = mutableListOf<Pair<Long, PointF>>()
    private var frameCount = 0
    private var currentFps = 0f
    private var fpsTimer = 0L

    @OptIn(ExperimentalGetImage::class)
    fun analyze(image: ImageProxy): DetectionResult {
        val startTime = System.currentTimeMillis()

        frameCount++
        if (startTime - fpsTimer >= 1000L) {
            currentFps = frameCount * 1000f / (startTime - fpsTimer).coerceAtLeast(1L)
            frameCount = 0
            fpsTimer = startTime
        }

        try {
            val planes = image.planes
            if (planes.size < 3) return emptyResult(startTime)

            val imgW = image.width
            val imgH = image.height

            // 1. Detect any faces using ML Kit with strict timeout
            var detectedFaces: List<Face> = emptyList()
            val mediaImage = image.image
            if (mediaImage != null) {
                try {
                    val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                    val task = faceDetector.process(inputImage)
                    detectedFaces = Tasks.await(task, 75, TimeUnit.MILLISECONDS) ?: emptyList()
                } catch (_: Exception) {
                    // Fall back to geometric exclusion if ML Kit times out
                }
            }

            // Downsample grid for ultra-low latency (<10ms)
            val gridW = 80
            val gridH = 60
            val stepX = imgW / gridW
            val stepY = imgH / gridH

            // 2. Build Face Exclusion Mask (expand face box by 25% for chin/ears/neck)
            val faceMask = BooleanArray(gridW * gridH)
            var primaryFaceBoxNorm: RectF? = null

            for (face in detectedFaces) {
                val box = face.boundingBox
                // CameraX rotation might mirror / rotate coordinates
                val rotation = image.imageInfo.rotationDegrees
                val (fLeft, fTop, fRight, fBottom) = if (rotation == 90 || rotation == 270) {
                    // Coordinates relative to rotated dimensions
                    val normL = (box.left.toFloat() / imgH).coerceIn(0f, 1f)
                    val normR = (box.right.toFloat() / imgH).coerceIn(0f, 1f)
                    val normT = (box.top.toFloat() / imgW).coerceIn(0f, 1f)
                    val normB = (box.bottom.toFloat() / imgW).coerceIn(0f, 1f)
                    listOf(normL, normT, normR, normB)
                } else {
                    val normL = (box.left.toFloat() / imgW).coerceIn(0f, 1f)
                    val normR = (box.right.toFloat() / imgW).coerceIn(0f, 1f)
                    val normT = (box.top.toFloat() / imgH).coerceIn(0f, 1f)
                    val normB = (box.bottom.toFloat() / imgH).coerceIn(0f, 1f)
                    listOf(normL, normT, normR, normB)
                }

                // Expand by 25% to cover chin, neck, and hair
                val expX = (fRight - fLeft) * 0.25f
                val expY = (fBottom - fTop) * 0.30f
                val eLeft = (fLeft - expX).coerceAtLeast(0f)
                val eRight = (fRight + expX).coerceAtMost(1f)
                val eTop = (fTop - expY).coerceAtLeast(0f)
                val eBottom = (fBottom + expY * 1.4f).coerceAtMost(1f) // neck extends down

                primaryFaceBoxNorm = RectF(eLeft, eTop, eRight, eBottom)

                val gxMin = (eLeft * gridW).toInt().coerceIn(0, gridW - 1)
                val gxMax = (eRight * gridW).toInt().coerceIn(0, gridW - 1)
                val gyMin = (eTop * gridH).toInt().coerceIn(0, gridH - 1)
                val gyMax = (eBottom * gridH).toInt().coerceIn(0, gridH - 1)

                for (gy in gyMin..gyMax) {
                    for (gx in gxMin..gxMax) {
                        faceMask[gy * gridW + gx] = true
                    }
                }
            }

            // 3. Extract skin pixels while strictly masking out any face region
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val skinGrid = BooleanArray(gridW * gridH)
            var skinPixelCount = 0
            var sumX = 0L
            var sumY = 0L
            var minX = gridW
            var maxX = 0
            var minY = gridH
            var maxY = 0

            for (gy in 0 until gridH) {
                val py = (gy * stepY).coerceAtMost(imgH - 1)
                val uvY = py / 2
                for (gx in 0 until gridW) {
                    // IF this pixel is inside the face mask, skip completely!
                    if (faceMask[gy * gridW + gx]) continue

                    val px = (gx * stepX).coerceAtMost(imgW - 1)
                    val uvX = px / 2

                    val yIdx = py * yRowStride + px
                    val uvIdx = uvY * uvRowStride + uvX * uvPixelStride

                    if (yIdx < yBuffer.limit() && uvIdx < uBuffer.limit() && uvIdx < vBuffer.limit()) {
                        val y = yBuffer.get(yIdx).toInt() and 0xFF
                        val u = uBuffer.get(uvIdx).toInt() and 0xFF
                        val v = vBuffer.get(uvIdx).toInt() and 0xFF

                        // Human skin chrominance cluster: Cb in [78..128], Cr in [134..173], Y in [50..240]
                        val isSkin = (y in 50..240) && (u in 78..128) && (v in 134..173)

                        if (isSkin) {
                            skinGrid[gy * gridW + gx] = true
                            skinPixelCount++
                            sumX += gx
                            sumY += gy
                            if (gx < minX) minX = gx
                            if (gx > maxX) maxX = gx
                            if (gy < minY) minY = gy
                            if (gy > maxY) maxY = gy
                        }
                    }
                }
            }

            // Minimum skin area check
            val minSkinThreshold = (gridW * gridH * 0.025f).toInt()
            val maxSkinThreshold = (gridW * gridH * 0.60f).toInt()

            if (skinPixelCount < minSkinThreshold || skinPixelCount > maxSkinThreshold || minX >= maxX || minY >= maxY) {
                return emptyResult(startTime, detectedFaces.isNotEmpty(), primaryFaceBoxNorm)
            }

            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            val boxArea = boxWidth * boxHeight
            val solidity = skinPixelCount.toFloat() / boxArea.coerceAtLeast(1)
            val aspectRatio = boxHeight.toFloat() / boxWidth.coerceAtLeast(1)

            // Geometric Face Fallback Check (if ML Kit did not run or was obstructed):
            // Oval shape in top 40% of screen without wrist stem
            val isTopHalf = minY < gridH * 0.25f && maxY < gridH * 0.65f
            if (isTopHalf && solidity > 0.85f && aspectRatio in 0.9f..1.4f && detectedFaces.isEmpty()) {
                // Secondary check: does it enter from bottom?
                val touchesBottom = maxY >= gridH - 3
                if (!touchesBottom) {
                    return emptyResult(startTime, true, primaryFaceBoxNorm)
                }
            }

            val centroidX = (sumX.toFloat() / skinPixelCount) / gridW
            val centroidY = (sumY.toFloat() / skinPixelCount) / gridH
            val currentCentroid = PointF(centroidX, centroidY)

            // Track Motion for Swipes
            val detectedSwipe = processMotion(startTime, currentCentroid)

            // Approximate Palm Center via Maximum Inscribed Circle
            var bestCenterX = minX + boxWidth / 2
            var bestCenterY = minY + boxHeight / 2
            var bestRadius = 0f

            val sampleStep = 2
            for (gy in minY..maxY step sampleStep) {
                for (gx in minX..maxX step sampleStep) {
                    if (!skinGrid[gy * gridW + gx]) continue

                    var minDistance = Float.MAX_VALUE
                    for (d in 1..25) {
                        val left = gx - d < 0 || !skinGrid[gy * gridW + (gx - d)]
                        val right = gx + d >= gridW || !skinGrid[gy * gridW + (gx + d)]
                        val top = gy - d < 0 || !skinGrid[(gy - d) * gridW + gx]
                        val bottom = gy + d >= gridH || !skinGrid[(gy + d) * gridW + gx]

                        if (left || right || top || bottom) {
                            minDistance = d.toFloat()
                            break
                        }
                    }

                    if (minDistance < Float.MAX_VALUE && minDistance > bestRadius) {
                        bestRadius = minDistance
                        bestCenterX = gx
                        bestCenterY = gy
                    }
                }
            }

            if (bestRadius < 2.5f) {
                bestRadius = (minOf(boxWidth, boxHeight) * 0.28f).coerceAtLeast(2.5f)
            }

            // Radial raycasting: 36 sectors (every 10 degrees)
            val numSectors = 36
            val radialDistances = FloatArray(numSectors)
            val normalizedRadialDistances = FloatArray(numSectors)
            val angleStep = (2 * PI) / numSectors

            for (i in 0 until numSectors) {
                val angle = i * angleStep
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()

                var maxDist = 0f
                for (step in 1..42) {
                    val sx = (bestCenterX + cosA * step).toInt()
                    val sy = (bestCenterY + sinA * step).toInt()

                    if (sx in 0 until gridW && sy in 0 until gridH) {
                        if (skinGrid[sy * gridW + sx]) {
                            maxDist = step.toFloat()
                        }
                    } else break
                }
                radialDistances[i] = maxDist
                normalizedRadialDistances[i] = (maxDist / bestRadius).coerceIn(0f, 4f)
            }

            // ANATOMICAL REQUIREMENT: Radial Peak-to-Valley Concavity Check
            // A human hand with extended fingers ALWAYS has deep concavities (inter-digital valleys).
            // A round blob (like an undetected face, neck, or shirt) has min/max radial ratio > 0.70.
            var minRadial = Float.MAX_VALUE
            var maxRadial = 0f
            for (d in radialDistances) {
                if (d > maxRadial) maxRadial = d
                if (d > 0 && d < minRadial) minRadial = d
            }
            val peakToValleyRatio = if (maxRadial > 0f) minRadial / maxRadial else 1.0f

            // Fingertip peak detection
            val fingerThresholdRatio = 1.32f
            val minFingerDist = bestRadius * fingerThresholdRatio
            val detectedFingertips = mutableListOf<PointF>()
            val tipAngles = mutableListOf<Double>()

            for (i in 0 until numSectors) {
                val prev = radialDistances[(i - 1 + numSectors) % numSectors]
                val curr = radialDistances[i]
                val next = radialDistances[(i + 1) % numSectors]

                if (curr > minFingerDist && curr >= prev && curr >= next) {
                    val angle = i * angleStep
                    val tipNormX = (bestCenterX + cos(angle).toFloat() * curr) / gridW
                    val tipNormY = (bestCenterY + sin(angle).toFloat() * curr) / gridH

                    val tipPoint = PointF(tipNormX.coerceIn(0f, 1f), tipNormY.coerceIn(0f, 1f))
                    val isDuplicate = detectedFingertips.any { existing ->
                        val dx = existing.x - tipPoint.x
                        val dy = existing.y - tipPoint.y
                        sqrt((dx * dx + dy * dy).toDouble()) < 0.12
                    }

                    if (!isDuplicate) {
                        detectedFingertips.add(tipPoint)
                        tipAngles.add(angle)
                    }
                }
            }

            val fingerCount = detectedFingertips.size

            // If the blob has no fingers AND peakToValleyRatio > 0.65, but is large (> 20% area), it is NOT a fist, it is an ambient blob!
            if (fingerCount == 0 && peakToValleyRatio > 0.65f && boxArea > gridW * gridH * 0.15f) {
                return emptyResult(startTime, detectedFaces.isNotEmpty(), primaryFaceBoxNorm)
            }

            val normPalmCenter = PointF(bestCenterX.toFloat() / gridW, bestCenterY.toFloat() / gridH)
            val normPalmRadius = bestRadius / gridW
            val normBoundingBox = RectF(
                minX.toFloat() / gridW,
                minY.toFloat() / gridH,
                maxX.toFloat() / gridW,
                maxY.toFloat() / gridH
            )

            // Extract Hand Signature
            val currentSignature = GestureSignature(
                radialDistances = normalizedRadialDistances,
                fingerCount = fingerCount,
                aspectRatio = aspectRatio,
                solidity = solidity,
                palmRadiusRatio = bestRadius / maxOf(boxWidth, boxHeight).toFloat(),
                peakAngles = tipAngles
            )

            // Pattern Matching: Trained Profiles vs Heuristic Classification
            var finalGesture: GestureType? = null
            var finalConfidence = 0f
            var bestMatchScore = 0f
            var isTrainedMatch = false

            if (detectedSwipe != null) {
                finalGesture = detectedSwipe
                finalConfidence = 0.92f
            } else if (trainedProfiles.isNotEmpty()) {
                var bestGestureName: String? = null
                var highestSim = 0f

                for ((gestureId, samples) in trainedProfiles) {
                    for (sample in samples) {
                        val sim = currentSignature.similarityWith(sample)
                        if (sim > highestSim) {
                            highestSim = sim
                            bestGestureName = gestureId
                        }
                    }
                }

                bestMatchScore = highestSim

                if (highestSim >= matchThreshold && bestGestureName != null) {
                    try {
                        finalGesture = GestureType.valueOf(bestGestureName)
                        finalConfidence = highestSim
                        isTrainedMatch = true
                    } catch (_: Exception) {
                    }
                } else if (!requireTrainedOnly) {
                    val (hGesture, hConf) = classifyStaticStrict(
                        fingerCount = fingerCount,
                        palmCenter = normPalmCenter,
                        fingertips = detectedFingertips,
                        angles = tipAngles,
                        box = normBoundingBox,
                        solidity = solidity
                    )
                    finalGesture = hGesture
                    finalConfidence = hConf
                }
            } else if (!requireTrainedOnly) {
                val (hGesture, hConf) = classifyStaticStrict(
                    fingerCount = fingerCount,
                    palmCenter = normPalmCenter,
                    fingertips = detectedFingertips,
                    angles = tipAngles,
                    box = normBoundingBox,
                    solidity = solidity
                )
                finalGesture = hGesture
                finalConfidence = hConf
            }

            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            return DetectionResult(
                gesture = finalGesture,
                confidence = finalConfidence,
                latencyMs = latency,
                handDetected = true,
                palmCenter = normPalmCenter,
                palmRadius = normPalmRadius,
                fingertips = detectedFingertips,
                boundingBox = normBoundingBox,
                rawFingerCount = fingerCount,
                fps = currentFps,
                signature = currentSignature,
                matchScore = bestMatchScore,
                isTrainedMatch = isTrainedMatch,
                faceDetected = detectedFaces.isNotEmpty(),
                faceBoundingBox = primaryFaceBoxNorm
            )
        } catch (_: Exception) {
            return emptyResult(startTime)
        }
    }

    private fun classifyStaticStrict(
        fingerCount: Int,
        palmCenter: PointF,
        fingertips: List<PointF>,
        angles: List<Double>,
        box: RectF,
        solidity: Float
    ): Pair<GestureType?, Float> {
        return when {
            fingerCount in 4..5 && box.width() > 0.18f && box.height() > 0.18f -> {
                GestureType.OPEN_PALM to 0.88f
            }
            fingerCount == 0 && box.width() in 0.10f..0.28f && box.height() in 0.10f..0.28f && solidity > 0.68f -> {
                GestureType.FIST to 0.85f
            }
            fingerCount == 1 -> {
                val tip = fingertips.first()
                val dy = tip.y - palmCenter.y
                val dx = tip.x - palmCenter.x
                when {
                    dy < -0.16f && abs(dx) < 0.10f -> GestureType.POINTING_UP to 0.88f
                    dy < -0.14f && abs(dx) >= 0.10f -> GestureType.THUMBS_UP to 0.85f
                    dy > 0.16f -> GestureType.THUMBS_DOWN to 0.85f
                    dx > 0.16f -> GestureType.POINTING_RIGHT to 0.85f
                    dx < -0.16f -> GestureType.POINTING_LEFT to 0.85f
                    else -> null to 0f
                }
            }
            fingerCount == 2 -> {
                val f1 = fingertips[0]
                val f2 = fingertips[1]
                val dist = sqrt(((f1.x - f2.x) * (f1.x - f2.x) + (f1.y - f2.y) * (f1.y - f2.y)).toDouble())
                if (f1.y < palmCenter.y && f2.y < palmCenter.y) {
                    if (dist > 0.24f) GestureType.ROCK_ON to 0.82f
                    else GestureType.VICTORY to 0.89f
                } else null to 0f
            }
            fingerCount == 3 -> {
                val topFingers = fingertips.count { it.y < palmCenter.y }
                if (topFingers >= 2) GestureType.OK_SIGN to 0.82f else null to 0f
            }
            else -> null to 0f
        }
    }

    private fun processMotion(now: Long, currentPos: PointF): GestureType? {
        motionHistory.add(now to currentPos)
        motionHistory.removeAll { (time, _) -> now - time > 400L }

        if (motionHistory.size < 4) return null

        val oldest = motionHistory.first()
        val newest = motionHistory.last()

        val timeDiff = (newest.first - oldest.first).toFloat()
        if (timeDiff < 120f) return null

        val deltaX = newest.second.x - oldest.second.x
        val deltaY = newest.second.y - oldest.second.y

        val horizontalVelocity = deltaX / (timeDiff / 1000f)

        return when {
            horizontalVelocity > 1.05f && abs(deltaY) < 0.20f -> {
                motionHistory.clear()
                GestureType.SWIPE_RIGHT
            }
            horizontalVelocity < -1.05f && abs(deltaY) < 0.20f -> {
                motionHistory.clear()
                GestureType.SWIPE_LEFT
            }
            else -> null
        }
    }

    private fun emptyResult(
        startTime: Long,
        faceDetected: Boolean = false,
        faceBox: RectF? = null
    ): DetectionResult {
        return DetectionResult(
            gesture = null,
            confidence = 0f,
            latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L),
            handDetected = false,
            fps = currentFps,
            faceDetected = faceDetected,
            faceBoundingBox = faceBox
        )
    }
}
