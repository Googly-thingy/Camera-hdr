package com.example.data.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GestureSignature(
    val radialDistances: FloatArray, // 36 normalized radial rays relative to palm radius
    val fingerCount: Int,
    val aspectRatio: Float,
    val solidity: Float,
    val palmRadiusRatio: Float,
    val peakAngles: List<Double>
) {
    /**
     * Computes similarity score between 0.0 (completely different) and 1.0 (identical match).
     */
    fun similarityWith(other: GestureSignature): Float {
        // 1. Radial signature correlation / cosine similarity (weight: 0.50)
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val len = minOf(radialDistances.size, other.radialDistances.size)
        for (i in 0 until len) {
            val a = radialDistances[i]
            val b = other.radialDistances[i]
            dot += a * b
            normA += a * a
            normB += b * b
        }
        val radialCosine = if (normA > 0f && normB > 0f) {
            (dot / (sqrt(normA) * sqrt(normB))).coerceIn(0f, 1f)
        } else 0f

        // 2. Finger count match (weight: 0.25)
        val fingerDiff = abs(this.fingerCount - other.fingerCount)
        val fingerScore = when (fingerDiff) {
            0 -> 1.0f
            1 -> 0.40f
            else -> 0.0f
        }

        // 3. Aspect ratio similarity (weight: 0.15)
        val aspectDiff = abs(this.aspectRatio - other.aspectRatio)
        val aspectScore = (1.0f - (aspectDiff / 1.5f)).coerceIn(0f, 1f)

        // 4. Solidity similarity (weight: 0.10)
        val solidityDiff = abs(this.solidity - other.solidity)
        val solidityScore = (1.0f - (solidityDiff / 0.5f)).coerceIn(0f, 1f)

        return (0.50f * radialCosine + 0.25f * fingerScore + 0.15f * aspectScore + 0.10f * solidityScore).coerceIn(0f, 1f)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GestureSignature
        return radialDistances.contentEquals(other.radialDistances) &&
                fingerCount == other.fingerCount &&
                aspectRatio == other.aspectRatio &&
                solidity == other.solidity &&
                palmRadiusRatio == other.palmRadiusRatio &&
                peakAngles == other.peakAngles
    }

    override fun hashCode(): Int {
        var result = radialDistances.contentHashCode()
        result = 31 * result + fingerCount
        result = 31 * result + aspectRatio.hashCode()
        result = 31 * result + solidity.hashCode()
        result = 31 * result + palmRadiusRatio.hashCode()
        result = 31 * result + peakAngles.hashCode()
        return result
    }
}
