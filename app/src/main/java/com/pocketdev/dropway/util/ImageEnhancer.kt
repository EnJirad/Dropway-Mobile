package com.pocketdev.dropway.util

import android.graphics.Bitmap

/**
 * Ported from Dropway Web src/lib/imageEnhance.ts
 * Applies contrast and edge-sharpening enhancements to improve OCR accuracy on shipping labels.
 */
object ImageEnhancer {

    /**
     * เพิ่มความคมชัดของภาพด้วย 3x3 Sharpen Kernel
     * Kernel:
     *   [ 0, -1,  0 ]
     *   [-1,  5, -1 ]
     *   [ 0, -1,  0 ]
     */
    fun sharpenBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 3 || height < 3) return source

        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // Copy borders as-is
        for (x in 0 until width) {
            dstPixels[x] = srcPixels[x]
            dstPixels[(height - 1) * width + x] = srcPixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            dstPixels[y * width] = srcPixels[y * width]
            dstPixels[y * width + (width - 1)] = srcPixels[y * width + (width - 1)]
        }

        // Apply 3x3 sharpen kernel to interior pixels
        for (y in 1 until height - 1) {
            val row = y * width
            val prevRow = (y - 1) * width
            val nextRow = (y + 1) * width

            for (x in 1 until width - 1) {
                val c = srcPixels[row + x]
                val top = srcPixels[prevRow + x]
                val bottom = srcPixels[nextRow + x]
                val left = srcPixels[row + (x - 1)]
                val right = srcPixels[row + (x + 1)]

                val a = (c shr 24) and 0xFF

                val cr = (c shr 16) and 0xFF
                val topr = (top shr 16) and 0xFF
                val bottomr = (bottom shr 16) and 0xFF
                val leftr = (left shr 16) and 0xFF
                val rightr = (right shr 16) and 0xFF
                val nr = (5 * cr - topr - bottomr - leftr - rightr).coerceIn(0, 255)

                val cg = (c shr 8) and 0xFF
                val topg = (top shr 8) and 0xFF
                val bottomg = (bottom shr 8) and 0xFF
                val leftg = (left shr 8) and 0xFF
                val rightg = (right shr 8) and 0xFF
                val ng = (5 * cg - topg - bottomg - leftg - rightg).coerceIn(0, 255)

                val cb = c and 0xFF
                val topb = top and 0xFF
                val bottomb = bottom and 0xFF
                val leftb = left and 0xFF
                val rightb = right and 0xFF
                val nb = (5 * cb - topb - bottomb - leftb - rightb).coerceIn(0, 255)

                dstPixels[row + x] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }
}
