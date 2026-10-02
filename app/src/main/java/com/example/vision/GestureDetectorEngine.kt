package com.example.vision

import android.graphics.PointF
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.example.data.model.GestureType
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.atan2
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
    val fps: Float = 0f
)

class GestureDetectorEngine(
    var sensitivityThreshold: Float = 0.65f // 0.5 (sensitive) to 0.8 (strict)
) {
    private val motionHistory = mutableListOf<Pair<Long, PointF>>()
    private var lastFrameTime = 0L
    private var frameCount = 0
    private var currentFps = 0f
    private var fpsTimer = 0L

    fun analyze(image: ImageProxy): DetectionResult {
        val startTime = System.currentTimeMillis()

        // Measure FPS
        frameCount++
        if (startTime - fpsTimer >= 1000L) {
            currentFps = frameCount * 1000f / (startTime - fpsTimer).coerceAtLeast(1L)
            frameCount = 0
            fpsTimer = startTime
        }

        try {
            val planes = image.planes
            if (planes.size < 3) {
                return emptyResult(startTime)
            }

            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val imgW = image.width
            val imgH = image.height

            // Downsample grid for ultra-low latency (<10ms)
            val gridW = 80
            val gridH = 60
            val stepX = imgW / gridW
            val stepY = imgH / gridH

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
                    val px = (gx * stepX).coerceAtMost(imgW - 1)
                    val uvX = px / 2

                    val yIdx = py * yRowStride + px
                    val uvIdx = uvY * uvRowStride + uvX * uvPixelStride

                    if (yIdx < yBuffer.limit() && uvIdx < uBuffer.limit() && uvIdx < vBuffer.limit()) {
                        val y = yBuffer.get(yIdx).toInt() and 0xFF
                        val u = uBuffer.get(uvIdx).toInt() and 0xFF // Cb
                        val v = vBuffer.get(uvIdx).toInt() and 0xFF // Cr

                        // Human skin chrominance model in YCbCr:
                        // Cb in [77..127], Cr in [133..173], Y in [45..245]
                        val isSkin = (y in 45..245) && (u in 77..130) && (v in 133..175)

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

            // Minimum skin area check (at least 2.5% of grid)
            val minSkinThreshold = (gridW * gridH * 0.025f).toInt()
            if (skinPixelCount < minSkinThreshold || minX >= maxX || minY >= maxY) {
                return emptyResult(startTime)
            }

            val centroidX = (sumX.toFloat() / skinPixelCount) / gridW
            val centroidY = (sumY.toFloat() / skinPixelCount) / gridH
            val currentCentroid = PointF(centroidX, centroidY)

            // Track Motion for Swipes
            val detectedSwipe = processMotion(startTime, currentCentroid)

            // Approximate Palm Center via Maximum Inscribed Circle
            var bestCenterX = minX + (maxX - minX) / 2
            var bestCenterY = minY + (maxY - minY) / 2
            var bestRadius = 0f

            val sampleStep = 2
            for (gy in minY..maxY step sampleStep) {
                for (gx in minX..maxX step sampleStep) {
                    if (!skinGrid[gy * gridW + gx]) continue

                    // Distance to nearest non-skin boundary
                    var minDistance = Float.MAX_VALUE
                    // Check orthogonal and diagonal directions
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

            if (bestRadius < 2f) {
                bestRadius = ((maxX - minX).coerceAtMost(maxY - minY) * 0.25f).coerceAtLeast(2f)
            }

            // Fingertip Detection via Radial Raycast from Palm Center
            val numSectors = 36
            val radialDistances = FloatArray(numSectors)
            val tipCandidatePoints = mutableListOf<PointF>()
            val tipAngles = mutableListOf<Double>()

            val angleStep = (2 * PI) / numSectors
            for (i in 0 until numSectors) {
                val angle = i * angleStep
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()

                var maxDist = 0f
                var lastSkinX = bestCenterX.toFloat()
                var lastSkinY = bestCenterY.toFloat()

                // Raycast outwards
                for (step in 1..40) {
                    val sampleX = (bestCenterX + cosA * step).toInt()
                    val sampleY = (bestCenterY + sinA * step).toInt()

                    if (sampleX in 0 until gridW && sampleY in 0 until gridH) {
                        if (skinGrid[sampleY * gridW + sampleX]) {
                            maxDist = step.toFloat()
                            lastSkinX = sampleX.toFloat()
                            lastSkinY = sampleY.toFloat()
                        }
                    } else {
                        break
                    }
                }
                radialDistances[i] = maxDist
            }

            // Find local peaks in radial signature
            val fingerThresholdRatio = 1.35f
            val minFingerDist = bestRadius * fingerThresholdRatio

            val detectedFingertips = mutableListOf<PointF>()
            for (i in 0 until numSectors) {
                val prev = radialDistances[(i - 1 + numSectors) % numSectors]
                val curr = radialDistances[i]
                val next = radialDistances[(i + 1) % numSectors]

                if (curr > minFingerDist && curr >= prev && curr >= next) {
                    val angle = i * angleStep
                    val tipNormX = (bestCenterX + cos(angle).toFloat() * curr) / gridW
                    val tipNormY = (bestCenterY + sin(angle).toFloat() * curr) / gridH

                    val tipPoint = PointF(tipNormX.coerceIn(0f, 1f), tipNormY.coerceIn(0f, 1f))
                    // Ensure spacing between detected fingertips
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
            val normPalmCenter = PointF(bestCenterX.toFloat() / gridW, bestCenterY.toFloat() / gridH)
            val normPalmRadius = bestRadius / gridW
            val normBoundingBox = RectF(
                minX.toFloat() / gridW,
                minY.toFloat() / gridH,
                maxX.toFloat() / gridW,
                maxY.toFloat() / gridH
            )

            // Classify Gesture
            var detectedGesture: GestureType? = detectedSwipe
            var confidence = 0.85f

            if (detectedGesture == null) {
                val (gesture, conf) = classifyStaticGesture(
                    fingerCount = fingerCount,
                    palmCenter = normPalmCenter,
                    fingertips = detectedFingertips,
                    angles = tipAngles,
                    box = normBoundingBox,
                    palmRadius = normPalmRadius
                )
                detectedGesture = gesture
                confidence = conf
            }

            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            return DetectionResult(
                gesture = detectedGesture,
                confidence = confidence,
                latencyMs = latency,
                handDetected = true,
                palmCenter = normPalmCenter,
                palmRadius = normPalmRadius,
                fingertips = detectedFingertips,
                boundingBox = normBoundingBox,
                rawFingerCount = fingerCount,
                fps = currentFps
            )
        } catch (_: Exception) {
            return emptyResult(startTime)
        }
    }

    private fun classifyStaticGesture(
        fingerCount: Int,
        palmCenter: PointF,
        fingertips: List<PointF>,
        angles: List<Double>,
        box: RectF,
        palmRadius: Float
    ): Pair<GestureType?, Float> {
        val boxWidth = box.width()
        val boxHeight = box.height()
        val aspectRatio = boxHeight / (boxWidth.coerceAtLeast(0.01f))

        return when {
            // 4 or 5 fingers extended outward
            fingerCount >= 4 -> {
                GestureType.OPEN_PALM to 0.92f
            }

            // 0 fingers extended, compact hand
            fingerCount == 0 -> {
                GestureType.FIST to 0.88f
            }

            // 1 finger extended
            fingerCount == 1 -> {
                val tip = fingertips.first()
                val dy = tip.y - palmCenter.y
                val dx = tip.x - palmCenter.x

                when {
                    // Finger pointed straight up
                    dy < -0.15f && Math.abs(dx) < 0.12f -> {
                        // High vertical aspect ratio = Pointing Up
                        GestureType.POINTING_UP to 0.89f
                    }
                    // Thumb or finger pointing up with angled posture
                    dy < -0.12f && Math.abs(dx) >= 0.12f -> {
                        GestureType.THUMBS_UP to 0.86f
                    }
                    // Finger pointing downward
                    dy > 0.15f -> {
                        GestureType.THUMBS_DOWN to 0.87f
                    }
                    // Pointing right
                    dx > 0.15f -> {
                        GestureType.POINTING_RIGHT to 0.86f
                    }
                    // Pointing left
                    dx < -0.15f -> {
                        GestureType.POINTING_LEFT to 0.86f
                    }
                    else -> GestureType.POINTING_UP to 0.75f
                }
            }

            // 2 fingers extended
            fingerCount == 2 -> {
                val f1 = fingertips[0]
                val f2 = fingertips[1]
                val distBetweenTips = sqrt(((f1.x - f2.x) * (f1.x - f2.x) + (f1.y - f2.y) * (f1.y - f2.y)).toDouble())

                // If tips are near the top (y < palmCenter.y) and moderately spaced -> Victory/Peace
                if (f1.y < palmCenter.y && f2.y < palmCenter.y) {
                    if (distBetweenTips > 0.22f) {
                        // Spread very wide -> Rock On / Horns (Thumb + Pinky)
                        GestureType.ROCK_ON to 0.84f
                    } else {
                        // Index + Middle -> Victory
                        GestureType.VICTORY to 0.91f
                    }
                } else {
                    GestureType.VICTORY to 0.78f
                }
            }

            // 3 fingers extended
            fingerCount == 3 -> {
                // If 3 fingers are pointing up and thumb + index loop close -> OK sign
                val topFingers = fingertips.count { it.y < palmCenter.y }
                if (topFingers >= 2) {
                    GestureType.OK_SIGN to 0.84f
                } else {
                    GestureType.OPEN_PALM to 0.70f
                }
            }

            else -> null to 0f
        }
    }

    private fun processMotion(now: Long, currentPos: PointF): GestureType? {
        // Keep motion history for last 450ms
        motionHistory.add(now to currentPos)
        motionHistory.removeAll { (time, _) -> now - time > 450L }

        if (motionHistory.size < 4) return null

        val oldest = motionHistory.first()
        val newest = motionHistory.last()

        val timeDiff = (newest.first - oldest.first).toFloat()
        if (timeDiff < 100f) return null // Need at least 100ms of motion

        val deltaX = newest.second.x - oldest.second.x
        val deltaY = newest.second.y - oldest.second.y

        // Horizontal swipe requires significant horizontal motion and small vertical drift
        val horizontalVelocity = deltaX / (timeDiff / 1000f)

        return when {
            horizontalVelocity > 0.85f && Math.abs(deltaY) < 0.25f -> {
                motionHistory.clear()
                GestureType.SWIPE_RIGHT
            }
            horizontalVelocity < -0.85f && Math.abs(deltaY) < 0.25f -> {
                motionHistory.clear()
                GestureType.SWIPE_LEFT
            }
            else -> null
        }
    }

    private fun emptyResult(startTime: Long): DetectionResult {
        return DetectionResult(
            gesture = null,
            confidence = 0f,
            latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L),
            handDetected = false,
            fps = currentFps
        )
    }
}
