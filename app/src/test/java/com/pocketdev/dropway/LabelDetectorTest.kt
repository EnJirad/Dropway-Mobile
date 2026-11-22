package com.pocketdev.dropway

import com.pocketdev.dropway.util.LabelDetector
import org.junit.Assert.*
import org.junit.Test

class LabelDetectorTest {

    @Test
    fun testLooksLikeLabelCriteria() {
        val validLabelAnalysis = LabelDetector.FrameAnalysis(
            gray = IntArray(100) { 150 },
            mean = 140f,        // > 95
            std = 25f,         // > 20
            whiteRatio = 0.50f, // between 0.14 and 0.92
            edgeRatio = 0.15f,  // between 0.02 and 0.42
            sharpness = 12f     // > 6
        )
        assertTrue(LabelDetector.looksLikeLabel(validLabelAnalysis))

        // Too dark
        val darkAnalysis = validLabelAnalysis.copy(mean = 60f)
        assertFalse(LabelDetector.looksLikeLabel(darkAnalysis))

        // Too blurry (sharpness too low)
        val blurryAnalysis = validLabelAnalysis.copy(sharpness = 3f)
        assertFalse(LabelDetector.looksLikeLabel(blurryAnalysis))

        // Uniform blank background (no edges)
        val blankAnalysis = validLabelAnalysis.copy(edgeRatio = 0.005f)
        assertFalse(LabelDetector.looksLikeLabel(blankAnalysis))
    }

    @Test
    fun testLooksTooCloseCriteria() {
        val tooCloseAnalysis = LabelDetector.FrameAnalysis(
            gray = IntArray(100) { 200 },
            mean = 180f,        // > 168
            std = 15f,
            whiteRatio = 0.95f, // > 0.90
            edgeRatio = 0.05f,
            sharpness = 8f
        )
        assertTrue(LabelDetector.looksTooClose(tooCloseAnalysis))

        val normalDistanceAnalysis = tooCloseAnalysis.copy(whiteRatio = 0.60f)
        assertFalse(LabelDetector.looksTooClose(normalDistanceAnalysis))
    }

    @Test
    fun testFrameDifferenceStillnessDetection() {
        val frame1 = IntArray(100) { 120 }
        val identicalFrame2 = IntArray(100) { 120 }
        val diffIdentical = LabelDetector.frameDifference(frame1, identicalFrame2)
        assertEquals(0f, diffIdentical, 0.001f)

        val slightlyMovedFrame = IntArray(100) { if (it % 2 == 0) 124 else 120 }
        val diffSmall = LabelDetector.frameDifference(frame1, slightlyMovedFrame)
        assertTrue(diffSmall < 9f) // Still considered still

        val fastMovingFrame = IntArray(100) { if (it % 2 == 0) 180 else 50 }
        val diffLarge = LabelDetector.frameDifference(frame1, fastMovingFrame)
        assertTrue(diffLarge > 9f) // Moving!
    }
}
