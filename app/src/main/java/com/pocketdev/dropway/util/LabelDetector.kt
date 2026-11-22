package com.pocketdev.dropway.util

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ported from Dropway Web src/lib/labelDetect.ts
 * Real-time frame analysis to detect parcel shipping labels on video streams.
 */
object LabelDetector {

    const val ANALYSIS_MAX_SIDE = 320

    data class FrameAnalysis(
        val gray: IntArray,
        val mean: Float,
        val std: Float,
        val whiteRatio: Float,
        val edgeRatio: Float,
        val sharpness: Float
    )

    /**
     * เกณฑ์ว่าภาพเหมือนใบปะหน้า:
     * - mean > 95: สว่างพอ (กระดาษขาว)
     * - std > 20: มีคอนทราสต์ตัวหนังสือกับพื้นหลัง
     * - whiteRatio ระหว่าง 0.14 - 0.92: มีพื้นที่กระดาษขาวพอเหมาะ
     * - edgeRatio ระหว่าง 0.02 - 0.42: มีเส้นขอบตัวหนังสือ/ตาราง
     * - sharpness > 6: ภาพคมชัดพอที่จะอ่านได้
     */
    fun looksLikeLabel(a: FrameAnalysis): Boolean {
        return a.mean > 95f &&
                a.std > 20f &&
                a.whiteRatio > 0.14f &&
                a.whiteRatio < 0.92f &&
                a.edgeRatio > 0.02f &&
                a.edgeRatio < 0.42f &&
                a.sharpness > 6f
    }

    /**
     * จ่อใกล้เกินไป: กระดาษขาวกินเกือบทั้งกรอบ จนตัวอักษรริมใบหลุดออกนอกภาพ
     */
    fun looksTooClose(a: FrameAnalysis): Boolean {
        return a.mean > 168f && a.whiteRatio > 0.90f
    }

    /**
     * ความต่างเฉลี่ยของสองเฟรม (0 = นิ่งสนิท, ค่าน้อยกว่า 9 = นิ่งพอที่จะถ่ายภาพ)
     */
    fun frameDifference(a: IntArray, b: IntArray): Float {
        val len = minOf(a.size, b.size)
        if (len == 0) return 255f
        var sum = 0L
        var count = 0
        for (i in 0 until len step 3) {
            sum += abs(a[i] - b[i])
            count++
        }
        return if (count > 0) sum.toFloat() / count else 255f
    }

    /**
     * วัดคุณสมบัติเฟรมจาก Bitmap (ขนาดประมาณ 320px หรือครอปกลาง 70%)
     */
    fun analyzeBitmap(bitmap: Bitmap): FrameAnalysis? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 8 || height < 8) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        var sum = 0.0
        var whiteCount = 0

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val v = (r * 299 + g * 587 + b * 114) / 1000
            gray[i] = v
            sum += v
            if (v > 170) whiteCount++
        }

        val total = (width * height).toDouble()
        val mean = (sum / total).toFloat()

        var variance = 0.0
        for (v in gray) {
            val d = v - mean
            variance += d * d
        }
        val std = sqrt(variance / total).toFloat()

        // ความหนาแน่นขอบ + ความคมชัด
        var edges = 0
        var compared = 0
        var gradientSum = 0L

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val i = rowOffset + x
                val gx = abs(gray[i] - gray[i + 1])
                val gy = abs(gray[i] - gray[i + width])
                val grad = gx + gy
                gradientSum += grad
                if (grad > 40) edges++
                compared++
            }
        }

        val whiteRatio = (whiteCount / total).toFloat()
        val edgeRatio = if (compared > 0) edges.toFloat() / compared else 0f
        val sharpness = if (compared > 0) gradientSum.toFloat() / compared else 0f

        return FrameAnalysis(
            gray = gray,
            mean = mean,
            std = std,
            whiteRatio = whiteRatio,
            edgeRatio = edgeRatio,
            sharpness = sharpness
        )
    }
}
