package com.example.vision

import android.graphics.PointF
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.example.data.model.GestureSignature
import com.example.data.model.GestureType
import kotlin.math.PI
import kotlin.math.abs
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
    val fps: Float = 0f,
    val signature: GestureSignature? = null,
    val matchScore: Float = 0f,
    val isTrainedMatch: Boolean = false
)

class GestureDetectorEngine(
    var sensitivityThreshold: Float = 0.65f, // 0.5 (sensitive) to 0.85 (strict)
    var matchThreshold: Float = 0.82f,      // Required match score to trained template
    var requireTrainedOnly: Boolean = false  // Only trigger if matched with user-trained sample
) {
    // User trained profiles: GestureId -> List of sample signatures
    var trainedProfiles: Map<String, List<GestureSignature>> = emptyMap()

    private val motionHistory = mutableListOf<Pair<Long, PointF>>()
    private var frameCount = 0
    private var currentFps = 0f
    private var fpsTimer = 0L

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

            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val imgW = image.width
            val imgH = image.height

            // Downsample grid for low-latency (<8ms)
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

            // Ambient skin chrominance cluster: Cb in [77..128], Cr in [133..173], Y in [50..240]
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
                        val u = uBuffer.get(uvIdx).toInt() and 0xFF
                        val v = vBuffer.get(uvIdx).toInt() and 0xFF

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

            // Minimum hand size check (at least 3.5% and at most 60% of frame)
            val totalPixels = gridW * gridH
            val minSkinThreshold = (totalPixels * 0.035f).toInt()
            val maxSkinThreshold = (totalPixels * 0.65f).toInt()

            if (skinPixelCount < minSkinThreshold || skinPixelCount > maxSkinThreshold || minX >= maxX || minY >= maxY) {
                return emptyResult(startTime)
            }

            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            val boxArea = boxWidth * boxHeight
            val solidity = skinPixelCount.toFloat() / boxArea.coerceAtLeast(1)
            val aspectRatio = boxHeight.toFloat() / boxWidth.coerceAtLeast(1)

            // Strict False-Positive / Face Filter:
            // A human face directly facing the camera has high solidity (0.85+), no finger valleys, and is positioned near center-top without wrist edge
            val isNearTopCenter = minY < gridH * 0.15f && (minX + maxX) / 2 in (gridW * 0.25f).toInt()..(gridW * 0.75f).toInt()
            if (solidity > 0.88f && isNearTopCenter && aspectRatio in 0.85f..1.35f) {
                // Reject probable face in top center
                return emptyResult(startTime)
            }

            val centroidX = (sumX.toFloat() / skinPixelCount) / gridW
            val centroidY = (sumY.toFloat() / skinPixelCount) / gridH
            val currentCentroid = PointF(centroidX, centroidY)

            // Process optical motion for swipe
            val detectedSwipe = processMotion(startTime, currentCentroid)

            // Compute Palm Center via Maximum Inscribed Circle
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
                // Match against user-trained profiles!
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

                // Check against calibrated threshold
                if (highestSim >= matchThreshold && bestGestureName != null) {
                    try {
                        finalGesture = GestureType.valueOf(bestGestureName)
                        finalConfidence = highestSim
                        isTrainedMatch = true
                    } catch (_: Exception) {
                    }
                } else if (!requireTrainedOnly) {
                    // Fallback to strict heuristic
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
                // No user profiles trained yet: use strict heuristic
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
                isTrainedMatch = isTrainedMatch
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
        // Strict classification requiring solid hand structure
        return when {
            // Open Palm requires 4-5 fingers and broad bounding box
            fingerCount in 4..5 && box.width() > 0.20f && box.height() > 0.20f -> {
                GestureType.OPEN_PALM to 0.88f
            }

            // Fist requires 0 fingers and compact aspect ratio
            fingerCount == 0 && box.width() > 0.12f && box.height() > 0.12f && solidity > 0.65f -> {
                GestureType.FIST to 0.85f
            }

            // 1 finger
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

            // 2 fingers (Victory or Rock On)
            fingerCount == 2 -> {
                val f1 = fingertips[0]
                val f2 = fingertips[1]
                val dist = sqrt(((f1.x - f2.x) * (f1.x - f2.x) + (f1.y - f2.y) * (f1.y - f2.y)).toDouble())
                if (f1.y < palmCenter.y && f2.y < palmCenter.y) {
                    if (dist > 0.24f) GestureType.ROCK_ON to 0.82f
                    else GestureType.VICTORY to 0.89f
                } else null to 0f
            }

            // 3 fingers (OK sign)
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
